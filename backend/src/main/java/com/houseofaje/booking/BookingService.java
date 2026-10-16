package com.houseofaje.booking;

import com.houseofaje.availability.AvailabilityService;
import com.houseofaje.payment.DepositInstructions;
import com.houseofaje.payment.DepositRequest;
import com.houseofaje.payment.Payment;
import com.houseofaje.payment.PaymentProvider;
import com.houseofaje.payment.PaymentRepository;
import com.houseofaje.payment.PaymentStatus;
import com.houseofaje.payment.PaymentType;
import com.houseofaje.payment.RefundService;
import com.houseofaje.notification.NotificationService;
import com.houseofaje.notification.NotificationType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingService {

    /** Deposit is 40% of the service price, paid to hold the slot (unless paid in full). */
    private static final BigDecimal DEPOSIT_RATIO = new BigDecimal("0.40");
    /** In-person payments have no provider; they are recorded as already paid. */
    private static final String MANUAL_PROVIDER = "MANUAL";
    private static final String CURRENCY = "NGN";

    private final JdbcTemplate jdbc;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final PaymentProvider paymentProvider;
    private final SlotHoldService slotHolds;
    private final AvailabilityService availability;
    private final RefundService refundService;
    private final AuditLogRepository auditLog;
    private final NotificationService notificationService;
    private final TransactionTemplate tx;
    private final ZoneId businessZone;
    private final Duration paymentWindow;

    public BookingService(
            JdbcTemplate jdbc,
            BookingRepository bookings,
            PaymentRepository payments,
            PaymentProvider paymentProvider,
            SlotHoldService slotHolds,
            AvailabilityService availability,
            RefundService refundService,
            AuditLogRepository auditLog,
            NotificationService notificationService,
            PlatformTransactionManager txManager,
            @Value("${hoa.business-zone:Africa/Lagos}") String businessZone,
            @Value("${hoa.payment-window:PT3M}") Duration paymentWindow) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.payments = payments;
        this.paymentProvider = paymentProvider;
        this.slotHolds = slotHolds;
        this.availability = availability;
        this.refundService = refundService;
        this.auditLog = auditLog;
        this.notificationService = notificationService;
        this.tx = new TransactionTemplate(txManager);
        this.businessZone = ZoneId.of(businessZone);
        this.paymentWindow = paymentWindow;
    }

    /**
     * Creates a booking in {@code PENDING_PAYMENT}, records the deposit payment,
     * places a Redis slot hold for the payment window, and returns the bank-transfer
     * instructions. Idempotent on {@code idempotencyKey}: a replay returns the same
     * booking and instructions without creating a second row.
     */
    public BookingResult create(CreateBookingRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        Optional<Payment> existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get());
        }
        try {
            return tx.execute(status -> doCreate(request, idempotencyKey));
        } catch (DataIntegrityViolationException e) {
            // Lost an idempotency race: another request committed the same key first.
            Optional<Payment> winner = payments.findByIdempotencyKey(idempotencyKey);
            if (winner.isPresent()) {
                return replay(winner.get());
            }
            throw e;
        }
    }

    private BookingResult doCreate(CreateBookingRequest request, String idempotencyKey) {
        if (request.customerId() == null || request.serviceId() == null
                || request.staffId() == null || request.startAt() == null) {
            throw new IllegalArgumentException("customerId, serviceId, staffId and startAt are required");
        }

        ServiceRow service = loadActiveService(request.serviceId());
        CustomerRow customer = loadCustomer(request.customerId());
        requireStaffForService(request.staffId(), request.serviceId());

        Instant end = request.startAt().plus(Duration.ofMinutes(service.durationMinutes()));
        if (!availability.isSlotBookable(request.serviceId(), request.staffId(), request.startAt(), end)) {
            throw new SlotUnavailableException("Requested slot is not available");
        }

        UUID id = UUID.randomUUID();
        String reference = UUID.randomUUID().toString().replace("-", "");
        // The customer chooses at booking whether to pay the 40% deposit or the full
        // price up front. Either way the amount is recorded as the DEPOSIT payment.
        BigDecimal deposit = request.payFull()
                ? service.price()
                : service.price().multiply(DEPOSIT_RATIO).setScale(2, RoundingMode.HALF_UP);

        Booking booking = new Booking(id, reference, request.customerId(), request.serviceId(), request.staffId(),
                request.startAt(), end, BookingStatus.PENDING_PAYMENT, service.price(), deposit, Instant.now());

        try {
            bookings.insert(booking);
        } catch (DataIntegrityViolationException e) {
            // Exclusion constraint fired: another booking took the slot concurrently.
            throw new SlotUnavailableException("Requested slot was taken concurrently");
        }

        UUID paymentId = UUID.randomUUID();
        payments.insert(new Payment(paymentId, id, deposit, PaymentType.DEPOSIT, PaymentStatus.PENDING,
                paymentProvider.name(), null, null, idempotencyKey, CURRENCY, null));

        slotHolds.hold(id, request.staffId(), request.startAt(), end, paymentWindow);

        DepositInstructions instructions = paymentProvider.initiateDeposit(
                new DepositRequest(reference, deposit, CURRENCY, customer.email(), customer.phone()));
        payments.updateProviderReference(paymentId, instructions.providerReference());

        availability.evict(request.staffId(), request.startAt().atZone(businessZone).toLocalDate());
        return new BookingResult(booking, instructions);
    }

    @Transactional
    public Booking confirm(UUID id) {
        Booking booking = transition(load(id), BookingStatus.CONFIRMED);
        notificationService.notifyBooking(booking, NotificationType.CONFIRMATION);
        return booking;
    }

    @Transactional
    public Booking complete(UUID id) {
        Booking booking = transition(load(id), BookingStatus.COMPLETED);
        recordBalance(booking);
        notificationService.notifyBooking(booking, NotificationType.REVIEW_REQUEST);
        return booking;
    }

    @Transactional
    public Booking cancel(UUID id) {
        Booking current = load(id);
        boolean refundDue = current.status() == BookingStatus.CONFIRMED;
        Booking booking = transition(current, BookingStatus.CANCELLED);
        // Refund 30% of the service price on a cancellation of a paid booking; the
        // other 10% of the 40% deposit is retained as the breaking fee.
        if (refundDue) {
            refundService.refundDeposit(current);
        }
        auditLog.log("booking", current.id().toString(), "CANCELLED", null,
                Map.of("status", current.status().name()),
                Map.of("status", booking.status().name()));
        notificationService.notifyBooking(booking, NotificationType.CANCELLATION);
        return booking;
    }

    @Transactional
    public Booking noShow(UUID id) {
        Booking current = load(id);
        Booking booking = transition(current, BookingStatus.NO_SHOW);
        // Same as cancellation: refund 30% of the service price, retain 10%.
        refundService.refundDeposit(current);
        return booking;
    }

    @Transactional
    public Booking expire(UUID id) {
        return transition(load(id), BookingStatus.EXPIRED);
    }

    /**
     * Records a deposit received outside the webhook (cash, manual transfer) and
     * confirms the booking if it is still awaiting payment. Idempotent for the
     * payment: a deposit already {@code PAID} is left untouched.
     */
    @Transactional
    public Booking markDepositPaid(UUID bookingId) {
        Booking booking = load(bookingId);
        Payment deposit = payments.findDepositForBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("No deposit found for booking " + bookingId));
        if (deposit.status() != PaymentStatus.PAID) {
            payments.markPaidManual(deposit.id(), Instant.now());
            auditLog.log("payment", deposit.id().toString(), "MARK_PAID", null,
                    Map.of("status", deposit.status().name()), Map.of("status", PaymentStatus.PAID.name()));
        }
        if (booking.status() == BookingStatus.PENDING_PAYMENT) {
            return confirm(bookingId);
        }
        return booking;
    }

    private Booking load(UUID id) {
        return bookings.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
    }

    private Booking transition(Booking current, BookingStatus to) {
        BookingStateMachine.requireTransition(current.status(), to);
        if (!bookings.updateStatus(current.id(), current.status(), to)) {
            throw new InvalidStateTransitionException(current.status(), to);
        }
        // The hold is only meaningful while payment is pending; release it once the
        // booking is no longer waiting on a deposit.
        if (to == BookingStatus.CONFIRMED || to == BookingStatus.CANCELLED || to == BookingStatus.EXPIRED) {
            slotHolds.release(current.id());
        }
        availability.evict(current.staffId(), current.startAt().atZone(businessZone).toLocalDate());
        return new Booking(current.id(), current.reference(), current.customerId(), current.serviceId(),
                current.staffId(), current.startAt(), current.endAt(), to,
                current.price(), current.depositAmount(), current.createdAt());
    }

    /** Records the unpaid remainder as a PAID in-person payment when the service completes. */
    private void recordBalance(Booking booking) {
        BigDecimal balance = booking.price().subtract(booking.depositAmount()).setScale(2, RoundingMode.HALF_UP);
        if (balance.signum() <= 0) {
            return; // paid in full up front; nothing left to collect
        }
        if (payments.findBalanceForBooking(booking.id()).isPresent()) {
            return; // a balance payment (in-app or in person) already exists
        }
        payments.insert(new Payment(UUID.randomUUID(), booking.id(), balance, PaymentType.BALANCE,
                PaymentStatus.PAID, MANUAL_PROVIDER, null, null, booking.reference() + "-balance",
                CURRENCY, Instant.now()));
    }

    /**
     * Initiates in-app payment of the remaining balance on a confirmed booking.
     * Idempotent: a second call reuses the pending balance payment and returns the
     * same virtual-account instructions. The webhook marks it paid without
     * re-confirming the (already confirmed) booking.
     */
    public DepositInstructions payBalance(UUID bookingId) {
        Booking booking = load(bookingId);
        if (booking.status() != BookingStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only confirmed bookings can pay a balance");
        }
        BigDecimal remaining = booking.price().subtract(booking.depositAmount()).setScale(2, RoundingMode.HALF_UP);
        if (remaining.signum() <= 0) {
            throw new IllegalArgumentException("This booking is already paid in full");
        }
        CustomerRow customer = loadCustomer(booking.customerId());

        Payment balance = payments.findBalanceForBooking(bookingId).orElseGet(() -> {
            Payment created = new Payment(UUID.randomUUID(), bookingId, remaining, PaymentType.BALANCE,
                    PaymentStatus.PENDING, paymentProvider.name(), null, null,
                    booking.reference() + "-balance", CURRENCY, null);
            payments.insert(created);
            return created;
        });

        DepositInstructions instructions = paymentProvider.initiateDeposit(
                new DepositRequest(booking.reference(), balance.amount(), balance.currency(),
                        customer.email(), customer.phone()));
        payments.updateProviderReference(balance.id(), instructions.providerReference());
        return instructions;
    }

    /**
     * Re-issues the bank-transfer instructions for a booking still awaiting its
     * deposit (or full) payment. Idempotent: the provider resolves the same
     * stable virtual account from the booking reference.
     */
    public DepositInstructions payDeposit(UUID bookingId) {
        Booking booking = load(bookingId);
        if (booking.status() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException("Only bookings awaiting payment can pay the deposit");
        }
        CustomerRow customer = loadCustomer(booking.customerId());
        Payment deposit = payments.findDepositForBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("No deposit found for booking " + bookingId));
        DepositInstructions instructions = paymentProvider.initiateDeposit(
                new DepositRequest(booking.reference(), deposit.amount(), deposit.currency(),
                        customer.email(), customer.phone()));
        payments.updateProviderReference(deposit.id(), instructions.providerReference());
        return instructions;
    }

    private BookingResult replay(Payment payment) {
        Booking booking = bookings.findById(payment.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(payment.bookingId()));
        CustomerRow customer = loadCustomer(booking.customerId());
        // initiateDeposit is idempotent per booking reference, so replaying it
        // returns the same stable virtual account.
        DepositInstructions instructions = paymentProvider.initiateDeposit(
                new DepositRequest(booking.reference(), payment.amount(), payment.currency(),
                        customer.email(), customer.phone()));
        return new BookingResult(booking, instructions);
    }

    // ------------------------------------------------------------------
    // Lookups / validation
    // ------------------------------------------------------------------

    private ServiceRow loadActiveService(UUID serviceId) {
        List<ServiceRow> rows = jdbc.query(
                "SELECT id, duration_minutes, price FROM services WHERE id = ? AND active = TRUE",
                (rs, n) -> new ServiceRow(rs.getObject("id", UUID.class), rs.getInt("duration_minutes"), rs.getBigDecimal("price")),
                serviceId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Service not found or inactive: " + serviceId);
        }
        return rows.get(0);
    }

    private CustomerRow loadCustomer(UUID customerId) {
        List<CustomerRow> rows = jdbc.query(
                "SELECT email, phone FROM customers WHERE id = ? AND deleted_at IS NULL",
                (rs, n) -> new CustomerRow(rs.getString("email"), rs.getString("phone")),
                customerId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Customer not found: " + customerId);
        }
        return rows.get(0);
    }

    private void requireStaffForService(UUID staffId, UUID serviceId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM staff_services ss JOIN staff s ON s.id = ss.staff_id "
                        + "WHERE ss.staff_id = ? AND ss.service_id = ? AND s.active = TRUE",
                Integer.class, staffId, serviceId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Staff does not provide this service: " + staffId);
        }
    }

    private record ServiceRow(UUID id, int durationMinutes, BigDecimal price) {}

    private record CustomerRow(String email, String phone) {}
}
