"use client";

import { useEffect, useRef, useState } from "react";

export interface KebabAction {
  label: string;
  onSelect: () => void;
  danger?: boolean;
}

/** A three-dot "⋯" dropdown for row actions. Closes on outside click. */
export default function KebabMenu({ actions }: { actions: KebabAction[] }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function onDocClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label="More actions"
        className="rounded-full border border-hoa-border-strong px-3 py-1 text-xs text-hoa-muted hover:border-hoa-gold hover:text-hoa-text"
      >
        ⋯
      </button>
      {open && (
        <div className="hoa-surface absolute right-0 z-20 mt-1 w-44 overflow-hidden text-sm shadow-xl">
          {actions.map((a) => (
            <button
              key={a.label}
              type="button"
              onClick={() => {
                setOpen(false);
                a.onSelect();
              }}
              className={`block w-full px-3 py-2 text-left text-hoa-text hover:bg-white/5 ${
                a.danger ? "text-red-400" : ""
              }`}
            >
              {a.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
