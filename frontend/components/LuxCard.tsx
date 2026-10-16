import type { ReactNode } from "react";

/** Gradient-border luxury card. Padding/radius come via className. */
export default function LuxCard({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={`lux-card ${className}`}>{children}</div>;
}
