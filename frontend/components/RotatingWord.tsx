"use client";

import { useEffect, useState } from "react";

// Must match the slide transition duration applied to the moving track.
const SLIDE_MS = 500;

interface RotatingWordProps {
  words: string[];
  interval?: number;
  className?: string;
}

/**
 * A single word slot that scrolls vertically through `words` in an infinite,
 * seamless loop: each word pauses briefly, then slides up one line. The list is
 * extended with the first word again at the end so the wrap back to the start
 * is invisible (the track slides onto that duplicate, then snaps back off-screen
 * with the transition disabled).
 */
export default function RotatingWord({
  words,
  interval = 2400,
  className = "",
}: RotatingWordProps) {
  const [index, setIndex] = useState(0);
  const [snapping, setSnapping] = useState(false);

  useEffect(() => {
    if (words.length <= 1) return;
    const id = setInterval(
      () => setIndex((i) => Math.min(i + 1, words.length)),
      interval,
    );
    return () => clearInterval(id);
  }, [words.length, interval]);

  // Once the last word has slid up onto the duplicate first word, snap back to
  // the real first word with no transition so the loop continues upward.
  useEffect(() => {
    if (index < words.length) return;
    const t = setTimeout(() => {
      setSnapping(true);
      setIndex(0);
    }, SLIDE_MS);
    return () => clearTimeout(t);
  }, [index, words.length]);

  // Re-arm the transition in time for the next slide.
  useEffect(() => {
    if (!snapping) return;
    const t = setTimeout(() => setSnapping(false), 0);
    return () => clearTimeout(t);
  }, [snapping]);

  if (words.length === 0) return null;

  const items = [...words, words[0]];
  const widest = words.reduce((a, b) => (a.length >= b.length ? a : b));

  return (
    <span className={`relative inline-block overflow-hidden align-text-bottom ${className}`}>
      {/* Invisible sizer reserves the width of the longest word. */}
      <span aria-hidden="true" className="invisible whitespace-nowrap leading-[1.25em]">
        {widest}
      </span>

      <span
        className={`absolute left-0 top-0 flex flex-col will-change-transform motion-reduce:transition-none ${
          snapping ? "transition-none" : "transition-transform duration-500 ease-out"
        }`}
        style={{ transform: `translateY(-${index * 1.25}em)` }}
      >
        {items.map((word, i) => (
          <span key={`${word}-${i}`} className="whitespace-nowrap leading-[1.25em]">
            {word}
          </span>
        ))}
      </span>
    </span>
  );
}
