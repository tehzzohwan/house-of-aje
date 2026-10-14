"use client";

import { usePathname, useRouter } from "next/navigation";

/** Returns to the previous page in the browser history, falling back to home. */
export default function BackButton() {
  const router = useRouter();
  const pathname = usePathname();

  // Nothing to return to on the home page.
  if (pathname === "/") return null;

  function goBack() {
    if (typeof window !== "undefined" && window.history.length > 1) {
      router.back();
    } else {
      router.push("/");
    }
  }

  return (
    <button
      type="button"
      onClick={goBack}
      aria-label="Return to previous page"
      className="mb-6 inline-block text-sm text-zinc-600 hover:text-black dark:text-zinc-400 dark:hover:text-white"
    >
      ← Back
    </button>
  );
}
