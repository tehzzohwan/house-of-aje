import type {
  AdminBooking,
  AdminBookingPage,
  AdminService,
  AdminStaff,
  AttendanceStatus,
  AvailabilitySlot,
  BestSeller,
  Booking,
  BookingResult,
  BookingStatus,
  BookingSummary,
  CreateCategoryInput,
  CreateServiceInput,
  CreateStaffInput,
  Customer,
  CustomerNotification,
  DepositInstructions,
  ProfitReport,
  PublicStaff,
  PublishedReview,
  Review,
  ReviewStatus,
  ReviewSummary,
  Service,
  ServiceCategory,
  StaffAvailability,
  StaffHours,
  StaffInviteStatus,
  StaffPerformance,
  StaffProfile,
} from "./types";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:6040";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

// Every request carries credentials so the browser attaches the HttpOnly session
// cookie. The JWT is never read by JavaScript — it lives only in the cookie.
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body != null && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers,
    credentials: "include",
  });

  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = (await res.json()) as { message?: string };
      if (typeof body.message === "string" && body.message) {
        message = body.message;
      }
    } catch {
      // non-JSON error body; keep the generic message
    }
    throw new ApiError(res.status, message);
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

function postJson<T>(path: string, body: unknown, init: RequestInit = {}): Promise<T> {
  return request<T>(path, { method: "POST", body: JSON.stringify(body), ...init });
}

export type Role = "ADMIN" | "STAFF" | "CUSTOMER";

/** Login/registration result: the role, only. The token travels as an HttpOnly cookie. */
export interface AuthResult {
  role: Role;
}

export const authApi = {
  login(identifier: string, password: string): Promise<AuthResult> {
    return postJson<AuthResult>("/api/auth/login", { identifier, password });
  },

  /** The authenticated role, or null when signed out. */
  me(): Promise<{ role: Role | null }> {
    return request<{ role: Role | null }>("/api/auth/me");
  },

  logout(): Promise<void> {
    return request<void>("/api/auth/logout", { method: "POST" });
  },
};

export const api = {
  listServices(): Promise<Service[]> {
    return request<Service[]>("/api/public/services");
  },

  availability(params: { serviceId: string; staffId?: string; date: string }): Promise<AvailabilitySlot[]> {
    const q = new URLSearchParams({ serviceId: params.serviceId, date: params.date });
    if (params.staffId) q.set("staffId", params.staffId);
    return request<AvailabilitySlot[]>(`/api/public/availability?${q.toString()}`);
  },

  /** Staff who perform a service, with an availability flag for the chosen date. */
  staff(serviceId: string, date?: string): Promise<PublicStaff[]> {
    const q = new URLSearchParams({ serviceId });
    if (date) q.set("date", date);
    return request<PublicStaff[]>(`/api/public/staff?${q.toString()}`);
  },

  createCustomer(body: { firstName: string; lastName: string; email: string; phone: string }): Promise<Customer> {
    return postJson<Customer>("/api/public/customers", body);
  },

  createBooking(
    body: { customerId: string; serviceId: string; staffId: string; startAt: string; payFull: boolean },
    idempotencyKey: string,
  ): Promise<BookingResult> {
    return postJson<BookingResult>("/api/public/bookings", body, {
      headers: { "Idempotency-Key": idempotencyKey },
    });
  },

  reviews(serviceId: string, limit = 10): Promise<PublishedReview[]> {
    return request<PublishedReview[]>(`/api/public/reviews?serviceId=${serviceId}&limit=${limit}`);
  },

  /** Latest published reviews plus the aggregate rating, for the landing page. */
  reviewsSummary(limit = 3): Promise<ReviewSummary> {
    return request<ReviewSummary>(`/api/public/reviews/latest?limit=${limit}`);
  },
};

function adminRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  return request<T>(path, init);
}

function adminText(path: string): Promise<string> {
  const headers = new Headers();
  headers.set("Accept", "text/csv");
  return fetch(`${API_URL}${path}`, { headers, credentials: "include" }).then(async (res) => {
    if (!res.ok) {
      throw new ApiError(res.status, `Request failed (${res.status})`);
    }
    return res.text();
  });
}

export const adminApi = {
  services(): Promise<AdminService[]> {
    return adminRequest<AdminService[]>("/api/admin/services");
  },

  createService(body: CreateServiceInput): Promise<AdminService> {
    return adminRequest<AdminService>("/api/admin/services", {
      method: "POST",
      body: JSON.stringify(body),
    });
  },

  updateService(id: string, body: CreateServiceInput): Promise<AdminService> {
    return adminRequest<AdminService>(`/api/admin/services/${id}`, {
      method: "PUT",
      body: JSON.stringify(body),
    });
  },

  setServiceActive(id: string, active: boolean): Promise<AdminService> {
    return adminRequest<AdminService>(
      `/api/admin/services/${id}/${active ? "activate" : "deactivate"}`,
      { method: "POST" },
    );
  },

  categories(): Promise<ServiceCategory[]> {
    return adminRequest<ServiceCategory[]>("/api/admin/categories");
  },

  createCategory(body: CreateCategoryInput): Promise<ServiceCategory> {
    return adminRequest<ServiceCategory>("/api/admin/categories", {
      method: "POST",
      body: JSON.stringify(body),
    });
  },

  updateCategory(id: string, body: CreateCategoryInput): Promise<ServiceCategory> {
    return adminRequest<ServiceCategory>(`/api/admin/categories/${id}`, {
      method: "PUT",
      body: JSON.stringify(body),
    });
  },

  setCategoryActive(id: string, active: boolean): Promise<ServiceCategory> {
    return adminRequest<ServiceCategory>(
      `/api/admin/categories/${id}/${active ? "activate" : "deactivate"}`,
      { method: "POST" },
    );
  },

  staff(): Promise<AdminStaff[]> {
    return adminRequest<AdminStaff[]>("/api/admin/staff");
  },

  createStaff(body: CreateStaffInput): Promise<AdminStaff> {
    return adminRequest<AdminStaff>("/api/admin/staff", {
      method: "POST",
      body: JSON.stringify(body),
    });
  },

  updateStaff(id: string, body: CreateStaffInput): Promise<AdminStaff> {
    return adminRequest<AdminStaff>(`/api/admin/staff/${id}`, {
      method: "PUT",
      body: JSON.stringify(body),
    });
  },

  setStaffActive(id: string, active: boolean): Promise<AdminStaff> {
    return adminRequest<AdminStaff>(
      `/api/admin/staff/${id}/${active ? "activate" : "deactivate"}`,
      { method: "POST" },
    );
  },

  inviteStaff(id: string): Promise<{ token: string }> {
    return adminRequest<{ token: string }>(`/api/admin/staff/${id}/invite`, { method: "POST" });
  },

  staffInviteStatus(id: string): Promise<StaffInviteStatus> {
    return adminRequest<StaffInviteStatus>(`/api/admin/staff/${id}/invite`);
  },

  deleteStaff(id: string): Promise<void> {
    return adminRequest<void>(`/api/admin/staff/${id}`, { method: "DELETE" });
  },

  staffAvailability(id: string): Promise<StaffAvailability> {
    return adminRequest<StaffAvailability>(`/api/admin/staff/${id}/availability`);
  },

  saveStaffAvailability(
    id: string,
    body: { serviceIds: string[]; hours: StaffHours[] },
  ): Promise<StaffAvailability> {
    return adminRequest<StaffAvailability>(`/api/admin/staff/${id}/availability`, {
      method: "PUT",
      body: JSON.stringify(body),
    });
  },

  bookings(status?: BookingStatus, page = 0, size = 10): Promise<AdminBookingPage> {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) q.set("status", status);
    return adminRequest<AdminBookingPage>(`/api/admin/bookings?${q.toString()}`);
  },

  transition(
    id: string,
    action: "confirm" | "complete" | "cancel" | "no-show" | "expire",
  ): Promise<Booking> {
    return adminRequest<Booking>(`/api/admin/bookings/${id}/${action}`, { method: "POST" });
  },

  markPaid(id: string): Promise<Booking> {
    return adminRequest<Booking>(`/api/admin/bookings/${id}/mark-paid`, { method: "POST" });
  },

  profit(from?: string, to?: string): Promise<ProfitReport> {
    const q = new URLSearchParams();
    if (from) q.set("from", from);
    if (to) q.set("to", to);
    const qs = q.toString();
    return adminRequest<ProfitReport>(`/api/admin/reports/profit${qs ? `?${qs}` : ""}`);
  },

  paymentsCsv(from?: string, to?: string): Promise<string> {
    const q = new URLSearchParams();
    if (from) q.set("from", from);
    if (to) q.set("to", to);
    const qs = q.toString();
    return adminText(`/api/admin/reports/payments.csv${qs ? `?${qs}` : ""}`);
  },

  bestSelling(from?: string, to?: string): Promise<BestSeller[]> {
    const q = new URLSearchParams();
    if (from) q.set("from", from);
    if (to) q.set("to", to);
    const qs = q.toString();
    return adminRequest<BestSeller[]>(`/api/admin/reports/best-selling${qs ? `?${qs}` : ""}`);
  },

  staffPerformance(from?: string, to?: string): Promise<StaffPerformance[]> {
    const q = new URLSearchParams();
    if (from) q.set("from", from);
    if (to) q.set("to", to);
    const qs = q.toString();
    return adminRequest<StaffPerformance[]>(`/api/admin/reports/staff-performance${qs ? `?${qs}` : ""}`);
  },

  reviews(status?: ReviewStatus, limit = 50): Promise<Review[]> {
    const q = new URLSearchParams({ limit: String(limit) });
    if (status) q.set("status", status);
    return adminRequest<Review[]>(`/api/admin/reviews?${q.toString()}`);
  },

  setReviewStatus(id: string, action: "hide" | "publish"): Promise<Review> {
    return adminRequest<Review>(`/api/admin/reviews/${id}/${action}`, { method: "POST" });
  },
};

export const customerApi = {
  register(body: {
    firstName: string;
    lastName: string;
    username: string;
    email: string;
    phone: string;
    password: string;
  }): Promise<AuthResult> {
    return postJson<AuthResult>("/api/auth/customer/register", body);
  },

  me(): Promise<Customer> {
    return adminRequest<Customer>("/api/customer/me");
  },

  bookings(): Promise<BookingSummary[]> {
    return adminRequest<BookingSummary[]>("/api/customer/bookings");
  },

  /** A booking placed by the logged-in customer; the server derives the customer. */
  createBooking(
    body: { serviceId: string; staffId: string; startAt: string; payFull: boolean },
    idempotencyKey: string,
  ): Promise<BookingResult> {
    return adminRequest<BookingResult>("/api/customer/bookings", {
      method: "POST",
      body: JSON.stringify(body),
      headers: { "Idempotency-Key": idempotencyKey },
    });
  },

  cancelBooking(id: string): Promise<Booking> {
    return adminRequest<Booking>(`/api/customer/bookings/${id}/cancel`, { method: "POST" });
  },

  /** Start in-app payment of the remaining balance on a confirmed booking. */
  payBalance(id: string): Promise<DepositInstructions> {
    return adminRequest<DepositInstructions>(`/api/customer/bookings/${id}/pay-balance`, {
      method: "POST",
    });
  },

  /** Re-issue bank-transfer instructions for a booking still awaiting deposit/full payment. */
  payDeposit(id: string): Promise<DepositInstructions> {
    return adminRequest<DepositInstructions>(`/api/customer/bookings/${id}/pay-deposit`, {
      method: "POST",
    });
  },

  notifications(): Promise<CustomerNotification[]> {
    return adminRequest<CustomerNotification[]>("/api/customer/notifications");
  },

  markNotificationRead(id: number): Promise<void> {
    return adminRequest<void>(`/api/customer/notifications/${id}/read`, { method: "POST" });
  },

  review(bookingId: string, rating: number, comment: string): Promise<void> {
    return adminRequest<void>(`/api/customer/bookings/${bookingId}/review`, {
      method: "POST",
      body: JSON.stringify({ rating, comment }),
    });
  },
};

export const staffApi = {
  register(body: {
    token: string;
    name: string;
    phone: string;
    password: string;
  }): Promise<AuthResult> {
    return postJson<AuthResult>("/api/auth/staff/register", body);
  },

  me(): Promise<StaffProfile> {
    return adminRequest<StaffProfile>("/api/staff/me");
  },

  attendanceStatus(): Promise<AttendanceStatus> {
    return adminRequest<AttendanceStatus>("/api/staff/attendance/status");
  },

  clockIn(): Promise<AttendanceStatus> {
    return adminRequest<AttendanceStatus>("/api/staff/attendance/clock-in", { method: "POST" });
  },

  clockOut(): Promise<AttendanceStatus> {
    return adminRequest<AttendanceStatus>("/api/staff/attendance/clock-out", { method: "POST" });
  },
};
