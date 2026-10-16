"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { authApi } from "@/lib/api";
import PasswordField from "@/components/PasswordField";

const HOME_BY_ROLE: Record<string, string> = {
  ADMIN: "/admin",
  STAFF: "/staff",
  CUSTOMER: "/account",
};

export default function LoginPage() {
  const router = useRouter();
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  // A relative path to return to after signing in (e.g. ?next=/book).
  const [next] = useState(() =>
    typeof window === "undefined"
      ? ""
      : new URLSearchParams(window.location.search).get("next") ?? "",
  );

  // Already signed in? Send the user straight to their home, never to the form.
  useEffect(() => {
    let cancelled = false;
    authApi
      .me()
      .then((result) => {
        if (cancelled || !result.role) return;
        const dest = next.startsWith("/") ? next : HOME_BY_ROLE[result.role] ?? "/";
        router.replace(dest);
      })
      .catch(() => {
        // Not signed in; stay on the form.
      });
    return () => {
      cancelled = true;
    };
  }, [router, next]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const result = await authApi.login(identifier, password);
      const dest = next.startsWith("/") ? next : HOME_BY_ROLE[result.role] ?? "/";
      router.push(dest);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign in failed");
      setLoading(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="mx-auto max-w-sm space-y-4">
      <h1 className="text-3xl font-semibold tracking-tight">Sign in</h1>
      <p className="text-sm text-zinc-500">
        Customers, staff and admins all sign in here.
      </p>

      <label className="block text-sm font-medium">
        Email, phone, or admin username
        <input
          required
          value={identifier}
          onChange={(e) => setIdentifier(e.target.value)}
          autoComplete="username"
          className="mt-2 block w-full rounded-lg border border-black/15 bg-white px-3 py-2 text-sm dark:border-white/15 dark:bg-black"
        />
      </label>

      <label className="block text-sm font-medium">
        Password
        <PasswordField
          required
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
        />
      </label>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <button
        type="submit"
        disabled={loading}
        className="w-full rounded-full bg-black px-6 py-3 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:opacity-40 dark:bg-white dark:text-black dark:hover:bg-zinc-200"
      >
        {loading ? "Signing in…" : "Sign in"}
      </button>

      <p className="text-center text-sm text-zinc-500">
        New here?{" "}
        <Link href="/account/register" className="font-medium text-black underline dark:text-white">
          Create an account
        </Link>
      </p>
    </form>
  );
}
