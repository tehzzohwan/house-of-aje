/** Small gold sparkle accent, decorative only. */
export default function Sparkle({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" className={className}>
      <path d="M12 2c.4 4.9 2.4 6.9 7.3 7.3-4.9.4-6.9 2.4-7.3 7.3-.4-4.9-2.4-6.9-7.3-7.3 4.9-.4 6.9-2.4 7.3-7.3Z" />
    </svg>
  );
}
