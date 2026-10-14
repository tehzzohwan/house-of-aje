"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { PublishedReview, Service } from "@/lib/types";
import { formatDateTime } from "@/lib/format";

export default function ReviewsPage() {
  const [services, setServices] = useState<Service[]>([]);
  const [serviceId, setServiceId] = useState<string>("");
  const [reviews, setReviews] = useState<PublishedReview[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .listServices()
      .then((list) => {
        setServices(list);
        if (list.length > 0) setServiceId(list[0].id);
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load services"));
  }, []);

  useEffect(() => {
    if (!serviceId) return;
    setLoading(true);
    setError(null);
    api
      .reviews(serviceId)
      .then(setReviews)
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load reviews"))
      .finally(() => setLoading(false));
  }, [serviceId]);

  return (
    <div>
      <h1 className="text-3xl font-semibold tracking-tight">Reviews</h1>
      <p className="mt-2 text-zinc-600 dark:text-zinc-400">What our clients are saying.</p>

      <label className="mt-6 block text-sm font-medium">
        Service
        <select
          value={serviceId}
          onChange={(e) => setServiceId(e.target.value)}
          className="mt-2 block w-full max-w-xs rounded-lg border border-black/15 bg-white px-3 py-2 text-sm dark:border-white/15 dark:bg-black"
        >
          {services.map((service) => (
            <option key={service.id} value={service.id}>
              {service.name}
            </option>
          ))}
        </select>
      </label>

      {error && <p className="mt-6 text-sm text-red-600">{error}</p>}

      {loading ? (
        <p className="mt-6 text-zinc-500">Loading…</p>
      ) : reviews.length === 0 ? (
        <p className="mt-6 text-zinc-500">No reviews yet for this service.</p>
      ) : (
        <ul className="mt-6 space-y-4">
          {reviews.map((review) => (
            <li
              key={review.id}
              className="rounded-xl border border-black/10 p-5 dark:border-white/10"
            >
              <div className="flex items-center justify-between gap-4">
                <span className="font-medium">{review.customerName}</span>
                <span className="text-sm text-amber-500">
                  {"★".repeat(review.rating)}
                  <span className="text-zinc-300 dark:text-zinc-600">
                    {"★".repeat(5 - review.rating)}
                  </span>
                </span>
              </div>
              <p className="mt-2 text-sm text-zinc-700 dark:text-zinc-300">{review.comment}</p>
              <p className="mt-3 text-xs text-zinc-500">{formatDateTime(review.createdAt)}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
