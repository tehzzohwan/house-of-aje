package com.houseofaje.review;

import com.houseofaje.booking.Booking;
import com.houseofaje.booking.BookingNotFoundException;
import com.houseofaje.booking.BookingRepository;
import com.houseofaje.booking.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    ReviewRepository reviews;
    @Mock
    BookingRepository bookings;

    private final UUID customer = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @Test
    void createsPublishedReviewForCompletedBooking() {
        when(bookings.findById(bookingId)).thenReturn(Optional.of(completedBooking(customer, bookingId)));

        Review result = service().create(new CreateReviewRequest(customer, bookingId, 5, "Loved it"));

        ArgumentCaptor<Review> inserted = ArgumentCaptor.forClass(Review.class);
        verify(reviews).insert(inserted.capture());
        assertThat(inserted.getValue().status()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(inserted.getValue().rating()).isEqualTo(5);
        assertThat(inserted.getValue().bookingId()).isEqualTo(bookingId);
        assertThat(result.bookingId()).isEqualTo(bookingId);
    }

    @Test
    void rejectsRatingBelowOne() {
        assertThatThrownBy(() -> service().create(new CreateReviewRequest(customer, bookingId, 0, "bad")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRatingAboveFive() {
        assertThatThrownBy(() -> service().create(new CreateReviewRequest(customer, bookingId, 6, "bad")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownBooking() {
        when(bookings.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(new CreateReviewRequest(customer, bookingId, 5, "ok")))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void rejectsReviewByAnotherCustomer() {
        when(bookings.findById(bookingId)).thenReturn(Optional.of(completedBooking(UUID.randomUUID(), bookingId)));

        assertThatThrownBy(() -> service().create(new CreateReviewRequest(customer, bookingId, 5, "ok")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsReviewForNonCompletedBooking() {
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking(customer, BookingStatus.CONFIRMED, bookingId)));

        assertThatThrownBy(() -> service().create(new CreateReviewRequest(customer, bookingId, 5, "ok")))
                .isInstanceOf(BookingNotCompletedException.class);
    }

    @Test
    void mapsDuplicateInsertToReviewAlreadyExists() {
        when(bookings.findById(bookingId)).thenReturn(Optional.of(completedBooking(customer, bookingId)));
        doThrow(new DataIntegrityViolationException("duplicate")).when(reviews).insert(any());

        assertThatThrownBy(() -> service().create(new CreateReviewRequest(customer, bookingId, 5, "ok")))
                .isInstanceOf(ReviewAlreadyExistsException.class);
    }

    @Test
    void hidesAndPublishesById() {
        UUID reviewId = UUID.randomUUID();
        when(reviews.updateStatus(reviewId, ReviewStatus.HIDDEN)).thenReturn(true);
        when(reviews.findById(reviewId)).thenReturn(Optional.of(review(reviewId, ReviewStatus.HIDDEN)));

        Review hidden = service().setStatus(reviewId, ReviewStatus.HIDDEN);

        assertThat(hidden.status()).isEqualTo(ReviewStatus.HIDDEN);
    }

    @Test
    void setStatusOnUnknownReviewThrows() {
        UUID reviewId = UUID.randomUUID();
        when(reviews.updateStatus(reviewId, ReviewStatus.HIDDEN)).thenReturn(false);

        assertThatThrownBy(() -> service().setStatus(reviewId, ReviewStatus.HIDDEN))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    private ReviewService service() {
        return new ReviewService(reviews, bookings);
    }

    private static Booking completedBooking(UUID customerId, UUID bookingId) {
        return booking(customerId, BookingStatus.COMPLETED, bookingId);
    }

    private static Booking booking(UUID customerId, BookingStatus status, UUID bookingId) {
        return new Booking(bookingId, "ref", customerId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now(), Instant.now(), status, new BigDecimal("10000.00"),
                new BigDecimal("4000.00"), Instant.now());
    }

    private static Review review(UUID id, ReviewStatus status) {
        return new Review(id, UUID.randomUUID(), UUID.randomUUID(), 5, "nice", status, Instant.now());
    }
}
