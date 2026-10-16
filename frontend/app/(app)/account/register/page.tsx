"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { customerApi } from "@/lib/api";
import PasswordField from "@/components/PasswordField";

export default function CustomerRegisterPage() {
  const router = useRouter();
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await customerApi.register({
        firstName,
        lastName,
        username,
        email,
        phone,
        password,
      });
      router.push("/account");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
      setLoading(false);
    }
  }

  const inputClass =
    "mt-2 block w-full rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text placeholder:text-hoa-muted focus:border-hoa-gold focus:outline-none";

  return (
    <form onSubmit={onSubmit} className="mx-auto max-w-sm space-y-4">
      <h1 className="text-3xl font-semibold tracking-tight">Create an account</h1>
      <p className="text-sm text-hoa-muted">
        Registering with the phone you book with links your existing bookings.
      </p>

      <div className="grid gap-4 sm:grid-cols-2">
        <label className="block text-sm font-medium">
          First name
          <input
            required
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
            className={inputClass}
          />
        </label>
        <label className="block text-sm font-medium">
          Last name
          <input
            required
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
            className={inputClass}
          />
        </label>
      </div>

      <label className="block text-sm font-medium">
        Username
        <input
          required
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          className={inputClass}
        />
      </label>

      <label className="block text-sm font-medium">
        Email{" "}
        <span className="font-normal text-hoa-muted">(optional)</span>
        <input
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
          className={inputClass}
        />
      </label>

      <label className="block text-sm font-medium">
        Phone
        <input
          required
          type="tel"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          placeholder="08012345678"
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
        {loading ? "Creating account…" : "Create account"}
      </button>

      <p className="text-center text-sm text-hoa-muted">
        Already have an account?{" "}
        <Link href="/login" className="font-medium text-hoa-gold underline">
          Sign in
        </Link>
      </p>
    </form>
  );
}
