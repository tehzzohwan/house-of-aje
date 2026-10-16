"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { authApi, customerApi, staffApi } from "@/lib/api";
import type { CustomerNotification } from "@/lib/types";
import { formatDateTime } from "@/lib/format";

type Role = "ADMIN" | "STAFF" | "CUSTOMER";

const ROLE_LABEL: Record<Role, string> = {
  ADMIN: "Admin",
  STAFF: "Staff",
  CUSTOMER: "Customer",
};

const PROFILE_PATH: Record<Role, string> = {
  ADMIN: "/admin",
  STAFF: "/staff",
  CUSTOMER: "/account",
};

const NOTIFICATION_LABEL: Record<string, string> = {
  CONFIRMATION: "Booking confirmed",
  REMINDER_24H: "Appointment tomorrow",
  REMINDER_1H: "Appointment in 1 hour",
  SESSION_STARTED: "Your session has started",
  CANCELLATION: "Booking cancelled",
  REVIEW_REQUEST: "Please rate your visit",
};

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0][0]?.toUpperCase() ?? "?";
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export default function Nav() {
  const pathname = usePathname();
  const router = useRouter();
  const [role, setRole] = useState<Role | null>(null);
  const [displayName, setDisplayName] = useState<string | null>(null);
  const [notifications, setNotifications] = useState<CustomerNotification[]>([]);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    setOpen(false);

    authApi
      .me()
      .then(({ role: active }) => {
        setRole(active);

        if (active === "ADMIN") {
          setDisplayName("Admin");
        } else if (active === "STAFF") {
          staffApi
            .me()
            .then((p) => setDisplayName(p.name))
            .catch(() => setDisplayName("Staff"));
        } else if (active === "CUSTOMER") {
          customerApi
            .me()
            .then((c) => setDisplayName(`${c.firstName} ${c.lastName}`.trim()))
            .catch(() => setDisplayName("Customer"));
          customerApi
            .notifications()
            .then(setNotifications)
            .catch(() => setNotifications([]));
        } else {
          setDisplayName(null);
        }
      })
      .catch(() => {
        setRole(null);
        setDisplayName(null);
        setNotifications([]);
      });
  }, [pathname]);

  function signOut() {
    setRole(null);
    setDisplayName(null);
    setNotifications([]);
    setOpen(false);
    authApi.logout().catch(() => {});
    router.push("/");
  }

  function markRead(n: CustomerNotification) {
    if (n.readAt) return;
    customerApi
      .markNotificationRead(n.id)
      .then(() =>
        setNotifications((prev) =>
          prev.map((x) => (x.id === n.id ? { ...x, readAt: new Date().toISOString() } : x)),
        ),
      )
      .catch(() => {});
  }

  const unreadCount = notifications.filter((n) => !n.readAt).length;

  const links = [
    { href: "/services", label: "Services" },
    { href: "/book", label: "Book" },
    { href: "/reviews", label: "Reviews" },
  ];

  return (
    <nav className="flex items-center gap-6 text-sm">
      {links.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          className="text-hoa-muted hover:text-hoa-gold"
        >
          {item.label}
        </Link>
      ))}

      {role ? (
        <div className="relative">
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            aria-label="Account menu"
            aria-expanded={open}
            className="relative flex h-8 w-8 items-center justify-center rounded-full bg-hoa-gold text-xs font-semibold text-hoa-ink transition hover:brightness-110"
          >
            {initials(displayName ?? ROLE_LABEL[role])}
            {unreadCount > 0 && (
              <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-hoa-blush px-1 text-[10px] font-semibold text-hoa-ink">
                {unreadCount > 9 ? "9+" : unreadCount}
              </span>
            )}
          </button>

          {open && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
              <div className="hoa-surface absolute right-0 z-50 mt-2 w-72 p-2 shadow-xl">
                <div className="px-3 py-2">
                  <p className="truncate text-sm font-medium">
                    {displayName ?? ROLE_LABEL[role]}
                  </p>
                  <span className="mt-1 inline-block rounded-full bg-white/10 px-2 py-0.5 text-xs font-medium text-hoa-muted">
                    {ROLE_LABEL[role]}
                  </span>
                </div>

                {role === "CUSTOMER" && notifications.length > 0 && (
                  <div className="border-t border-hoa-border">
                    <p className="px-3 pb-1 pt-2 font-mono text-[11px] uppercase tracking-wider text-hoa-muted">
                      Notifications
                    </p>
                    <ul className="max-h-56 overflow-y-auto">
                      {notifications.slice(0, 5).map((n) => (
                        <li key={n.id}>
                          <button
                            type="button"
                            onClick={() => markRead(n)}
                            className="flex w-full items-start gap-2 rounded-lg px-3 py-2 text-left hover:bg-white/5"
                          >
                            <span
                              className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${
                                n.readAt ? "bg-hoa-border-strong" : "bg-hoa-gold"
                              }`}
                            />
                            <span className="min-w-0">
                              <span className="block truncate text-sm text-hoa-text">
                                {NOTIFICATION_LABEL[n.type] ?? n.type}
                              </span>
                              <span className="block truncate text-xs text-hoa-muted">
                                {n.serviceName ? `${n.serviceName} · ` : ""}
                                {formatDateTime(n.createdAt)}
                              </span>
                            </span>
                          </button>
                        </li>
                      ))}
                    </ul>
                    <Link
                      href="/account"
                      onClick={() => setOpen(false)}
                      className="block rounded-lg px-3 py-2 text-sm text-hoa-gold hover:bg-white/5"
                    >
                      View all
                    </Link>
                  </div>
                )}

                <Link
                  href={PROFILE_PATH[role]}
                  onClick={() => setOpen(false)}
                  className="block rounded-lg px-3 py-2 text-sm text-hoa-text hover:bg-white/5"
                >
                  View profile
                </Link>
                <button
                  type="button"
                  onClick={signOut}
                  className="block w-full rounded-lg px-3 py-2 text-left text-sm text-hoa-text hover:bg-white/5"
                >
                  Sign out
                </button>
              </div>
            </>
          )}
        </div>
      ) : (
        <Link
          href="/login"
          className="text-hoa-muted hover:text-hoa-gold"
        >
          Sign in
        </Link>
      )}
    </nav>
  );
}
