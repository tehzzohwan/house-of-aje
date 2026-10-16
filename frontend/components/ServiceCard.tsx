import Link from "next/link";
import type { Service } from "@/lib/types";
import { formatDuration, formatMoney } from "@/lib/format";
import Sparkle from "./Sparkle";

export default function ServiceCard({ service }: { service: Service }) {
  return (
    <div className="lux-card flex h-full flex-col justify-between p-6">
      <div>
        <div className="flex items-start justify-between gap-4">
          <h3 className="flex items-center gap-2 text-xl font-semibold text-hoa-text">
            <Sparkle className="h-4 w-4 shrink-0 text-hoa-gold" />
            {service.name}
          </h3>
          <span className="whitespace-nowrap font-mono font-medium text-hoa-gold">
            {formatMoney(service.price)}
          </span>
        </div>
        <p className="mt-2 text-sm text-hoa-soft">{service.description}</p>
      </div>

      <div className="mt-5 flex items-center justify-between gap-3">
        <span className="rounded-full border border-hoa-border px-3 py-1 font-mono text-xs uppercase tracking-wider text-hoa-muted">
          {formatDuration(service.durationMinutes).toUpperCase()}
        </span>
        <Link
          href={`/book?service=${service.id}`}
          className="text-sm font-medium text-hoa-blush transition-colors hover:text-hoa-gold motion-reduce:transition-none"
        >
          Book this →
        </Link>
      </div>
    </div>
  );
}
