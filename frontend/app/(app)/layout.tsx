import type { ReactNode } from "react";
import Link from "next/link";
import Nav from "@/components/Nav";
import BackButton from "@/components/BackButton";

/** Shared chrome for the app pages (everything except the marketing home page). */
export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <header className="sticky top-0 z-40 border-b border-hoa-border bg-hoa-bg/80 backdrop-blur">
        <div className="mx-auto flex w-full max-w-5xl items-center justify-between px-4 py-4">
          <Link href="/" className="text-lg font-semibold tracking-tight">
            House of <span className="text-hoa-gold">Aje</span>
          </Link>
          <Nav />
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
        <BackButton />
        {children}
      </main>
    </>
  );
}
