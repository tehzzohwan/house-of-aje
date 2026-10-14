export const BUSINESS_TIME_ZONE = "Africa/Lagos";

const ngn = new Intl.NumberFormat("en-NG", {
  style: "currency",
  currency: "NGN",
  minimumFractionDigits: 2,
});

export function formatMoney(value: number): string {
  return ngn.format(value);
}

/** Abbreviation shown next to times so customers know the business timezone. */
export const TIMEZONE_TAG = "WAT";

export function formatDateTime(iso: string): string {
  const when = new Date(iso).toLocaleString("en-NG", {
    timeZone: BUSINESS_TIME_ZONE,
    dateStyle: "medium",
    timeStyle: "short",
  });
  return `${when} ${TIMEZONE_TAG}`;
}

/** Local wall-clock time for a slot instant, in the business timezone. */
export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString("en-NG", {
    timeZone: BUSINESS_TIME_ZONE,
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** "09:00 – 10:00 WAT" — a tagged wall-clock range in the business timezone. */
export function formatTimeRange(startIso: string, endIso: string): string {
  return `${formatTime(startIso)} – ${formatTime(endIso)} ${TIMEZONE_TAG}`;
}

/** Today's date (YYYY-MM-DD) in the business timezone. */
export function businessDateString(date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: BUSINESS_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);
  const get = (type: string): string => parts.find((p) => p.type === type)?.value ?? "";
  return `${get("year")}-${get("month")}-${get("day")}`;
}

/** Human duration like "1 hr 30 min" / "45 min" from a minute count. */
export function formatDuration(minutes: number): string {
  const total = Math.max(0, Math.round(minutes));
  const h = Math.floor(total / 60);
  const m = total % 60;
  if (h === 0) return `${m} min`;
  if (m === 0) return `${h} hr${h === 1 ? "" : "s"}`;
  return `${h} hr${h === 1 ? "" : "s"} ${m} min`;
}

/** Compact countdown like "2d 4h", "3h 12m", "4m 05s", or "30s". */
export function formatCountdown(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const d = Math.floor(total / 86400);
  const h = Math.floor((total % 86400) / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${String(s).padStart(2, "0")}s`;
  return `${s}s`;
}
