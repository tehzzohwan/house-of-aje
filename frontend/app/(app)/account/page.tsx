"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { authApi, customerApi } from "@/lib/api";
import type {
  BookingSummary,
  Customer,
  CustomerNotification,
  DepositInstructions,
} from "@/lib/types";
import { formatDateTime, formatMoney } from "@/lib/format";
import Countdown from "@/components/Countdown";
import DepositCountdown from "@/components/DepositCountdown";

const statusLabel: Record<string, string> = {
  PENDING_PAYMENT: "Awaiting deposit",
  CONFIRMED: "Confirmed",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
  NO_SHOW: "No-show",
  EXPIRED: "Expired",
};

const paymentStateLabel: Record<string, string> = {
  AWAITING_PAYMENT: "Awaiting payment",
  DEPOSIT_PAID: "Deposit paid",
  PAID_IN_FULL: "Payment complete",
};

const notificationLabel: Record<string, string> = {
  CONFIRMATION: "Booking confirmed",
  REMINDER_24H: "Appointment tomorrow",
  REMINDER_1H: "Appointment in 1 hour",
  SESSION_STARTED: "Your session has started",
  CANCELLATION: "Booking cancelled",
  REVIEW_REQUEST: "Please rate your visit",
};

const statusLabelFor = (b: BookingSummary) =>
  b.status === "PENDING_PAYMENT"
    ? b.depositAmount >= b.price
      ? "Awaiting full payment"
      : "Awaiting deposit"
    : statusLabel[b.status] ?? b.status;

export default function AccountPage() {
  const [authed, setAuthed] = useState(false);
  const [ready, setReady] = useState(false);
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [bookings, setBookings] = useState<BookingSummary[]>([]);
  const [notifications, setNotifications] = useState<CustomerNotification[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [cancelingId, setCancelingId] = useState<string | null>(null);

  const [reviewing, setReviewing] = useState<BookingSummary | null>(null);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [submittingReview, setSubmittingReview] = useState(false);

  const [balanceBooking, setBalanceBooking] = useState<BookingSummary | null>(null);
  const [balanceInstructions, setBalanceInstructions] = useState<DepositInstructions | null>(null);
  const [payingBalance, setPayingBalance] = useState(false);
  const [balanceError, setBalanceError] = useState<string | null>(null);

  const [depositBooking, setDepositBooking] = useState<BookingSummary | null>(null);
  const [depositInstructions, setDepositInstructions] = useState<DepositInstructions | null>(null);
  const [payingDeposit, setPayingDeposit] = useState(false);
  const [depositError, setDepositError] = useState<string | null>(null);

  useEffect(() => {
    authApi
      .me()
      .then(({ role }) => {
        setAuthed(role === "CUSTOMER");
        setReady(true);
      })
      .catch(() => {
        setAuthed(false);
        setReady(true);
      });
  }, []);

  const loadData = useCallback(async () => {
    if (!authed) return;
    const [me, mine, notices] = await Promise.all([
      customerApi.me(),
      customerApi.bookings(),
      customerApi.notifications(),
    ]);
    setCustomer(me);
    setBookings(mine);
    setNotifications(notices);
    // Surface the rating prompt once the appointment has been paid and
    // completed: auto-open for the most recent completed, unreviewed booking.
    const pending = mine.find((b) => b.status === "COMPLETED" && !b.reviewed);
    if (pending) setReviewing(pending);
  }, [authed]);

  useEffect(() => {
    if (!authed) {
      setLoading(false);
      return;
    }
    loadData()
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load your account"))
      .finally(() => setLoading(false));
  }, [authed, loadData]);

  function openReview(booking: BookingSummary) {
    setReviewing(booking);
    setRating(0);
    setComment("");
    setReviewError(null);
  }

  async function submitReview() {
    if (!authed || !reviewing || rating < 1) return;
    setSubmittingReview(true);
    setReviewError(null);
    try {
      await customerApi.review(reviewing.id, rating, comment);
      setBookings((prev) =>
        prev.map((b) => (b.id === reviewing.id ? { ...b, reviewed: true } : b)),
      );
      setReviewing(null);
    } catch (e) {
      setReviewError(e instanceof Error ? e.message : "Failed to submit review");
    } finally {
      setSubmittingReview(false);
    }
  }

  async function cancelBooking(b: BookingSummary) {
    const message =
      b.status === "CONFIRMED"
        ? `Cancel this booking? You'll forfeit ${formatMoney(
            b.price * 0.1,
          )} (breaking fee) and ${formatMoney(b.price * 0.3)} will be refunded.`
        : "Cancel this booking? Your slot will be released.";
    if (!window.confirm(message)) {
      return;
    }
    setCancelingId(b.id);
    setError(null);
    try {
      await customerApi.cancelBooking(b.id);
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to cancel booking");
    } finally {
      setCancelingId(null);
    }
  }

  async function payBalance(b: BookingSummary) {
    setBalanceBooking(b);
    setBalanceInstructions(null);
    setBalanceError(null);
    setPayingBalance(true);
    try {
      const instructions = await customerApi.payBalance(b.id);
      setBalanceInstructions(instructions);
    } catch (e) {
      setBalanceError(e instanceof Error ? e.message : "Failed to start balance payment");
    } finally {
      setPayingBalance(false);
    }
  }

  async function payDeposit(b: BookingSummary) {
    setDepositBooking(b);
    setDepositInstructions(null);
    setDepositError(null);
    setPayingDeposit(true);
    try {
      const instructions = await customerApi.payDeposit(b.id);
      setDepositInstructions(instructions);
    } catch (e) {
      setDepositError(e instanceof Error ? e.message : "Failed to load payment instructions");
    } finally {
      setPayingDeposit(false);
    }
  }

  async function openNotification(n: CustomerNotification) {
    if (n.readAt) return;
    try {
      await customerApi.markNotificationRead(n.id);
      setNotifications((prev) =>
        prev.map((x) => (x.id === n.id ? { ...x, readAt: new Date().toISOString() } : x)),
      );
    } catch {
      // Reading state is a convenience; never fail the page over it.
    }
  }

  if (!ready) {
    return <p className="text-sm text-hoa-muted">Loading…</p>;
  }

  if (!authed) {
    return (
      <div className="mx-auto max-w-sm space-y-4 text-center">
        <h1 className="text-3xl font-semibold tracking-tight">Your account</h1>
        <p className="text-hoa-muted">Sign in to view your bookings.</p>
        <Link
          href="/login"
          className="cta-gradient inline-block w-full px-6 py-3 text-sm font-semibold"
        >
          Sign in
        </Link>
        <Link href="/account/register" className="inline-block text-sm text-hoa-muted underline">
          Create an account
        </Link>
      </div>
    );
  }

  const cancellable = (b: BookingSummary) =>
    b.status === "PENDING_PAYMENT" || b.status === "CONFIRMED";

  const nowMs = Date.now();
  const upcomingCount = bookings.filter(
    (b) => b.status === "CONFIRMED" && new Date(b.startAt).getTime() > nowMs,
  ).length;
  const completedCount = bookings.filter((b) => b.status === "COMPLETED").length;
  const totalSpent = bookings
    .filter((b) => b.status === "COMPLETED")
    .reduce((sum, b) => sum + b.price, 0);
  const nextBooking = [...bookings]
    .filter(
      (b) =>
        (b.status === "CONFIRMED" || b.status === "PENDING_PAYMENT") &&
        new Date(b.startAt).getTime() > nowMs,
    )
    .sort((a, b) => a.startAt.localeCompare(b.startAt))[0];

  const overview = [
    { label: "Upcoming", value: String(upcomingCount) },
    { label: "Completed", value: String(completedCount) },
    { label: "Total spent", value: formatMoney(totalSpent) },
    {
      label: "Next appointment",
      value: nextBooking ? formatDateTime(nextBooking.startAt) : "—",
    },
  ];

  return (
    <div>
      <h1 className="text-3xl font-semibold tracking-tight">
        {customer ? `${customer.firstName} ${customer.lastName}` : "Your account"}
      </h1>
      {customer?.username && (
        <p className="mt-1 text-sm text-hoa-muted">@{customer.username}</p>
      )}

      {!loading && bookings.length > 0 && (
        <section className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {overview.map((stat) => (
            <div key={stat.label} className="hoa-surface p-4">
              <p className="font-mono text-[11px] uppercase tracking-wider text-hoa-muted">
                {stat.label}
              </p>
              <p className="mt-1 text-sm font-semibold text-hoa-text">{stat.value}</p>
            </div>
          ))}
        </section>
      )}

      {notifications.length > 0 && (
        <section className="mt-6">
          <h2 className="text-sm font-medium">Notifications</h2>
          <ul className="mt-2 space-y-2">
            {notifications.map((n) => (
              <li key={n.id}>
                <button
                  type="button"
                  onClick={() => openNotification(n)}
                  className={`flex w-full items-center justify-between gap-4 rounded-xl border px-4 py-3 text-left text-sm ${
                    n.readAt
                      ? "border-hoa-border text-hoa-muted"
                      : "border-hoa-gold bg-white/10"
                  }`}
                >
                  <span>
                    <span className="block font-medium">
                      {notificationLabel[n.type] ?? n.type}
                    </span>
                    {n.serviceName && (
                      <span className="block text-xs text-hoa-muted">
                        {n.serviceName}
                        {n.startAt ? ` · ${formatDateTime(n.startAt)}` : ""}
                      </span>
                    )}
                  </span>
                  {!n.readAt && (
                    <span className="h-2 w-2 shrink-0 rounded-full bg-hoa-gold" />
                  )}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {error && <p className="mt-6 text-sm text-red-400">{error}</p>}
      {balanceError && <p className="mt-6 text-sm text-red-400">{balanceError}</p>}
      {depositError && <p className="mt-6 text-sm text-red-400">{depositError}</p>}

      {loading ? (
        <p className="mt-8 text-sm text-hoa-muted">Loading…</p>
      ) : bookings.length === 0 ? (
        <p className="mt-8 text-sm text-hoa-muted">
          You have no bookings yet.{" "}
          <Link href="/book" className="text-hoa-gold underline">
            Book an appointment
          </Link>
        </p>
      ) : (
        <ul className="mt-8 space-y-3">
          {bookings.map((b) => (
            <li
              key={b.id}
              className="hoa-surface p-4"
            >
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="font-medium">{b.serviceName}</p>
                  <p className="text-sm text-hoa-muted">
                    {formatDateTime(b.startAt)} · with {b.staffName}
                  </p>
                  {b.status === "CONFIRMED" && (
                    <p className="mt-1 text-sm font-medium text-hoa-gold">
                      <Countdown startAt={b.startAt} />
                    </p>
                  )}
                  {b.status === "PENDING_PAYMENT" && (
                    <p className="mt-1 text-sm font-medium text-hoa-gold">
                      <DepositCountdown
                        createdAt={b.createdAt}
                        payFull={b.depositAmount >= b.price}
                      />
                    </p>
                  )}
                  <p className="mt-1 font-mono text-xs text-hoa-muted">{b.reference}</p>
                </div>
                <div className="text-right">
                  <span className="inline-block rounded-full bg-white/10 px-2.5 py-1 text-xs font-medium">
                    {statusLabelFor(b)}
                  </span>
                  <p className="mt-1">
                    <span className="inline-block rounded-full border border-hoa-border-strong px-2.5 py-1 text-xs text-hoa-muted">
                      {paymentStateLabel[b.paymentState] ?? b.paymentState}
                    </span>
                  </p>
                  <p className="mt-2 text-sm">{formatMoney(b.price)}</p>
                  {b.status === "COMPLETED" && !b.reviewed && (
                    <button
                      onClick={() => openReview(b)}
                      className="mt-2 rounded-full border border-hoa-border-strong px-3 py-1 text-xs hover:border-hoa-gold hover:text-hoa-text"
                    >
                      Rate this service
                    </button>
                  )}
                  {b.status === "PENDING_PAYMENT" && (
                    <button
                      onClick={() => payDeposit(b)}
                      disabled={payingDeposit}
                      className="mt-2 block w-full rounded-full border border-hoa-border-strong px-3 py-1 text-xs hover:border-hoa-gold disabled:opacity-40"
                    >
                      {payingDeposit
                        ? "Preparing…"
                        : b.depositAmount >= b.price
                          ? "Pay in full"
                          : "Pay deposit"}
                    </button>
                  )}
                  {b.status === "CONFIRMED" && b.depositAmount < b.price && (
                    <button
                      onClick={() => payBalance(b)}
                      disabled={payingBalance}
                      className="mt-2 block w-full rounded-full border border-hoa-border-strong px-3 py-1 text-xs hover:border-hoa-gold disabled:opacity-40"
                    >
                      {payingBalance
                        ? "Preparing…"
                        : `Pay balance · ${formatMoney(b.price - b.depositAmount)}`}
                    </button>
                  )}
                  {cancellable(b) && (
                    <button
                      onClick={() => cancelBooking(b)}
                      disabled={cancelingId === b.id}
                      className="mt-2 block w-full rounded-full border border-red-800 px-3 py-1 text-xs text-red-400 hover:border-red-500 disabled:opacity-40"
                    >
                      {cancelingId === b.id ? "Cancelling…" : "Cancel booking"}
                    </button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {reviewing && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-4 sm:items-center">
          <div className="lux-card w-full max-w-md p-6">
            <h2 className="text-xl font-semibold tracking-tight">Rate your visit</h2>
            <p className="mt-1 text-sm text-hoa-muted">
              {reviewing.serviceName} · {formatDateTime(reviewing.startAt)}
            </p>

            <div className="mt-4 flex justify-center gap-2">
              {[1, 2, 3, 4, 5].map((star) => (
                <button
                  key={star}
                  type="button"
                  onClick={() => setRating(star)}
                  aria-label={`${star} star${star > 1 ? "s" : ""}`}
                  className={`text-3xl text-hoa-gold transition-transform hover:scale-110 ${
                    star <= rating ? "opacity-100" : "opacity-30"
                  }`}
                >
                  ★
                </button>
              ))}
            </div>

            <textarea
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="How was your experience? (optional)"
              rows={3}
              className="mt-4 w-full rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            />

            {reviewError && <p className="mt-2 text-sm text-red-400">{reviewError}</p>}

            <div className="mt-4 flex justify-end gap-3">
              <button
                onClick={() => setReviewing(null)}
                className="rounded-full px-4 py-2 text-sm text-hoa-muted hover:text-hoa-text"
              >
                Not now
              </button>
              <button
                onClick={submitReview}
                disabled={rating < 1 || submittingReview}
                className="cta-gradient px-6 py-2 text-sm font-semibold disabled:opacity-40"
              >
                {submittingReview ? "Submitting…" : "Submit rating"}
              </button>
            </div>
          </div>
        </div>
      )}

      {balanceInstructions && balanceBooking && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-4 sm:items-center">
          <div className="lux-card w-full max-w-md p-6">
            <h2 className="text-xl font-semibold tracking-tight">Pay your balance</h2>
            <p className="mt-1 text-sm text-hoa-muted">
              {balanceBooking.serviceName} ·{" "}
              {formatMoney(balanceBooking.price - balanceBooking.depositAmount)} remaining
            </p>

            <dl className="mt-4 space-y-2 text-sm">
              <div className="flex justify-between">
                <dt className="text-hoa-muted">Bank</dt>
                <dd>{balanceInstructions.bankName}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-hoa-muted">Account number</dt>
                <dd className="font-mono">{balanceInstructions.accountNumber}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-hoa-muted">Account name</dt>
                <dd>{balanceInstructions.accountName}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-hoa-muted">Reference</dt>
                <dd className="font-mono">{balanceInstructions.providerReference}</dd>
              </div>
            </dl>

            <div className="mt-5 flex justify-end gap-3">
              <button
                onClick={() => {
                  setBalanceInstructions(null);
                  setBalanceBooking(null);
                }}
                className="cta-gradient px-6 py-2 text-sm font-semibold"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}

      {depositInstructions && depositBooking && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-4 sm:items-center">
          <div className="lux-card w-full max-w-md p-6">
            <h2 className="text-xl font-semibold tracking-tight">
              {depositBooking.depositAmount >= depositBooking.price
                ? "Complete your payment"
                : "Pay your deposit"}
            </h2>
            <p className="mt-1 text-sm text-hoa-muted">
              {depositBooking.serviceName} · {formatMoney(depositBooking.depositAmount)} due
            </p>

            <dl className="mt-4 space-y-2 text-sm">
              <div className="flex justify-between">
                <dt className="text-hoa-muted">Bank</dt>
                <dd>{depositInstructions.bankName}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-hoa-muted">Account number</dt>
                <dd className="font-mono">{depositInstructions.accountNumber}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-hoa-muted">Account name</dt>
                <dd>{depositInstructions.accountName}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-hoa-muted">Reference</dt>
                <dd className="font-mono">{depositInstructions.providerReference}</dd>
              </div>
            </dl>

            <div className="mt-5 flex justify-end gap-3">
              <button
                onClick={() => {
                  setDepositInstructions(null);
                  setDepositBooking(null);
                }}
                className="cta-gradient px-6 py-2 text-sm font-semibold"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
