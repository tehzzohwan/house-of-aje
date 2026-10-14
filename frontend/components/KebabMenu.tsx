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
        className="rounded-full border border-black/15 px-3 py-1 text-xs hover:border-black dark:border-white/15 dark:hover:border-white"
      >
        ⋯
      </button>
      {open && (
        <div className="absolute right-0 z-20 mt-1 w-44 overflow-hidden rounded-lg border border-black/10 bg-white text-sm shadow-lg dark:border-white/10 dark:bg-zinc-900">
          {actions.map((a) => (
            <button
              key={a.label}
              type="button"
              onClick={() => {
                setOpen(false);
                a.onSelect();
              }}
              className={`block w-full px-3 py-2 text-left hover:bg-black/5 dark:hover:bg-white/10 ${
                a.danger ? "text-red-600 dark:text-red-400" : ""
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
