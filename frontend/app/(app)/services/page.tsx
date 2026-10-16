import type { Metadata } from "next";
import { api } from "@/lib/api";
import type { Service } from "@/lib/types";
import ServiceCard from "@/components/ServiceCard";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Services | House of Aje",
  description: "Browse the full House of Aje nails and lashes menu.",
};

export default async function ServicesPage() {
  const services: Service[] = await api.listServices().catch(() => []);

  const groups = new Map<string, Service[]>();
  for (const service of services) {
    const key = service.categoryName ?? "Other";
    const list = groups.get(key);
    if (list) list.push(service);
    else groups.set(key, [service]);
  }
  const entries = Array.from(groups.entries());

  return (
    <div>
      <h1 className="text-3xl font-semibold tracking-tight">Our services</h1>
      <p className="mt-2 text-hoa-soft">
        The full menu — pick a service to see its open slots and book.
      </p>

      {entries.length === 0 ? (
        <p className="mt-8 text-sm text-hoa-muted">
          No services are available right now. Please check back soon.
        </p>
      ) : (
        entries.map(([category, items]) => (
          <div key={category} className="mt-10">
            <h2 className="text-lg font-semibold text-hoa-blush">{category}</h2>
            <div className="mt-4 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {items.map((service) => (
                <ServiceCard key={service.id} service={service} />
              ))}
            </div>
          </div>
        ))
      )}
    </div>
  );
}
