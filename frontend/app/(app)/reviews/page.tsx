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
      <p className="mt-2 text-hoa-muted">What our clients are saying.</p>

      <label className="mt-6 block text-sm font-medium">
        Service
        <select
          value={serviceId}
          onChange={(e) => setServiceId(e.target.value)}
          className="mt-2 block w-full max-w-xs rounded-lg border border-hoa-border-strong bg-white/5 px-3 py-2 text-sm text-hoa-text focus:border-hoa-gold focus:outline-none"
        >
          {services.map((service) => (
            <option key={service.id} value={service.id}>
              {service.name}
            </option>
          ))}
        </select>
      </label>

      {error && <p className="mt-6 text-sm text-red-400">{error}</p>}

      {loading ? (
        <p className="mt-6 text-hoa-muted">Loading…</p>
      ) : reviews.length === 0 ? (
        <p className="mt-6 text-hoa-muted">No reviews yet for this service.</p>
      ) : (
        <ul className="mt-6 space-y-4">
          {reviews.map((review) => (
            <li
              key={review.id}
              className="hoa-surface p-5"
            >
              <div className="flex items-center justify-between gap-4">
                <span className="font-medium">{review.customerName}</span>
                <span className="text-sm text-hoa-gold">
                  {"★".repeat(review.rating)}
                  <span className="text-hoa-border-strong">
                    {"★".repeat(5 - review.rating)}
                  </span>
                </span>
              </div>
              <p className="mt-2 text-sm text-hoa-text">{review.comment}</p>
              <p className="mt-3 text-xs text-hoa-muted">{formatDateTime(review.createdAt)}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
