import type { ReactNode } from "react";

export default function StepCard({
  numeral,
  title,
  children,
}: {
  numeral: string;
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="lux-card p-7">
      <div className="font-mono text-[44px] font-semibold leading-none text-hoa-gold">{numeral}</div>
      <div className="mt-4 h-px w-10 bg-hoa-gold/60" aria-hidden="true" />
      <h3 className="mt-4 text-lg font-semibold text-hoa-text">{title}</h3>
      <p className="mt-2 text-sm leading-relaxed text-hoa-soft">{children}</p>
    </div>
  );
}
