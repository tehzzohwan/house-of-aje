"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { staffApi } from "@/lib/api";
import PasswordField from "@/components/PasswordField";

export default function StaffRegisterPage() {
  const router = useRouter();
  const [token] = useState(() =>
    typeof window === "undefined"
      ? ""
      : new URLSearchParams(window.location.search).get("token") ?? "",
  );
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await staffApi.register({ token, name, phone, password });
      router.push("/staff");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
      setLoading(false);
    }
  }

  if (!token) {
    return (
      <div className="mx-auto max-w-sm space-y-4 text-center">
        <h1 className="text-3xl font-semibold tracking-tight">Staff registration</h1>
        <p className="text-zinc-500">
          Ask your admin for an invite link to register a staff account.
        </p>
        <Link
          href="/login"
          className="inline-block w-full rounded-full bg-black px-6 py-3 text-sm font-medium text-white dark:bg-white dark:text-black"
        >
          Back to sign in
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="mx-auto max-w-sm space-y-4">
      <h1 className="text-3xl font-semibold tracking-tight">Staff registration</h1>
      <p className="text-sm text-zinc-500">
        Complete your staff account using the invite your admin sent.
      </p>

      <label className="block text-sm font-medium">
        Name
        <input
          required
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="mt-2 block w-full rounded-lg border border-black/15 bg-white px-3 py-2 text-sm dark:border-white/15 dark:bg-black"
        />
      </label>

      <label className="block text-sm font-medium">
        Phone <span className="font-normal text-zinc-400">(optional)</span>
        <input
          type="tel"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          className="mt-2 block w-full rounded-lg border border-black/15 bg-white px-3 py-2 text-sm dark:border-white/15 dark:bg-black"
        />
      </label>

      <label className="block text-sm font-medium">
        Password
        <PasswordField required value={password} onChange={setPassword} autoComplete="new-password" />
      </label>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <button
        type="submit"
        disabled={loading}
        className="w-full rounded-full bg-black px-6 py-3 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:opacity-40 dark:bg-white dark:text-black dark:hover:bg-zinc-200"
      >
        {loading ? "Registering…" : "Register"}
      </button>

      <p className="text-center text-sm text-zinc-500">
        Already registered?{" "}
        <Link href="/login" className="font-medium text-black underline dark:text-white">
          Sign in
        </Link>
      </p>
    </form>
  );
}
