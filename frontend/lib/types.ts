export type UUID = string;

export interface Service {
  id: UUID;
  name: string;
  slug: string;
  description: string;
  durationMinutes: number;
  price: number;
  categoryId: UUID | null;
  categoryName: string | null;
}

export interface AdminService {
  id: UUID;
  name: string;
  slug: string;
  description: string | null;
  durationMinutes: number;
  price: number;
  bufferMinutes: number;
  active: boolean;
  categoryId: UUID | null;
  categoryName: string | null;
}

export interface CreateServiceInput {
  name: string;
  slug?: string;
  description?: string;
  durationMinutes: number;
  price: number;
  bufferMinutes?: number;
  categoryId?: string;
}

export interface ServiceCategory {
  id: UUID;
  name: string;
  slug: string;
  active: boolean;
}

export interface CreateCategoryInput {
  name: string;
  slug?: string;
}

export interface AdminStaff {
  id: UUID;
  name: string;
  email: string | null;
  phone: string | null;
  bio: string | null;
  photoUrl: string | null;
  active: boolean;
}

export interface CreateStaffInput {
  name: string;
  email?: string;
  phone?: string;
  bio?: string;
  photoUrl?: string;
}

export interface StaffHours {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

export interface StaffAvailability {
  staffId: UUID;
  serviceIds: UUID[];
  hours: StaffHours[];
}

export interface AvailabilitySlot {
  start: string; // ISO-8601 instant (UTC)
  end: string;
  staffId: UUID;
  staffName: string;
}

export interface Customer {
  id: UUID;
  firstName: string;
  lastName: string;
  username: string | null;
  email: string;
  phone: string;
  createdAt: string;
}

export type BookingStatus =
  | "PENDING_PAYMENT"
  | "CONFIRMED"
  | "COMPLETED"
  | "CANCELLED"
  | "NO_SHOW"
  | "EXPIRED";

export interface Booking {
  id: UUID;
  reference: string;
  customerId: UUID;
  serviceId: UUID;
  staffId: UUID;
  startAt: string;
  endAt: string;
  status: BookingStatus;
  price: number;
  depositAmount: number;
  createdAt: string;
}

export interface DepositInstructions {
  providerReference: string;
  accountNumber: string;
  accountName: string;
  bankName: string;
}

export interface BookingResult {
  booking: Booking;
  depositInstructions: DepositInstructions;
}

export interface PublishedReview {
  id: UUID;
  customerName: string;
  rating: number;
  comment: string;
  createdAt: string;
}

export type ReviewStatus = "PUBLISHED" | "HIDDEN";

export interface Review {
  id: UUID;
  bookingId: UUID;
  customerId: UUID;
  rating: number;
  comment: string;
  status: ReviewStatus;
  createdAt: string;
}

export interface ProfitReport {
  grossRevenue: number;
  refunds: number;
  netProfit: number;
}

export interface BestSeller {
  serviceName: string;
  completed: number;
  revenue: number;
}

export interface StaffPerformance {
  staffName: string;
  bookings: number;
  completed: number;
  cancelled: number;
  revenue: number;
}

export interface AdminBooking {
  id: UUID;
  reference: string;
  customerName: string;
  serviceName: string;
  staffName: string;
  startAt: string;
  endAt: string;
  status: BookingStatus;
  price: number;
  depositAmount: number;
}

export interface AdminBookingPage {
  items: AdminBooking[];
  total: number;
}

export interface StaffInviteStatus {
  issued: boolean;
  token: string | null;
}

export interface BookingSummary {
  id: UUID;
  reference: string;
  serviceName: string;
  staffName: string;
  startAt: string;
  status: BookingStatus;
  price: number;
  depositAmount: number;
  createdAt: string;
  reviewed: boolean;
}

export interface StaffProfile {
  id: UUID;
  name: string;
  email: string | null;
  phone: string | null;
  bio: string | null;
  photoUrl: string | null;
  active: boolean;
  services: string[];
  hours: StaffHours[];
  bookings: BookingSummary[];
  clockedIn: boolean;
  workedMinutesToday: number;
}

export interface PublicStaff {
  id: UUID;
  name: string;
  bio: string | null;
  photoUrl: string | null;
  services: string[];
  available: boolean;
}

export interface CustomerNotification {
  id: number;
  type: string;
  serviceName: string | null;
  startAt: string | null;
  createdAt: string;
  readAt: string | null;
}

export interface AttendanceStatus {
  clockedIn: boolean;
  clockedInAt: string | null;
}
