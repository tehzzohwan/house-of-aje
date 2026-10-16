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
        <p className="text-hoa-muted">
          Ask your admin for an invite link to register a staff account.
        </p>
        <Link
          href="/login"
          className="cta-gradient inline-block w-full px-6 py-3 text-sm font-semibold"
        >
          Back to sign in
        </Link>
      </div>
    );
  }

  const inputClass =
    "mt-2 block w-full rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none";

  return (
    <form onSubmit={onSubmit} className="mx-auto max-w-sm space-y-4">
      <h1 className="text-3xl font-semibold tracking-tight">Staff registration</h1>
      <p className="text-sm text-hoa-muted">
        Complete your staff account using the invite your admin sent.
      </p>

      <label className="block text-sm font-medium">
        Name
        <input
          required
          value={name}
          onChange={(e) => setName(e.target.value)}
          className={inputClass}
        />
      </label>

      <label className="block text-sm font-medium">
        Phone <span className="font-normal text-hoa-muted">(optional)</span>
        <input
          type="tel"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          className={inputClass}
        />
      </label>

      <label className="block text-sm font-medium">
        Password
        <PasswordField required value={password} onChange={setPassword} autoComplete="new-password" />
      </label>

      {error && <p className="text-sm text-red-400">{error}</p>}

      <button
        type="submit"
        disabled={loading}
        className="cta-gradient w-full px-6 py-3 text-sm font-semibold disabled:opacity-40"
      >
        {loading ? "Registering…" : "Register"}
      </button>

      <p className="text-center text-sm text-hoa-muted">
        Already registered?{" "}
        <Link href="/login" className="font-medium text-hoa-gold underline">
          Sign in
        </Link>
      </p>
    </form>
  );
}
