export default function ReviewCard({
  name,
  rating,
  quote,
}: {
  name: string;
  rating: number;
  quote: string;
}) {
  const filled = Math.max(0, Math.min(5, Math.round(rating)));
  return (
    <div className="lux-card flex h-full flex-col p-7">
      <div aria-hidden="true" className="text-5xl leading-none text-hoa-blush">
        &ldquo;
      </div>
      <p className="mt-3 text-base leading-relaxed text-hoa-card">{quote}</p>

      <div className="mt-auto pt-6">
        <div
          role="img"
          aria-label={`Rated ${rating} out of 5`}
          className="text-lg tracking-wide text-hoa-gold"
        >
          {"★★★★★".slice(0, filled)}
          <span className="text-hoa-gold/25">{"★★★★★".slice(0, 5 - filled)}</span>
        </div>
        <p className="mt-2 font-mono text-xs font-semibold uppercase tracking-wider text-hoa-blush">{name}</p>
      </div>
    </div>
  );
}
