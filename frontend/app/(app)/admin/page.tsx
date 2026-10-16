"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { adminApi, authApi } from "@/lib/api";
import type {
  AdminBooking,
  AdminService,
  AdminStaff,
  BestSeller,
  BookingStatus,
  ProfitReport,
  Review,
  ServiceCategory,
  StaffPerformance,
} from "@/lib/types";
import { formatDateTime, formatDuration, formatMoney } from "@/lib/format";
import KebabMenu from "@/components/KebabMenu";

type Transition = "confirm" | "complete" | "cancel" | "no-show" | "expire";
type Tab = "reports" | "bookings" | "staff" | "services" | "categories" | "reviews";

const PAGE_SIZE = 10;

const TRANSITIONS: Record<string, Transition[]> = {
  PENDING_PAYMENT: ["confirm", "cancel", "expire"],
  CONFIRMED: ["complete", "cancel", "no-show"],
};

const STATUSES: BookingStatus[] = [
  "PENDING_PAYMENT",
  "CONFIRMED",
  "COMPLETED",
  "CANCELLED",
  "NO_SHOW",
  "EXPIRED",
];

const PAYMENT_STATE_LABELS: Record<string, string> = {
  AWAITING_PAYMENT: "Awaiting deposit",
  DEPOSIT_PAID: "Deposit paid",
  PAID_IN_FULL: "Payment complete",
};

const EMPTY_SERVICE_FORM = {
  name: "",
  slug: "",
  description: "",
  durationMinutes: "",
  price: "",
  bufferMinutes: "",
  categoryId: "",
};

const EMPTY_CATEGORY_FORM = {
  name: "",
  slug: "",
};

const EMPTY_STAFF_FORM = {
  name: "",
  email: "",
  phone: "",
};

const DAYS = [
  { day: 1, label: "Mon" },
  { day: 2, label: "Tue" },
  { day: 3, label: "Wed" },
  { day: 4, label: "Thu" },
  { day: 5, label: "Fri" },
  { day: 6, label: "Sat" },
  { day: 7, label: "Sun" },
];

function toTimeInput(t: string): string {
  return t.slice(0, 5);
}

export default function AdminPage() {
  const [authed, setAuthed] = useState(false);
  const [ready, setReady] = useState(false);
  const [tab, setTab] = useState<Tab>("reports");

  const [statusFilter, setStatusFilter] = useState<BookingStatus | "">("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");

  const [profit, setProfit] = useState<ProfitReport | null>(null);
  const [bestSelling, setBestSelling] = useState<BestSeller[]>([]);
  const [staff, setStaff] = useState<StaffPerformance[]>([]);
  const [bookings, setBookings] = useState<AdminBooking[]>([]);
  const [bookingsTotal, setBookingsTotal] = useState(0);
  const [bookingsPage, setBookingsPage] = useState(0);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [services, setServices] = useState<AdminService[]>([]);
  const [categories, setCategories] = useState<ServiceCategory[]>([]);
  const [staffMembers, setStaffMembers] = useState<AdminStaff[]>([]);
  const [form, setForm] = useState({ ...EMPTY_SERVICE_FORM });
  const [adding, setAdding] = useState(false);
  const [categoryForm, setCategoryForm] = useState({ ...EMPTY_CATEGORY_FORM });
  const [addingCategory, setAddingCategory] = useState(false);
  const [staffForm, setStaffForm] = useState({ ...EMPTY_STAFF_FORM });
  const [addingStaff, setAddingStaff] = useState(false);
  const [editingServiceId, setEditingServiceId] = useState<string | null>(null);
  const [editingCategoryId, setEditingCategoryId] = useState<string | null>(null);
  const [editingStaffEditId, setEditingStaffEditId] = useState<string | null>(null);
  const [editingStaffId, setEditingStaffId] = useState<string | null>(null);
  const [inviteLink, setInviteLink] = useState<string | null>(null);
  const [copiedInvite, setCopiedInvite] = useState(false);
  const [availServiceIds, setAvailServiceIds] = useState<string[]>([]);
  const [availHours, setAvailHours] = useState<Record<number, { startTime: string; endTime: string }>>({});
  const [availLoading, setAvailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    authApi
      .me()
      .then(({ role }) => {
        setAuthed(role === "ADMIN");
        setReady(true);
      })
      .catch(() => {
        setAuthed(false);
        setReady(true);
      });
  }, []);

  const loadAll = useCallback(async () => {
    if (!authed) return;
    setBusy(true);
    setError(null);
    try {
      const [p, b, s, sv, c, sm, r] = await Promise.all([
        adminApi.profit(fromDate || undefined, toDate || undefined),
        adminApi.bestSelling(fromDate || undefined, toDate || undefined),
        adminApi.staffPerformance(fromDate || undefined, toDate || undefined),
        adminApi.services(),
        adminApi.categories(),
        adminApi.staff(),
        adminApi.reviews(),
      ]);
      setProfit(p);
      setBestSelling(b);
      setStaff(s);
      setServices(sv);
      setCategories(c);
      setStaffMembers(sm);
      setReviews(r);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load dashboard");
    } finally {
      setBusy(false);
    }
  }, [authed, fromDate, toDate]);

  const loadBookings = useCallback(async () => {
    if (!authed) return;
    try {
      const page = await adminApi.bookings(statusFilter || undefined, bookingsPage, PAGE_SIZE);
      setBookings(page.items);
      setBookingsTotal(page.total);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load bookings");
    }
  }, [authed, statusFilter, bookingsPage]);

  useEffect(() => {
    if (authed) {
      loadAll();
      loadBookings();
    }
  }, [authed, loadAll, loadBookings]);

  const refresh = useCallback(async () => {
    await Promise.all([loadAll(), loadBookings()]);
  }, [loadAll, loadBookings]);

  if (!ready) return <p className="text-hoa-muted">Loading…</p>;

  if (!authed) {
    return (
      <div className="mx-auto max-w-sm text-center">
        <h1 className="text-3xl font-semibold tracking-tight">Admin</h1>
        <p className="mt-2 text-hoa-muted">Sign in to manage bookings and reviews.</p>
        <Link
          href="/login"
          className="cta-gradient mt-6 inline-block px-6 py-3 text-sm font-semibold"
        >
          Sign in
        </Link>
      </div>
    );
  }

  async function run(action: () => Promise<unknown>) {
    setError(null);
    try {
      await action();
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Action failed");
    }
  }

  async function downloadPaymentsCsv() {
    if (!authed) return;
    setError(null);
    try {
      const csv = await adminApi.paymentsCsv(fromDate || undefined, toDate || undefined);
      const blob = new Blob([csv], { type: "text/csv" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "payments.csv";
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to download payments");
    }
  }

  async function saveService(e: React.FormEvent) {
    e.preventDefault();
    if (!authed) return;
    setError(null);
    setAdding(true);
    try {
      const body = {
        name: form.name,
        slug: form.slug || undefined,
        description: form.description || undefined,
        durationMinutes: Number(form.durationMinutes),
        price: Number(form.price),
        bufferMinutes: form.bufferMinutes ? Number(form.bufferMinutes) : undefined,
        categoryId: form.categoryId || undefined,
      };
      if (editingServiceId) {
        await adminApi.updateService(editingServiceId, body);
      } else {
        await adminApi.createService(body);
      }
      setForm({ ...EMPTY_SERVICE_FORM });
      setEditingServiceId(null);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save service");
    } finally {
      setAdding(false);
    }
  }

  function editService(s: AdminService) {
    setEditingServiceId(s.id);
    setForm({
      name: s.name,
      slug: s.slug,
      description: s.description ?? "",
      durationMinutes: String(s.durationMinutes),
      price: String(s.price),
      bufferMinutes: String(s.bufferMinutes),
      categoryId: s.categoryId ?? "",
    });
  }

  function cancelServiceEdit() {
    setEditingServiceId(null);
    setForm({ ...EMPTY_SERVICE_FORM });
  }

  function toggleService(id: string, active: boolean) {
    if (!authed) return;
    run(() => adminApi.setServiceActive(id, !active));
  }

  async function saveCategory(e: React.FormEvent) {
    e.preventDefault();
    if (!authed) return;
    setError(null);
    setAddingCategory(true);
    try {
      const body = {
        name: categoryForm.name,
        slug: categoryForm.slug || undefined,
      };
      if (editingCategoryId) {
        await adminApi.updateCategory(editingCategoryId, body);
      } else {
        await adminApi.createCategory(body);
      }
      setCategoryForm({ ...EMPTY_CATEGORY_FORM });
      setEditingCategoryId(null);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save category");
    } finally {
      setAddingCategory(false);
    }
  }

  function editCategory(c: ServiceCategory) {
    setEditingCategoryId(c.id);
    setCategoryForm({ name: c.name, slug: c.slug });
  }

  function cancelCategoryEdit() {
    setEditingCategoryId(null);
    setCategoryForm({ ...EMPTY_CATEGORY_FORM });
  }

  function toggleCategory(id: string, active: boolean) {
    if (!authed) return;
    run(() => adminApi.setCategoryActive(id, !active));
  }

  async function saveStaff(e: React.FormEvent) {
    e.preventDefault();
    if (!authed) return;
    setError(null);
    setAddingStaff(true);
    try {
      const body = {
        name: staffForm.name,
        email: staffForm.email || undefined,
        phone: staffForm.phone || undefined,
      };
      if (editingStaffEditId) {
        await adminApi.updateStaff(editingStaffEditId, body);
      } else {
        await adminApi.createStaff(body);
      }
      setStaffForm({ ...EMPTY_STAFF_FORM });
      setEditingStaffEditId(null);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save staff");
    } finally {
      setAddingStaff(false);
    }
  }

  function editStaff(s: AdminStaff) {
    setEditingStaffEditId(s.id);
    setStaffForm({
      name: s.name,
      email: s.email ?? "",
      phone: s.phone ?? "",
    });
  }

  function cancelStaffEdit() {
    setEditingStaffEditId(null);
    setStaffForm({ ...EMPTY_STAFF_FORM });
  }

  function toggleStaff(id: string, active: boolean) {
    if (!authed) return;
    run(() => adminApi.setStaffActive(id, !active));
  }

  function deleteStaff(s: AdminStaff) {
    if (!authed) return;
    if (!window.confirm(`Delete ${s.name}? Their booking history is kept, but they can no longer sign in.`)) {
      return;
    }
    run(() => adminApi.deleteStaff(s.id));
  }

  async function inviteStaff(id: string) {
    if (!authed) return;
    setError(null);
    try {
      const { token: inviteToken } = await adminApi.inviteStaff(id);
      setInviteLink(`${window.location.origin}/staff/register?token=${inviteToken}`);
      setCopiedInvite(false);
    } catch (e) {
      // A link can only be generated once. If one already exists, surface it
      // instead of failing, so the admin can still copy it.
      try {
        const status = await adminApi.staffInviteStatus(id);
        if (status.issued && status.token) {
          setInviteLink(`${window.location.origin}/staff/register?token=${status.token}`);
          setCopiedInvite(false);
          return;
        }
      } catch {
        // fall through to the generic error below
      }
      setError(e instanceof Error ? e.message : "Failed to create invite");
    }
  }

  async function copyInviteLink() {
    if (!inviteLink) return;
    try {
      await navigator.clipboard.writeText(inviteLink);
    } catch {
      // Clipboard may be unavailable; the link is still visible to copy manually.
    }
    setCopiedInvite(true);
    setTimeout(() => {
      setInviteLink(null);
      setCopiedInvite(false);
    }, 2000);
  }

  function openAvailability(id: string) {
    if (!authed) return;
    setEditingStaffId(id);
    setAvailLoading(true);
    setError(null);
    adminApi
      .staffAvailability(id)
      .then((av) => {
        setAvailServiceIds(av.serviceIds);
        const map: Record<number, { startTime: string; endTime: string }> = {};
        for (const h of av.hours) {
          map[h.dayOfWeek] = { startTime: toTimeInput(h.startTime), endTime: toTimeInput(h.endTime) };
        }
        setAvailHours(map);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load availability"))
      .finally(() => setAvailLoading(false));
  }

  function toggleServiceCheck(id: string) {
    setAvailServiceIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  }

  function setHour(day: number, field: "startTime" | "endTime", value: string) {
    setAvailHours((prev) => ({
      ...prev,
      [day]: { ...(prev[day] ?? { startTime: "", endTime: "" }), [field]: value },
    }));
  }

  async function saveAvailability() {
    if (!authed || !editingStaffId) return;
    const hours: { dayOfWeek: number; startTime: string; endTime: string }[] = [];
    for (const d of DAYS) {
      const h = availHours[d.day];
      if (h && h.startTime && h.endTime) {
        hours.push({ dayOfWeek: d.day, startTime: h.startTime, endTime: h.endTime });
      }
    }
    setError(null);
    try {
      await adminApi.saveStaffAvailability(editingStaffId, {
        serviceIds: availServiceIds,
        hours,
      });
      setEditingStaffId(null);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save availability");
    }
  }

  const totalPages = Math.max(1, Math.ceil(bookingsTotal / PAGE_SIZE));

  const TABS: { key: Tab; label: string }[] = [
    { key: "reports", label: "Reports" },
    { key: "bookings", label: "Bookings" },
    { key: "staff", label: "Staff" },
    { key: "services", label: "Services" },
    { key: "categories", label: "Categories" },
    { key: "reviews", label: "Reviews" },
  ];

  return (
    <div className="space-y-8">
      <h1 className="text-3xl font-semibold tracking-tight">Admin dashboard</h1>

      <div className="flex flex-wrap gap-2">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`rounded-full px-4 py-2 text-sm font-medium transition-colors ${
              tab === t.key
                ? "bg-hoa-gold text-hoa-ink"
                : "border border-hoa-border-strong hover:border-hoa-gold hover:text-hoa-text"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {error && <p className="rounded-lg bg-red-500/10 px-4 py-3 text-sm text-red-400">{error}</p>}
      {busy && <p className="text-sm text-hoa-muted">Refreshing…</p>}

      {tab === "reports" && (
        <section>
          <h2 className="text-lg font-semibold">Reports</h2>

          <div className="mt-4 flex flex-wrap items-center gap-3 text-sm">
            <label className="flex items-center gap-2">
              From
              <input
                type="date"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
                className="rounded-lg border border-hoa-border-strong bg-white/5 px-2 py-1.5 text-sm text-hoa-text focus:border-hoa-gold focus:outline-none"
              />
            </label>
            <label className="flex items-center gap-2">
              To
              <input
                type="date"
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
                className="rounded-lg border border-hoa-border-strong bg-white/5 px-2 py-1.5 text-sm text-hoa-text focus:border-hoa-gold focus:outline-none"
              />
            </label>
            <button
              type="button"
              onClick={downloadPaymentsCsv}
              className="rounded-full border border-hoa-border-strong px-3 py-1.5 text-xs hover:border-hoa-gold hover:text-hoa-text"
            >
              Download payments CSV
            </button>
          </div>

          {profit && (
            <div className="mt-4 grid gap-4 sm:grid-cols-3">
              <StatCard label="Gross revenue" value={formatMoney(profit.grossRevenue)} />
              <StatCard label="Refunds" value={formatMoney(profit.refunds)} />
              <StatCard label="Net profit" value={formatMoney(profit.netProfit)} />
            </div>
          )}

          <div className="mt-4 grid gap-4 lg:grid-cols-2">
            <div className="hoa-surface p-5">
              <h3 className="font-medium">Best selling</h3>
              <Table
                rows={bestSelling.map((b) => [b.serviceName, String(b.completed), formatMoney(b.revenue)])}
                head={["Service", "Completed", "Revenue"]}
              />
            </div>
            <div className="hoa-surface p-5">
              <h3 className="font-medium">Staff performance</h3>
              <Table
                rows={staff.map((s) => [
                  s.staffName,
                  String(s.bookings),
                  String(s.completed),
                  String(s.cancelled),
                  formatMoney(s.revenue),
                ])}
                head={["Staff", "Bookings", "Completed", "Cancelled", "Revenue"]}
              />
            </div>
          </div>
        </section>
      )}

      {tab === "bookings" && (
        <section>
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold">Bookings</h2>
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as BookingStatus | "");
                setBookingsPage(0);
              }}
              className="rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            >
              <option value="">All statuses</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>

          <div className="mt-4 hoa-surface overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-hoa-border bg-white/5 text-left text-hoa-muted">
                <tr>
                  <th className="px-4 py-3 font-medium">Reference</th>
                  <th className="px-4 py-3 font-medium">Customer</th>
                  <th className="px-4 py-3 font-medium">Service</th>
                  <th className="px-4 py-3 font-medium">Staff</th>
                  <th className="px-4 py-3 font-medium">Start</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Payment</th>
                  <th className="px-4 py-3 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {bookings.map((b) => {
                  const actions = [
                    ...(b.status === "PENDING_PAYMENT"
                      ? [{ label: "Mark paid", onSelect: () => run(() => adminApi.markPaid(b.id)) }]
                      : []),
                    ...(TRANSITIONS[b.status] ?? []).map((action) => ({
                      label: action.charAt(0).toUpperCase() + action.slice(1),
                      onSelect: () => run(() => adminApi.transition(b.id, action)),
                    })),
                  ];
                  return (
                    <tr key={b.id} className="border-b border-hoa-border last:border-0">
                      <td className="px-4 py-3 font-mono">{b.reference}</td>
                      <td className="px-4 py-3">{b.customerName}</td>
                      <td className="px-4 py-3">{b.serviceName}</td>
                      <td className="px-4 py-3">{b.staffName}</td>
                      <td className="px-4 py-3">{formatDateTime(b.startAt)}</td>
                      <td className="px-4 py-3">{b.status}</td>
                      <td className="px-4 py-3">
                        {PAYMENT_STATE_LABELS[b.paymentState] ?? b.paymentState}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {actions.length > 0 ? (
                          <KebabMenu actions={actions} />
                        ) : (
                          <span className="text-hoa-muted">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="mt-3 flex items-center justify-between text-sm">
            <p className="text-hoa-muted">
              {bookingsTotal} booking{bookingsTotal === 1 ? "" : "s"} · page {bookingsPage + 1} of {totalPages}
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => setBookingsPage((p) => Math.max(0, p - 1))}
                disabled={bookingsPage === 0}
                className="rounded-full border border-hoa-border-strong px-4 py-1.5 text-xs hover:border-hoa-gold disabled:opacity-40"
              >
                Previous
              </button>
              <button
                onClick={() => setBookingsPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={bookingsPage >= totalPages - 1}
                className="rounded-full border border-hoa-border-strong px-4 py-1.5 text-xs hover:border-hoa-gold disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        </section>
      )}

      {tab === "staff" && (
        <section>
          <h2 className="text-lg font-semibold">Staff</h2>

          <form
            onSubmit={saveStaff}
            className="mt-4 grid gap-3 hoa-surface p-5 sm:grid-cols-2 lg:grid-cols-3"
          >
            <input
              placeholder="Name"
              value={staffForm.name}
              onChange={(e) => setStaffForm({ ...staffForm, name: e.target.value })}
              required
              className="rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            />
            <input
              type="email"
              placeholder="Email"
              value={staffForm.email}
              onChange={(e) => setStaffForm({ ...staffForm, email: e.target.value })}
              required
              className="rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            />
            <input
              placeholder="Phone"
              value={staffForm.phone}
              onChange={(e) => setStaffForm({ ...staffForm, phone: e.target.value })}
              required
              className="rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            />
            {editingStaffEditId && (
              <button
                type="button"
                onClick={cancelStaffEdit}
                className="rounded-full border border-hoa-border-strong px-6 py-3 text-sm text-hoa-muted"
              >
                Cancel
              </button>
            )}
            <button
              type="submit"
              disabled={addingStaff}
              className="cta-gradient rounded-full px-6 py-3 text-sm font-semibold disabled:opacity-40"
            >
              {addingStaff ? "Saving…" : editingStaffEditId ? "Save staff" : "Add staff"}
            </button>
          </form>
          <p className="mt-2 text-xs text-hoa-muted">
            Name, email and phone are for notifications only — the staff member sets their own login
            details via the invite link.
          </p>

          <div className="mt-4 hoa-surface overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-hoa-border bg-white/5 text-left text-hoa-muted">
                <tr>
                  <th className="px-4 py-3 font-medium">Name</th>
                  <th className="px-4 py-3 font-medium">Email</th>
                  <th className="px-4 py-3 font-medium">Phone</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {staffMembers.map((s) => (
                  <tr key={s.id} className="border-b border-hoa-border last:border-0">
                    <td className="px-4 py-3">{s.name}</td>
                    <td className="px-4 py-3">{s.email ?? "—"}</td>
                    <td className="px-4 py-3">{s.phone ?? "—"}</td>
                    <td className="px-4 py-3">{s.active ? "Active" : "Inactive"}</td>
                    <td className="px-4 py-3 text-right">
                      <KebabMenu
                        actions={[
                          { label: "Edit", onSelect: () => editStaff(s) },
                          { label: "Availability", onSelect: () => openAvailability(s.id) },
                          { label: "Invite", onSelect: () => inviteStaff(s.id) },
                          {
                            label: s.active ? "Deactivate" : "Activate",
                            onSelect: () => toggleStaff(s.id, s.active),
                          },
                          { label: "Delete", onSelect: () => deleteStaff(s), danger: true },
                        ]}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {inviteLink && (
            <div className="mt-4 hoa-surface p-4">
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-medium">Staff invite link</p>
                <div className="flex gap-2">
                  <button
                    onClick={copyInviteLink}
                    className="cta-gradient rounded-full px-3 py-1 text-xs font-semibold"
                  >
                    {copiedInvite ? "Copied!" : "Copy"}
                  </button>
                  <button
                    onClick={() => setInviteLink(null)}
                    aria-label="Clear invite link"
                    className="rounded-full border border-hoa-border-strong px-3 py-1 text-xs hover:border-hoa-gold hover:text-hoa-text"
                  >
                    ×
                  </button>
                </div>
              </div>
              <code className="mt-2 block break-all text-xs text-hoa-muted">{inviteLink}</code>
            </div>
          )}

          {editingStaffId && (
            <div className="mt-6 space-y-6 hoa-surface p-5">
              <div className="flex items-center justify-between">
                <h3 className="font-medium">
                  Availability — {staffMembers.find((s) => s.id === editingStaffId)?.name ?? ""}
                </h3>
                <button
                  onClick={() => setEditingStaffId(null)}
                  className="rounded-full border border-hoa-border-strong px-4 py-2 text-sm hover:border-hoa-gold hover:text-hoa-text"
                >
                  Close
                </button>
              </div>

              {availLoading ? (
                <p className="text-sm text-hoa-muted">Loading…</p>
              ) : (
                <div className="space-y-6">
                  <div>
                    <p className="text-sm font-medium">Services performed</p>
                    <div className="mt-2 flex flex-wrap gap-2">
                      {services.map((sv) => (
                        <label
                          key={sv.id}
                          className="flex cursor-pointer items-center gap-2 rounded-full border border-hoa-border-strong px-3 py-1 text-sm"
                        >
                          <input
                            type="checkbox"
                            checked={availServiceIds.includes(sv.id)}
                            onChange={() => toggleServiceCheck(sv.id)}
                          />
                          {sv.name}
                        </label>
                      ))}
                    </div>
                  </div>

                  <div>
                    <p className="text-sm font-medium">Working hours</p>
                    <div className="mt-2 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                      {DAYS.map((d) => (
                        <div key={d.day} className="flex items-center gap-2">
                          <span className="w-10 text-sm text-hoa-muted">{d.label}</span>
                          <input
                            type="time"
                            value={availHours[d.day]?.startTime ?? ""}
                            onChange={(e) => setHour(d.day, "startTime", e.target.value)}
                            className="rounded-lg border border-hoa-border-strong bg-white/5 px-2 py-1.5 text-sm text-hoa-text focus:border-hoa-gold focus:outline-none"
                          />
                          <span className="text-hoa-muted">–</span>
                          <input
                            type="time"
                            value={availHours[d.day]?.endTime ?? ""}
                            onChange={(e) => setHour(d.day, "endTime", e.target.value)}
                            className="rounded-lg border border-hoa-border-strong bg-white/5 px-2 py-1.5 text-sm text-hoa-text focus:border-hoa-gold focus:outline-none"
                          />
                        </div>
                      ))}
                    </div>
                    <p className="mt-2 text-xs text-hoa-muted">
                      Leave both times empty for a day the staff member is off.
                    </p>
                  </div>

                  <button
                    onClick={saveAvailability}
                    className="cta-gradient rounded-full px-6 py-3 text-sm font-semibold"
                  >
                    Save availability
                  </button>
                </div>
              )}
            </div>
          )}
        </section>
      )}

      {tab === "services" && (
        <section>
          <h2 className="text-lg font-semibold">Services</h2>

          <form
            onSubmit={saveService}
            className="mt-4 grid gap-3 hoa-surface p-5 sm:grid-cols-2 lg:grid-cols-3"
          >
            <input
              placeholder="Name"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
              className="rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            />
            <input
              placeholder="Slug (optional)"
              value={form.slug}
              onChange={(e) => setForm({ ...form, slug: e.target.value })}
              className="rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            />
            <input
              placeholder="Description (optional)"
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              className="rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            />
            <input
              type="number"
              min="1"
              placeholder="Duration (minutes)"
              value={form.durationMinutes}
              onChange={(e) => setForm({ ...form, durationMinutes: e.target.value })}
              required
              className="rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            />
            <input
              type="number"
              min="0"
              step="0.01"
              placeholder="Price (₦)"
              value={form.price}
              onChange={(e) => setForm({ ...form, price: e.target.value })}
              required
              className="rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            />
            <input
              type="number"
              min="0"
              placeholder="Buffer (minutes)"
              value={form.bufferMinutes}
              onChange={(e) => setForm({ ...form, bufferMinutes: e.target.value })}
              className="rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            />
            <select
              value={form.categoryId}
              onChange={(e) => setForm({ ...form, categoryId: e.target.value })}
              className="rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            >
              <option value="">No category</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            {editingServiceId && (
              <button
                type="button"
                onClick={cancelServiceEdit}
                className="rounded-full border border-hoa-border-strong px-6 py-3 text-sm text-hoa-muted"
              >
                Cancel
              </button>
            )}
            <button
              type="submit"
              disabled={adding}
              className="cta-gradient rounded-full px-6 py-3 text-sm font-semibold disabled:opacity-40"
            >
              {adding ? "Saving…" : editingServiceId ? "Save service" : "Add service"}
            </button>
          </form>

          <div className="mt-4 hoa-surface overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-hoa-border bg-white/5 text-left text-hoa-muted">
                <tr>
                  <th className="px-4 py-3 font-medium">Name</th>
                  <th className="px-4 py-3 font-medium">Category</th>
                  <th className="px-4 py-3 font-medium">Duration</th>
                  <th className="px-4 py-3 font-medium">Price</th>
                  <th className="px-4 py-3 font-medium">Buffer</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {services.map((s) => (
                  <tr key={s.id} className="border-b border-hoa-border last:border-0">
                    <td className="px-4 py-3">{s.name}</td>
                    <td className="px-4 py-3">{s.categoryName ?? "—"}</td>
                    <td className="px-4 py-3">{formatDuration(s.durationMinutes)}</td>
                    <td className="px-4 py-3">{formatMoney(s.price)}</td>
                    <td className="px-4 py-3">{formatDuration(s.bufferMinutes)}</td>
                    <td className="px-4 py-3">{s.active ? "Active" : "Inactive"}</td>
                    <td className="px-4 py-3 text-right">
                      <KebabMenu
                        actions={[
                          { label: "Edit", onSelect: () => editService(s) },
                          {
                            label: s.active ? "Deactivate" : "Activate",
                            onSelect: () => toggleService(s.id, s.active),
                          },
                        ]}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {tab === "categories" && (
        <section>
          <h2 className="text-lg font-semibold">Categories</h2>

          <form
            onSubmit={saveCategory}
            className="mt-4 grid gap-3 hoa-surface p-5 sm:grid-cols-2 lg:grid-cols-3"
          >
            <input
              placeholder="Name"
              value={categoryForm.name}
              onChange={(e) => setCategoryForm({ ...categoryForm, name: e.target.value })}
              required
              className="rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            />
            <input
              placeholder="Slug (optional)"
              value={categoryForm.slug}
              onChange={(e) => setCategoryForm({ ...categoryForm, slug: e.target.value })}
              className="rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none"
            />
            {editingCategoryId && (
              <button
                type="button"
                onClick={cancelCategoryEdit}
                className="rounded-full border border-hoa-border-strong px-6 py-3 text-sm text-hoa-muted"
              >
                Cancel
              </button>
            )}
            <button
              type="submit"
              disabled={addingCategory}
              className="cta-gradient rounded-full px-6 py-3 text-sm font-semibold disabled:opacity-40"
            >
              {addingCategory ? "Saving…" : editingCategoryId ? "Save category" : "Add category"}
            </button>
          </form>

          <div className="mt-4 hoa-surface overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-hoa-border bg-white/5 text-left text-hoa-muted">
                <tr>
                  <th className="px-4 py-3 font-medium">Name</th>
                  <th className="px-4 py-3 font-medium">Slug</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {categories.map((c) => (
                  <tr key={c.id} className="border-b border-hoa-border last:border-0">
                    <td className="px-4 py-3">{c.name}</td>
                    <td className="px-4 py-3 font-mono">{c.slug}</td>
                    <td className="px-4 py-3">{c.active ? "Active" : "Inactive"}</td>
                    <td className="px-4 py-3 text-right">
                      <KebabMenu
                        actions={[
                          { label: "Edit", onSelect: () => editCategory(c) },
                          {
                            label: c.active ? "Deactivate" : "Activate",
                            onSelect: () => toggleCategory(c.id, c.active),
                          },
                        ]}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {tab === "reviews" && (
        <section>
          <h2 className="text-lg font-semibold">Review moderation</h2>
          <div className="mt-4 hoa-surface overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-hoa-border bg-white/5 text-left text-hoa-muted">
                <tr>
                  <th className="px-4 py-3 font-medium">Rating</th>
                  <th className="px-4 py-3 font-medium">Comment</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {reviews.map((r) => (
                  <tr key={r.id} className="border-b border-hoa-border last:border-0">
                    <td className="px-4 py-3">{"★".repeat(r.rating)}</td>
                    <td className="max-w-md px-4 py-3">{r.comment}</td>
                    <td className="px-4 py-3">{r.status}</td>
                    <td className="px-4 py-3 text-right">
                      <KebabMenu
                        actions={[
                          {
                            label: r.status === "PUBLISHED" ? "Hide" : "Publish",
                            onSelect: () =>
                              run(() =>
                                adminApi.setReviewStatus(r.id, r.status === "PUBLISHED" ? "hide" : "publish"),
                              ),
                          },
                        ]}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="hoa-surface p-5">
      <p className="text-sm text-hoa-muted">{label}</p>
      <p className="mt-1 text-2xl font-semibold tracking-tight">{value}</p>
    </div>
  );
}

function Table({ head, rows }: { head: string[]; rows: string[][] }) {
  return (
    <table className="mt-3 w-full text-sm">
      <thead className="text-left text-hoa-muted">
        <tr>
          {head.map((h) => (
            <th key={h} className="py-1 pr-4 font-medium">
              {h}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.length === 0 ? (
          <tr>
            <td colSpan={head.length} className="py-2 text-hoa-muted">
              No data
            </td>
          </tr>
        ) : (
          rows.map((row, i) => (
            <tr key={i}>
              {row.map((cell, j) => (
                <td key={j} className="py-1 pr-4">
                  {cell}
                </td>
              ))}
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}
