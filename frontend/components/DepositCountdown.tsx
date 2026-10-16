"use client";

import { useEffect, useState } from "react";
import { formatCountdown } from "@/lib/format";

/** The deposit window in milliseconds; must match `hoa.payment-window` (PT3M). */
const DEPOSIT_WINDOW_MS = 3 * 60 * 1000;

/** Live countdown to the deposit deadline, flipping to "expired" at zero. */
export default function DepositCountdown({
  createdAt,
  payFull = false,
}: {
  createdAt: string;
  payFull?: boolean;
}) {
  const [now, setNow] = useState<number | null>(null);

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  // Render nothing until mounted to avoid a server/client clock mismatch.
  if (now == null) return null;

  const deadline = new Date(createdAt).getTime() + DEPOSIT_WINDOW_MS;
  const remaining = deadline - now;
  if (remaining <= 0) {
    return <span className="text-red-400">Payment window expired</span>;
  }
  return (
    <span>
      {payFull ? "Pay in full" : "Pay deposit"} within {formatCountdown(remaining)}
    </span>
  );
}
