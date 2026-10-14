"use client";

import { useState } from "react";

/** A password input with a show/hide toggle, used wherever we collect a password. */
export default function PasswordField({
  value,
  onChange,
  autoComplete,
  required,
}: {
  value: string;
  onChange: (value: string) => void;
  autoComplete?: string;
  required?: boolean;
}) {
  const [show, setShow] = useState(false);

  return (
    <div className="relative mt-2">
      <input
        required={required}
        type={show ? "text" : "password"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        className="block w-full rounded-lg border border-black/15 bg-white px-3 py-2 pr-14 text-sm dark:border-white/15 dark:bg-black"
      />
      <button
        type="button"
        onClick={() => setShow((v) => !v)}
        aria-label={show ? "Hide password" : "Show password"}
        className="absolute inset-y-0 right-0 flex items-center px-3 text-xs font-medium text-zinc-500 hover:text-black dark:hover:text-white"
      >
        {show ? "Hide" : "Show"}
      </button>
    </div>
  );
}
