"use client";

import { useState } from "react";
import Link from "next/link";
import { formatMoney } from "@/lib/format";
import type { Service } from "@/lib/types";

export interface CategoryLink {
  name: string;
  href: string;
  count: number;
}

/**
 * Hero appointment preview that flips (3D) to reveal the service categories.
 * Front = a sample booking; back = the category list. Click/tap to toggle.
 */
export default function AppointmentFlipCard({
  preview,
  categories,
}: {
  preview: Service;
  categories: CategoryLink[];
}) {
  const [flipped, setFlipped] = useState(false);

  return (
    <div className="flip">
      <div className={`flip-inner ${flipped ? "is-flipped" : ""}`}>
        {/* Front — sample booking */}
        <div className="flip-face lux-card flex flex-col p-7">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="font-mono text-xs uppercase tracking-wider text-hoa-muted">Your booking</p>
              <p className="mt-1 font-semibold text-hoa-text">{preview.name}</p>
            </div>
            <span className="whitespace-nowrap font-mono font-medium text-hoa-gold">
              {formatMoney(preview.price)}
            </span>
          </div>

          <div className="mt-5 space-y-3">
            <p className="font-mono text-xs uppercase tracking-wider text-hoa-muted">Pick a date</p>
            <div className="flex flex-wrap gap-2">
              <span className="rounded-full border border-hoa-border-strong px-3 py-1 text-xs text-hoa-muted">
                Mon 14
              </span>
              <span className="rounded-full border border-hoa-gold bg-hoa-gold/10 px-3 py-1 text-xs text-hoa-gold">
                Tue 15
              </span>
              <span className="rounded-full border border-hoa-border-strong px-3 py-1 text-xs text-hoa-muted">
                Wed 16
              </span>
            </div>

            <p className="font-mono text-xs uppercase tracking-wider text-hoa-muted">Pick a time</p>
            <div className="flex flex-wrap gap-2">
              <span className="rounded-full border border-hoa-border-strong px-3 py-1 font-mono text-xs text-hoa-muted">
                09:00
              </span>
              <span className="rounded-full border border-hoa-gold bg-hoa-gold/10 px-3 py-1 font-mono text-xs text-hoa-gold">
                10:30
              </span>
              <span className="rounded-full border border-hoa-border-strong px-3 py-1 font-mono text-xs text-hoa-muted">
                13:00
              </span>
              <span className="rounded-full border border-hoa-border-strong px-3 py-1 font-mono text-xs text-hoa-muted">
                15:30
              </span>
            </div>
          </div>

          <div className="mt-auto pt-6">
            <div className="cta-gradient px-5 py-2.5 text-center text-sm font-semibold">
              Confirm and pay
            </div>
            <button
              type="button"
              onClick={() => setFlipped(true)}
              className="mt-3 flex w-full items-center justify-center gap-1 text-sm font-medium text-hoa-blush transition-colors hover:text-hoa-gold motion-reduce:transition-none"
            >
              View categories →
            </button>
          </div>
        </div>

        {/* Back — category list */}
        <div className="flip-face flip-face--back lux-card flex flex-col p-7">
          <p className="font-mono text-xs uppercase tracking-wider text-hoa-muted">Pick a category</p>
          <div className="mt-4 space-y-3">
            {categories.map((category) => (
              <Link
                key={category.name}
                href={category.href}
                className="flex items-center justify-between gap-4 rounded-2xl border border-hoa-border px-4 py-3 transition-colors hover:border-hoa-gold motion-reduce:transition-none"
              >
                <span className="font-semibold text-hoa-text">{category.name}</span>
                <span className="text-xs text-hoa-muted">
                  {category.count} service{category.count === 1 ? "" : "s"} →
                </span>
              </Link>
            ))}
          </div>

          <button
            type="button"
            onClick={() => setFlipped(false)}
            className="mt-auto flex w-full items-center justify-center gap-1 pt-6 text-sm font-medium text-hoa-muted transition-colors hover:text-hoa-text motion-reduce:transition-none"
          >
            ← Back to booking
          </button>
        </div>
      </div>
    </div>
  );
}
