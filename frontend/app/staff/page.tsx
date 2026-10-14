"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { authApi, staffApi } from "@/lib/api";
import type { StaffProfile } from "@/lib/types";
import { formatDateTime, formatDuration, formatMoney } from "@/lib/format";

const DAYS = ["", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

const statusLabel: Record<string, string> = {
  PENDING_PAYMENT: "Awaiting deposit",
  CONFIRMED: "Confirmed",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
  NO_SHOW: "No-show",
  EXPIRED: "Expired",
};

function time(t: string): string {
  return t.slice(0, 5);
}

export default function StaffPage() {
  const [authed, setAuthed] = useState(false);
  const [ready, setReady] = useState(false);
  const [profile, setProfile] = useState<StaffProfile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [clocking, setClocking] = useState(false);

  useEffect(() => {
    authApi
      .me()
      .then(({ role }) => {
        setAuthed(role === "STAFF");
        setReady(true);
      })
      .catch(() => {
        setAuthed(false);
        setReady(true);
      });
  }, []);

  const loadProfile = useCallback(async () => {
    const p = await staffApi.me();
    setProfile(p);
  }, []);

  useEffect(() => {
    if (!authed) {
      setLoading(false);
      return;
    }
    loadProfile()
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load your profile"))
      .finally(() => setLoading(false));
  }, [authed, loadProfile]);

  async function clock(action: "in" | "out") {
    setClocking(true);
    setError(null);
    try {
      if (action === "in") {
        await staffApi.clockIn();
      } else {
        await staffApi.clockOut();
      }
      await loadProfile();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Clock action failed");
    } finally {
      setClocking(false);
    }
  }

  if (!ready) {
    return <p className="text-sm text-zinc-500">Loading…</p>;
  }

  if (!authed) {
    return (
      <div className="mx-auto max-w-sm space-y-4 text-center">
        <h1 className="text-3xl font-semibold tracking-tight">Staff portal</h1>
        <p className="text-zinc-500">Sign in to view your profile and appointments.</p>
        <Link
          href="/login"
          className="inline-block w-full rounded-full bg-black px-6 py-3 text-sm font-medium text-white dark:bg-white dark:text-black"
        >
          Sign in
        </Link>
        <Link href="/staff/register" className="inline-block text-sm text-zinc-500 underline">
          Register
        </Link>
      </div>
    );
  }

  if (loading) {
    return <p className="text-sm text-zinc-500">Loading…</p>;
  }

  if (error) {
    return <p className="text-sm text-red-600">{error}</p>;
  }

  if (!profile) {
    return null;
  }

  return (
    <div>
      <h1 className="text-3xl font-semibold tracking-tight">{profile.name}</h1>

      <section className="mt-6 flex items-center justify-between gap-4 rounded-xl border border-black/10 p-5 dark:border-white/10">
        <div>
          <p className="text-sm text-zinc-500">Today</p>
          <p className="mt-1 font-medium">
            {profile.clockedIn ? "Clocked in" : "Not clocked in"}
          </p>
          <p className="text-sm text-zinc-500">
            Time worked: {formatDuration(profile.workedMinutesToday)}
          </p>
        </div>
        <button
          onClick={() => clock(profile.clockedIn ? "out" : "in")}
          disabled={clocking}
          className={`rounded-full px-6 py-3 text-sm font-medium text-white transition-colors disabled:opacity-40 dark:text-black ${
            profile.clockedIn
              ? "bg-red-600 hover:bg-red-700 dark:bg-red-500"
              : "bg-black hover:bg-zinc-800 dark:bg-white dark:hover:bg-zinc-200"
          }`}
        >
          {clocking ? "Saving…" : profile.clockedIn ? "Clock out" : "Clock in"}
        </button>
      </section>

      <dl className="mt-6 rounded-xl border border-black/10 p-5 text-sm dark:border-white/10">
        {profile.email && (
          <div className="flex justify-between">
            <dt className="text-zinc-500">Email</dt>
            <dd>{profile.email}</dd>
          </div>
        )}
        {profile.phone && (
          <div className="flex justify-between">
            <dt className="text-zinc-500">Phone</dt>
            <dd>{profile.phone}</dd>
          </div>
        )}
        {profile.bio && (
          <div className="mt-3">
            <dt className="text-zinc-500">Bio</dt>
            <dd className="mt-1">{profile.bio}</dd>
          </div>
        )}
      </dl>

      <section className="mt-8">
        <h2 className="text-sm font-medium">Services</h2>
        {profile.services.length === 0 ? (
          <p className="mt-2 text-sm text-zinc-500">No services assigned.</p>
        ) : (
          <div className="mt-2 flex flex-wrap gap-2">
            {profile.services.map((name) => (
              <span
                key={name}
                className="rounded-full bg-black/5 px-3 py-1 text-sm dark:bg-white/10"
              >
                {name}
              </span>
            ))}
          </div>
        )}
      </section>

      <section className="mt-8">
        <h2 className="text-sm font-medium">Working hours</h2>
        {profile.hours.length === 0 ? (
          <p className="mt-2 text-sm text-zinc-500">No hours set.</p>
        ) : (
          <ul className="mt-2 grid gap-2 sm:grid-cols-2">
            {profile.hours.map((h) => (
              <li
                key={h.dayOfWeek}
                className="flex justify-between rounded-xl border border-black/10 px-4 py-3 text-sm dark:border-white/10"
              >
                <span className="font-medium">{DAYS[h.dayOfWeek] ?? h.dayOfWeek}</span>
                <span className="text-zinc-500">
                  {time(h.startTime)} – {time(h.endTime)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="mt-8">
        <h2 className="text-sm font-medium">Upcoming &amp; past appointments</h2>
        {profile.bookings.length === 0 ? (
          <p className="mt-2 text-sm text-zinc-500">No bookings yet.</p>
        ) : (
          <ul className="mt-3 space-y-3">
            {profile.bookings.map((b) => (
              <li
                key={b.id}
                className="rounded-xl border border-black/10 p-4 dark:border-white/10"
              >
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="font-medium">{b.serviceName}</p>
                    <p className="text-sm text-zinc-500">{formatDateTime(b.startAt)}</p>
                    <p className="mt-1 font-mono text-xs text-zinc-400">{b.reference}</p>
                  </div>
                  <div className="text-right">
                    <span className="inline-block rounded-full bg-black/5 px-2.5 py-1 text-xs font-medium dark:bg-white/10">
                      {statusLabel[b.status] ?? b.status}
                    </span>
                    <p className="mt-2 text-sm">{formatMoney(b.price)}</p>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
