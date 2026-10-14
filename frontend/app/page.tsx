import Link from "next/link";
import { api } from "@/lib/api";
import { formatDuration, formatMoney } from "@/lib/format";
import type { Service } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function Home() {
  const services = await api.listServices();

  // Group services by category so the catalogue reads as sections.
  const groups = new Map<string, Service[]>();
  for (const service of services) {
    const key = service.categoryName ?? "Other";
    const list = groups.get(key);
    if (list) list.push(service);
    else groups.set(key, [service]);
  }

  return (
    <div>
      <h1 className="text-3xl font-semibold tracking-tight">Our services</h1>
      <p className="mt-2 text-zinc-600 dark:text-zinc-400">
        Book a beauty or styling appointment with our specialists.
      </p>

      {Array.from(groups.entries()).map(([category, items]) => (
        <section key={category} className="mt-10">
          <h2 className="text-xl font-semibold tracking-tight">{category}</h2>
          <ul className="mt-4 grid gap-4 sm:grid-cols-2">
            {items.map((service) => (
              <li key={service.id}>
                <Link
                  href={`/book?service=${service.id}`}
                  className="block h-full rounded-xl border border-black/10 p-5 transition-colors hover:border-black dark:border-white/10 dark:hover:border-white"
                >
                  <div className="flex items-start justify-between gap-4">
                    <h3 className="font-semibold">{service.name}</h3>
                    <span className="whitespace-nowrap font-medium">{formatMoney(service.price)}</span>
                  </div>
                  <p className="mt-2 text-sm text-zinc-600 dark:text-zinc-400">
                    {service.description}
                  </p>
                  <p className="mt-3 text-xs text-zinc-500">{formatDuration(service.durationMinutes)}</p>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
