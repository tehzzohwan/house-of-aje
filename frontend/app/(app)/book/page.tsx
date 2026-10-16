"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, authApi, customerApi } from "@/lib/api";
import type { AvailabilitySlot, BookingResult, PublicStaff, Service } from "@/lib/types";
import { businessDateString, formatMoney, formatTime, formatTimeRange } from "@/lib/format";

const DRAFT_KEY = "hoa:booking-draft";

interface Draft {
  serviceId: string;
  staffId: string;
  date: string;
  startAt: string;
}

function readDraft(): Draft | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = sessionStorage.getItem(DRAFT_KEY);
    return raw ? (JSON.parse(raw) as Draft) : null;
  } catch {
    return null;
  }
}

export default function BookPage() {
  const router = useRouter();
  // Read the restored selection once; it is cleared below so a refresh doesn't re-apply it.
  const [draft] = useState<Draft | null>(readDraft);

  const [services, setServices] = useState<Service[]>([]);
  const [categoryKey, setCategoryKey] = useState("");
  const [serviceId, setServiceId] = useState("");
  const [date, setDate] = useState(businessDateString());
  const [staffList, setStaffList] = useState<PublicStaff[]>([]);
  const [selectedStaffId, setSelectedStaffId] = useState("");
  const [detailId, setDetailId] = useState("");
  const [slots, setSlots] = useState<AvailabilitySlot[]>([]);
  const [slot, setSlot] = useState<AvailabilitySlot | null>(null);

  const [loggedIn, setLoggedIn] = useState(false);
  const [idempotencyKey, setIdempotencyKey] = useState("");
  const [result, setResult] = useState<BookingResult | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [payFull, setPayFull] = useState(false);
  const [loadingStaff, setLoadingStaff] = useState(false);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (typeof window !== "undefined") sessionStorage.removeItem(DRAFT_KEY);
  }, []);

  useEffect(() => {
    authApi
      .me()
      .then(({ role }) => setLoggedIn(role === "CUSTOMER"))
      .catch(() => setLoggedIn(false));
  }, []);

  useEffect(() => {
    api
      .listServices()
      .then((list) => {
        setServices(list);
        if (list.length === 0) return;
        // Honour a pre-selected service (home page card), then a restored draft.
        const wanted = new URLSearchParams(window.location.search).get("service");
        const chosen =
          (wanted && list.find((s) => s.id === wanted)) ||
          (draft && list.find((s) => s.id === draft.serviceId)) ||
          list[0];
        setServiceId(chosen.id);
        setCategoryKey(chosen.categoryId ?? "other");
        if (draft?.date) setDate(draft.date);
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load services"));
  }, [draft]);

  // Load the specialists who perform the selected service.
  useEffect(() => {
    if (!serviceId || !date) return;
    setLoadingStaff(true);
    setSelectedStaffId("");
    setDetailId("");
    setSlots([]);
    setSlot(null);
    setError(null);
    api
      .staff(serviceId, date)
      .then((list) => {
        setStaffList(list);
        if (draft && list.some((s) => s.id === draft.staffId)) {
          setSelectedStaffId(draft.staffId);
        }
      })
      .catch((e) => {
        setStaffList([]);
        setError(e instanceof Error ? e.message : "Failed to load specialists");
      })
      .finally(() => setLoadingStaff(false));
  }, [serviceId, date, draft]);

  // Load the selected specialist's available times.
  useEffect(() => {
    if (!serviceId || !selectedStaffId || !date) return;
    setLoadingSlots(true);
    setSlot(null);
    setError(null);
    api
      .availability({ serviceId, staffId: selectedStaffId, date })
      .then((list) => {
        setSlots(list);
        const restore = draft && list.find((s) => s.start === draft.startAt);
        if (restore) setSlot(restore);
      })
      .catch((e) => {
        setSlots([]);
        setError(e instanceof Error ? e.message : "Failed to load availability");
      })
      .finally(() => setLoadingSlots(false));
  }, [serviceId, selectedStaffId, date, draft]);

  const groups = useMemo(() => {
    const map = new Map<string, { key: string; name: string; services: Service[] }>();
    for (const s of services) {
      const key = s.categoryId ?? "other";
      const existing = map.get(key);
      if (existing) {
        existing.services.push(s);
      } else {
        map.set(key, { key, name: s.categoryName ?? "Other", services: [s] });
      }
    }
    return Array.from(map.values());
  }, [services]);

  const selectedGroup = groups.find((g) => g.key === categoryKey);
  const groupServices = selectedGroup?.services ?? [];
  const selectedService = services.find((s) => s.id === serviceId);

  const sortedSlots = useMemo(() => [...slots].sort((a, b) => a.start.localeCompare(b.start)), [slots]);

  function chooseCategory(key: string) {
    setCategoryKey(key);
    const first = groups.find((g) => g.key === key)?.services[0];
    setServiceId(first ? first.id : "");
  }

  function selectStaff(id: string) {
    setSelectedStaffId(id);
    setSlot(null);
  }

  function toggleDetails(id: string) {
    setDetailId((prev) => (prev === id ? "" : id));
  }

  function chooseSlot(s: AvailabilitySlot) {
    setSlot(s);
    setIdempotencyKey(crypto.randomUUID());
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!slot || !selectedService) return;

    // Customers must be signed in so they can see booking details, the countdown
    // and act on their booking. Remember the selection and send them to login.
    if (!loggedIn) {
      if (typeof window !== "undefined") {
        sessionStorage.setItem(
          DRAFT_KEY,
          JSON.stringify({ serviceId, staffId: slot.staffId, date, startAt: slot.start }),
        );
      }
      router.push("/login?next=/book");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const key = idempotencyKey || crypto.randomUUID();
      const res = await customerApi.createBooking(
        { serviceId: selectedService.id, staffId: slot.staffId, startAt: slot.start, payFull },
        key,
      );
      setResult(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Booking failed");
    } finally {
      setSubmitting(false);
    }
  }

  if (result) {
    const { booking, depositInstructions } = result;
    return (
      <div className="mx-auto max-w-lg">
        <h1 className="text-3xl font-semibold tracking-tight">Booking confirmed</h1>
        <p className="mt-2 text-hoa-muted">
          Your slot is reserved pending deposit. Reference:{" "}
          <span className="font-mono font-medium">{booking.reference}</span>
        </p>

        <dl className="hoa-surface mt-6 space-y-2 p-5 text-sm">
          <div className="flex justify-between">
            <dt className="text-hoa-muted">Service</dt>
            <dd>{selectedService?.name}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-hoa-muted">Time</dt>
            <dd>{formatTimeRange(booking.startAt, booking.endAt)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-hoa-muted">Price</dt>
            <dd>{formatMoney(booking.price)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-hoa-muted">
              {booking.depositAmount >= booking.price ? "Amount due" : "Deposit due"}
            </dt>
            <dd className="font-medium">{formatMoney(booking.depositAmount)}</dd>
          </div>
        </dl>

        <div className="hoa-surface mt-6 p-5">
          <h2 className="font-semibold">
            {booking.depositAmount >= booking.price ? "Complete your payment" : "Pay your deposit"}
          </h2>
          <p className="mt-1 text-sm text-hoa-muted">
            Transfer the amount to the account below. Your booking is confirmed once the payment
            is received.
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
        </div>

        <Link
          href="/account"
          className="cta-gradient mt-6 inline-block px-6 py-3 text-sm font-semibold"
        >
          View my bookings
        </Link>
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-3xl font-semibold tracking-tight">Book an appointment</h1>

      <form onSubmit={onSubmit} className="mt-8 space-y-8">
        <section className="space-y-4">
          <label className="block text-sm font-medium">
            Category
            <select
              value={categoryKey}
              onChange={(e) => chooseCategory(e.target.value)}
              className="mt-2 block w-full max-w-sm rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text focus:border-hoa-gold focus:outline-none"
            >
              {groups.map((group) => (
                <option key={group.key} value={group.key}>
                  {group.name}
                </option>
              ))}
            </select>
          </label>

          <label className="block text-sm font-medium">
            Service
            <select
              value={serviceId}
              onChange={(e) => setServiceId(e.target.value)}
              className="mt-2 block w-full max-w-sm rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text focus:border-hoa-gold focus:outline-none"
            >
              {groupServices.map((service) => (
                <option key={service.id} value={service.id}>
                  {service.name} — {formatMoney(service.price)}
                </option>
              ))}
            </select>
          </label>

          <label className="block text-sm font-medium">
            Date
            <input
              type="date"
              value={date}
              min={businessDateString()}
              onChange={(e) => setDate(e.target.value)}
              className="mt-2 block w-full max-w-sm rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text focus:border-hoa-gold focus:outline-none"
            />
          </label>
        </section>

        <section>
          <h2 className="text-sm font-medium">Choose a specialist</h2>
          {loadingStaff ? (
            <p className="mt-3 text-sm text-hoa-muted">Loading specialists…</p>
          ) : staffList.length === 0 ? (
            <p className="mt-3 text-sm text-hoa-muted">
              No specialists perform this service. Try another service.
            </p>
          ) : (
            <ul className="mt-3 space-y-3">
              {staffList.map((s) => {
                const selected = s.id === selectedStaffId;
                const showDetails = detailId === s.id;
                return (
                  <li
                    key={s.id}
                    className={`hoa-surface p-4 ${
                      selected ? "border-hoa-gold" : ""
                    }`}
                  >
                    <div className="flex items-center justify-between gap-4">
                      <div>
                        <p className="font-medium">{s.name}</p>
                        <p className="text-xs text-hoa-muted">
                          {s.available ? "Available on this date" : "No availability on this date"}
                        </p>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => selectStaff(s.id)}
                          className="rounded-full border border-hoa-border-strong px-3 py-1 text-xs hover:border-hoa-gold hover:text-hoa-text"
                        >
                          Select staff
                        </button>
                        <button
                          type="button"
                          onClick={() => toggleDetails(s.id)}
                          className="rounded-full border border-hoa-border-strong px-3 py-1 text-xs hover:border-hoa-gold hover:text-hoa-text"
                        >
                          Details
                        </button>
                      </div>
                    </div>

                    {showDetails && (
                      <div className="mt-3 border-t border-hoa-border pt-3 text-sm">
                        {s.bio && <p className="text-hoa-muted">{s.bio}</p>}
                        {s.services.length > 0 && (
                          <div className="mt-2 flex flex-wrap gap-2">
                            {s.services.map((name) => (
                              <span
                                key={name}
                                className="rounded-full bg-white/10 px-3 py-1 text-xs"
                              >
                                {name}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        {selectedStaffId && (
          <section>
            <h2 className="text-sm font-medium">Choose a time</h2>
            {loadingSlots ? (
              <p className="mt-3 text-sm text-hoa-muted">Loading availability…</p>
            ) : sortedSlots.length === 0 ? (
              <p className="mt-3 text-sm text-hoa-muted">
                No available times for this specialist on this date.
              </p>
            ) : (
              <div className="mt-3 flex flex-wrap gap-2">
                {sortedSlots.map((s) => (
                  <button
                    key={`${s.start}-${s.staffId}`}
                    type="button"
                    onClick={() => chooseSlot(s)}
                    className={`rounded-full border px-4 py-2 text-sm transition-colors ${
                      slot?.start === s.start && slot?.staffId === s.staffId
                        ? "border-hoa-gold bg-hoa-gold text-hoa-ink"
                        : "border-hoa-border-strong hover:border-hoa-gold hover:text-hoa-text"
                    }`}
                  >
                    {formatTime(s.start)}
                  </button>
                ))}
              </div>
            )}
          </section>
        )}

        {slot && selectedService && (
          <section>
            <h2 className="text-sm font-medium">Payment</h2>
            <div className="mt-3 grid gap-2 sm:grid-cols-2">
              <button
                type="button"
                onClick={() => setPayFull(false)}
                className={`hoa-surface p-4 text-left transition-colors ${
                  !payFull
                    ? "border-hoa-gold"
                    : ""
                }`}
              >
                <span className="block font-medium">Pay deposit</span>
                <span className="block text-xs text-hoa-muted">
                  {formatMoney(selectedService.price * 0.4)} now
                </span>
              </button>
              <button
                type="button"
                onClick={() => setPayFull(true)}
                className={`hoa-surface p-4 text-left transition-colors ${
                  payFull
                    ? "border-hoa-gold"
                    : ""
                }`}
              >
                <span className="block font-medium">Pay in full</span>
                <span className="block text-xs text-hoa-muted">
                  {formatMoney(selectedService.price)} now
                </span>
              </button>
            </div>
          </section>
        )}

        {!loggedIn && (
          <p className="text-sm text-hoa-muted">
            You&apos;ll be asked to sign in or register after choosing a time, so you can see your
            booking details and countdown.
          </p>
        )}

        {error && <p className="text-sm text-red-400">{error}</p>}

        <button
          type="submit"
          disabled={!slot || submitting}
          className="cta-gradient rounded-full px-6 py-3 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-40"
        >
          {submitting ? "Booking…" : "Confirm booking"}
        </button>
      </form>
    </div>
  );
}
