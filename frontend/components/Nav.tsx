"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { authApi, customerApi, staffApi } from "@/lib/api";

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
        } else {
          setDisplayName(null);
        }
      })
      .catch(() => {
        setRole(null);
        setDisplayName(null);
      });
  }, [pathname]);

  function signOut() {
    setRole(null);
    setDisplayName(null);
    setOpen(false);
    authApi.logout().catch(() => {});
    router.push("/");
  }

  const links = [
    { href: "/", label: "Services" },
    { href: "/book", label: "Book" },
    { href: "/reviews", label: "Reviews" },
  ];

  return (
    <nav className="flex items-center gap-6 text-sm">
      {links.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          className="text-zinc-600 hover:text-black dark:text-zinc-400 dark:hover:text-white"
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
            className="flex h-8 w-8 items-center justify-center rounded-full bg-black text-xs font-medium text-white dark:bg-white dark:text-black"
          >
            {initials(displayName ?? ROLE_LABEL[role])}
          </button>

          {open && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
              <div className="absolute right-0 z-50 mt-2 w-56 rounded-xl border border-black/10 bg-white p-2 shadow-lg dark:border-white/10 dark:bg-zinc-900">
                <div className="px-3 py-2">
                  <p className="truncate text-sm font-medium">
                    {displayName ?? ROLE_LABEL[role]}
                  </p>
                  <span className="mt-1 inline-block rounded-full bg-black/5 px-2 py-0.5 text-xs font-medium text-zinc-500 dark:bg-white/10">
                    {ROLE_LABEL[role]}
                  </span>
                </div>
                <Link
                  href={PROFILE_PATH[role]}
                  onClick={() => setOpen(false)}
                  className="block rounded-lg px-3 py-2 text-sm text-zinc-700 hover:bg-black/5 dark:text-zinc-200 dark:hover:bg-white/10"
                >
                  View profile
                </Link>
                <button
                  type="button"
                  onClick={signOut}
                  className="block w-full rounded-lg px-3 py-2 text-left text-sm text-zinc-700 hover:bg-black/5 dark:text-zinc-200 dark:hover:bg-white/10"
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
          className="text-zinc-600 hover:text-black dark:text-zinc-400 dark:hover:text-white"
        >
          Sign in
        </Link>
      )}
    </nav>
  );
}
