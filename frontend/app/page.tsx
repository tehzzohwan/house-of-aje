import type { Metadata } from "next";
import Link from "next/link";
import { api } from "@/lib/api";
import type { ReviewSummary, Service } from "@/lib/types";
import Sparkle from "@/components/Sparkle";
import LuxCard from "@/components/LuxCard";
import GradientButton from "@/components/GradientButton";
import ServiceCard from "@/components/ServiceCard";
import StepCard from "@/components/StepCard";
import ReviewCard from "@/components/ReviewCard";
import AppointmentFlipCard from "@/components/AppointmentFlipCard";
import RotatingWord from "@/components/RotatingWord";

export const dynamic = "force-dynamic";

const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000";

const FALLBACK_SERVICES: Service[] = [
  {
    id: "fallback-acrylic-plum",
    name: "Acrylic Plum",
    slug: "acrylic-plum",
    description: "Long-lasting acrylic extensions with a plum finish.",
    durationMinutes: 120,
    price: 40000,
    categoryId: "nails",
    categoryName: "Nails",
  },
  {
    id: "fallback-manicure",
    name: "Manicure",
    slug: "manicure",
    description: "A classic manicure to keep your nails neat and healthy.",
    durationMinutes: 90,
    price: 20000,
    categoryId: "nails",
    categoryName: "Nails",
  },
  {
    id: "fallback-nail-remover",
    name: "Nail Remover",
    slug: "nail-remover",
    description: "Gentle removal of acrylics or gel.",
    durationMinutes: 10,
    price: 5000,
    categoryId: "nails",
    categoryName: "Nails",
  },
  {
    id: "fallback-lash-fix",
    name: "Lash Fix",
    slug: "lash-fix",
    description: "Freshen up your lash extensions.",
    durationMinutes: 70,
    price: 20000,
    categoryId: "lashes",
    categoryName: "Lashes",
  },
  {
    id: "fallback-max-volume",
    name: "Max Volume",
    slug: "max-volume",
    description: "Full, dramatic volume lashes.",
    durationMinutes: 50,
    price: 30000,
    categoryId: "lashes",
    categoryName: "Lashes",
  },
];

const FALLBACK_SUMMARY: ReviewSummary = { reviews: [], averageRating: 0, count: 0 };

export async function generateMetadata(): Promise<Metadata> {
  const title = "House of Aje | Nails & Lashes Appointments";
  const description =
    "Book nails and lashes appointments in minutes. Choose a specialist, pick an open slot, and lock it with a 40% deposit.";
  return {
    title,
    description,
    alternates: { canonical: SITE_URL },
    openGraph: { title, description, url: SITE_URL, siteName: "House of Aje", type: "website" },
    twitter: { card: "summary_large_image", title, description },
  };
}

export default async function Home() {
  // Server-side data with graceful fallback when the API is unreachable or empty.
  const fetchedServices = await api.listServices().catch(() => null);
  const services: Service[] =
    fetchedServices && fetchedServices.length > 0 ? fetchedServices : FALLBACK_SERVICES;
  const summary = await api.reviewsSummary(3).catch(() => FALLBACK_SUMMARY);

  // Group services by category so the catalogue reads as sections.
  const groups = new Map<string, Service[]>();
  for (const service of services) {
    const key = service.categoryName ?? "Other";
    const list = groups.get(key);
    if (list) list.push(service);
    else groups.set(key, [service]);
  }

  const preview = services[0] ?? FALLBACK_SERVICES[0];

  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "BeautySalon",
    name: "House of Aje",
    url: SITE_URL,
    description: "Nails and lashes appointments, booked in minutes.",
    openingHoursSpecification: [
      {
        "@type": "OpeningHoursSpecification",
        dayOfWeek: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"],
        opens: "09:00",
        closes: "21:00",
      },
      {
        "@type": "OpeningHoursSpecification",
        dayOfWeek: "Saturday",
        opens: "08:00",
        closes: "22:00",
      },
      {
        "@type": "OpeningHoursSpecification",
        dayOfWeek: "Sunday",
        opens: "12:00",
        closes: "19:00",
      },
    ],
    ...(summary.count > 0
      ? {
          aggregateRating: {
            "@type": "AggregateRating",
            ratingValue: summary.averageRating,
            reviewCount: summary.count,
          },
        }
      : {}),
    hasOfferCatalog: {
      "@type": "OfferCatalog",
      name: "Services",
      itemListElement: services.map((service) => ({
        "@type": "Offer",
        itemOffered: { "@type": "Service", name: service.name },
        price: String(service.price),
        priceCurrency: "NGN",
      })),
    },
  };

  const entries = Array.from(groups.entries());

  // Category links for the hero flip card (front = sample booking, back = categories).
  const categories = entries.map(([name, items]) => ({
    name,
    count: items.length,
    href: `/book?service=${items[0].id}`,
  }));

  // The rotating slot in the hero headline cycles through "appointment" and the
  // category names, so the base word is part of the loop too.
  const heroWords = ["appointment", ...entries.map(([name]) => name)];

  return (
    <div className="min-h-screen bg-hoa-bg text-hoa-text">
      {/* Nav */}
      <header className="border-b border-hoa-border">
        <nav
          aria-label="Primary"
          className="mx-auto flex w-full max-w-[1160px] flex-wrap items-center justify-between gap-x-6 gap-y-2 px-6 py-5"
        >
          <Link href="/" className="text-lg font-semibold tracking-tight">
            House of <span className="text-hoa-gold">Aje</span>
          </Link>
          <div className="flex flex-wrap items-center gap-6 text-sm">
            <Link href="/services" className="text-hoa-muted transition-colors hover:text-hoa-text">
              Services
            </Link>
            <Link href="/book" className="text-hoa-muted transition-colors hover:text-hoa-text">
              Book
            </Link>
            <Link href="/reviews" className="text-hoa-muted transition-colors hover:text-hoa-text">
              Reviews
            </Link>
            <Link href="/login" className="text-hoa-muted transition-colors hover:text-hoa-text">
              Sign in
            </Link>
          </div>
        </nav>
      </header>

      <main>
        {/* Hero */}
        <section className="mx-auto grid w-full max-w-[1160px] items-center gap-10 px-6 py-16 lg:grid-cols-2 lg:py-24">
          <div>
            <p className="inline-flex items-center gap-2 rounded-full border border-hoa-border-strong px-4 py-1.5 font-mono text-xs uppercase tracking-wider text-hoa-blush">
              Nails · Lashes · Styling
            </p>
            <h1 className="mt-6 text-4xl font-semibold leading-tight tracking-tight sm:text-5xl">
              <span className="sr-only">Your next appointment, booked in minutes.</span>
              <span aria-hidden="true">Your next <RotatingWord words={heroWords} className="font-mono text-hoa-gold" /> booked in minutes.</span>
            </h1>
            <p className="mt-5 max-w-md text-base text-hoa-soft">
              Choose a specialist, pick a time that is actually open, and lock it in with a 40%
              deposit. No phone calls, no waiting.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <GradientButton href="/book">Book now</GradientButton>
              <a
                href="#services"
                className="inline-flex items-center justify-center rounded-full border border-hoa-border-strong px-7 py-3 text-sm font-medium text-hoa-text transition-colors hover:border-hoa-gold motion-reduce:transition-none"
              >
                View services
              </a>
            </div>
          </div>

          {/* Appointment preview that flips into the category list */}
          <AppointmentFlipCard preview={preview} categories={categories} />
        </section>

        {/* Services */}
        <section id="services" className="mx-auto w-full max-w-[1160px] px-6 py-16">
          <h2 className="text-3xl font-semibold tracking-tight">Our services</h2>
          <p className="mt-2 text-hoa-soft">
            Book a beauty or styling appointment with our specialists.
          </p>

          {entries.map(([category, items], index) => {
            const isLast = index === entries.length - 1;
            return (
              <div key={category} className="mt-10">
                <h3 className="text-lg font-semibold text-hoa-blush">{category}</h3>
                <div className="mt-4 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
                  {items.slice(0, 3).map((service) => (
                    <ServiceCard key={service.id} service={service} />
                  ))}
                  {isLast && (
                    <Link
                      href="/services"
                      className="flex min-h-[160px] flex-col items-center justify-center gap-2 rounded-[26px] border border-dashed border-hoa-border-strong p-6 text-center transition-colors hover:border-hoa-gold motion-reduce:transition-none"
                    >
                      <span className="font-semibold text-hoa-text">More in the full menu</span>
                      <span className="text-sm text-hoa-blush">See every service →</span>
                    </Link>
                  )}
                </div>
              </div>
            );
          })}
        </section>

        {/* How it works */}
        <section className="mx-auto w-full max-w-[1160px] px-6 py-16">
          <h2 className="text-3xl font-semibold tracking-tight">Book in three steps</h2>
          <div className="mt-8 grid gap-5 md:grid-cols-3">
            <StepCard numeral="I" title="Choose your service">
              Pick the treatment you want from our nails and lashes menu.
            </StepCard>
            <StepCard numeral="II" title="Pick a specialist and time">
              Only the slots that are genuinely open for that person are shown, so you never
              double-book.
            </StepCard>
            <StepCard numeral="III" title="Pay a 40% deposit">
              Lock your slot by bank transfer. Pay the rest from the app whenever you&apos;re ready.
            </StepCard>
          </div>
        </section>

        {/* Why House of Aje */}
        <section className="mx-auto w-full max-w-[1160px] px-6 py-16">
          <div className="grid gap-10 lg:grid-cols-[1fr_2fr]">
            <h2 className="text-3xl font-semibold tracking-tight">Why House of Aje</h2>
            <div className="grid gap-8 sm:grid-cols-3">
              <div>
                <h3 className="font-semibold text-hoa-text">Book faster</h3>
                <p className="mt-2 text-sm text-hoa-soft">
                  Reserve your appointment in minutes without waiting on a reply.
                </p>
              </div>
              <div>
                <h3 className="font-semibold text-hoa-text">Secure payment</h3>
                <p className="mt-2 text-sm text-hoa-soft">
                  A 40% deposit holds your slot so it can&apos;t be given away.
                </p>
              </div>
              <div>
                <h3 className="font-semibold text-hoa-text">Real reviews</h3>
                <p className="mt-2 text-sm text-hoa-soft">
                  Only clients who completed and fully paid for a visit can leave a review.
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* About us */}
        <section className="mx-auto w-full max-w-[1160px] px-6 py-16">
          <div className="grid gap-10 lg:grid-cols-2">
            <div>
              <h2 className="text-3xl font-semibold tracking-tight">About us</h2>
              <p className="mt-4 text-hoa-soft">
                [ABOUT TEXT — a short introduction to House of Aje will go here.]
              </p>
            </div>
            <div className="space-y-6">
              <div
                role="img"
                aria-label="Photo placeholder"
                className="flex aspect-[4/3] items-center justify-center rounded-[26px] border border-dashed border-hoa-border-strong bg-hoa-border/40 text-sm text-hoa-muted"
              >
                [Photo]
              </div>
              <LuxCard className="p-6">
                <h3 className="font-semibold text-hoa-text">Opening hours</h3>
                <dl className="mt-4 space-y-2 text-sm">
                  <div className="flex justify-between">
                    <dt className="text-hoa-muted">Mon – Fri</dt>
                    <dd className="text-hoa-text">9am – 9pm</dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-hoa-muted">Sat</dt>
                    <dd className="text-hoa-text">8am – 10pm</dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-hoa-muted">Sun</dt>
                    <dd className="text-hoa-text">12pm – 7pm</dd>
                  </div>
                </dl>
              </LuxCard>
            </div>
          </div>
        </section>

        {/* Reviews */}
        <section className="mx-auto w-full max-w-[1160px] px-6 py-16">
          <div>
            <h2 className="text-3xl font-semibold tracking-tight">What our clients say</h2>
            {summary.count > 0 && (
              <p className="mt-2 text-sm text-hoa-muted">
                {summary.averageRating.toFixed(1)} ★ average from {summary.count}{" "}
                review{summary.count === 1 ? "" : "s"}
              </p>
            )}
          </div>

          {summary.reviews.length > 0 ? (
            <div className="mt-8 grid gap-5 md:grid-cols-3">
              {summary.reviews.slice(0, 3).map((review) => (
                <ReviewCard
                  key={review.id}
                  name={review.customerName}
                  rating={review.rating}
                  quote={review.comment}
                />
              ))}
            </div>
          ) : (
            <LuxCard className="mt-8 p-8 text-center">
              <p className="text-hoa-muted">
                [PLACEHOLDER] Client reviews will appear here once the first completed visits are
                reviewed.
              </p>
            </LuxCard>
          )}
        </section>

        {/* Closing CTA */}
        <section className="mx-auto w-full max-w-[1160px] px-6 py-16">
          <LuxCard className="relative overflow-hidden p-10 text-center sm:p-16">
            <div
              aria-hidden="true"
              className="pointer-events-none absolute inset-0"
              style={{
                background:
                  "radial-gradient(60% 60% at 50% 0%, rgba(235,185,198,0.18), transparent 70%)",
              }}
            />
            <Sparkle className="relative mx-auto h-6 w-6 text-hoa-gold" />
            <h2 className="relative mt-4 text-3xl font-semibold tracking-tight sm:text-4xl">
              Ready for your next set?
            </h2>
            <p className="relative mx-auto mt-4 max-w-md text-hoa-soft">
              Book your nails or lashes appointment now and lock it in with a 40% deposit.
            </p>
            <div className="relative mt-8">
              <GradientButton href="/book">Book now</GradientButton>
            </div>
          </LuxCard>
        </section>
      </main>

      {/* Footer */}
      <footer className="border-t border-hoa-border">
        <div className="mx-auto grid w-full max-w-[1160px] gap-10 px-6 py-14 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <p className="text-lg font-semibold tracking-tight">
              House of <span className="text-hoa-gold">Aje</span>
            </p>
            <p className="mt-3 text-sm text-hoa-muted">[Address line 1]</p>
            <p className="text-sm text-hoa-muted">[Address line 2]</p>
            <p className="mt-3 text-sm text-hoa-muted">
              Mon–Fri 9am–9pm · Sat 8am–10pm · Sun 12pm–7pm
            </p>
          </div>
          <div>
            <h3 className="font-mono text-sm font-semibold uppercase tracking-wider text-hoa-blush">
              Explore
            </h3>
            <ul className="mt-3 space-y-2 text-sm">
              <li>
                <Link href="/services" className="text-hoa-muted transition-colors hover:text-hoa-text">
                  Services
                </Link>
              </li>
              <li>
                <Link href="/book" className="text-hoa-muted transition-colors hover:text-hoa-text">
                  Book
                </Link>
              </li>
              <li>
                <Link
                  href="/reviews"
                  className="text-hoa-muted transition-colors hover:text-hoa-text"
                >
                  Reviews
                </Link>
              </li>
            </ul>
          </div>
          <div>
            <h3 className="font-mono text-sm font-semibold uppercase tracking-wider text-hoa-blush">
              Contact
            </h3>
            <ul className="mt-3 space-y-2 text-sm text-hoa-muted">
              <li>[Phone]</li>
              <li>[Email]</li>
              <li>[Instagram / socials]</li>
            </ul>
          </div>
          <div>
            <h3 className="font-mono text-sm font-semibold uppercase tracking-wider text-hoa-blush">
              Legal
            </h3>
            <ul className="mt-3 space-y-2 text-sm text-hoa-muted">
              <li>[Privacy policy]</li>
              <li>[Terms]</li>
            </ul>
          </div>
        </div>
        <div className="border-t border-hoa-border">
          <div className="mx-auto w-full max-w-[1160px] px-6 py-6 text-sm text-hoa-muted">
            © 2026 House of Aje
          </div>
        </div>
      </footer>

      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
    </div>
  );
}
