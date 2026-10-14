"use client";

import { useEffect, useState } from "react";
import { formatCountdown } from "@/lib/format";

/** Live countdown to an appointment start, flipping to "In progress" once begun. */
export default function Countdown({ startAt }: { startAt: string }) {
  const [now, setNow] = useState<number | null>(null);

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  // Render nothing until mounted to avoid a server/client clock mismatch.
  if (now == null) return null;

  const start = new Date(startAt).getTime();
  if (now < start) {
    return <span>Starts in {formatCountdown(start - now)}</span>;
  }
  return <span>In progress</span>;
}
