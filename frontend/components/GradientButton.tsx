import type { ReactNode } from "react";
import Link from "next/link";

interface Props {
  href?: string;
  onClick?: () => void;
  children: ReactNode;
  className?: string;
}

/** Pill gradient CTA. Renders a link when `href` is set, else a button. */
export default function GradientButton({ href, onClick, children, className = "" }: Props) {
  const cls =
    "cta-gradient inline-flex items-center justify-center px-7 py-3 text-sm font-semibold transition-opacity hover:opacity-90 motion-reduce:transition-none " +
    className;

  if (href) {
    return (
      <Link href={href} className={cls}>
        {children}
      </Link>
    );
  }
  return (
    <button type="button" onClick={onClick} className={cls}>
      {children}
    </button>
  );
}
