import type { ReactNode } from 'react';

export function Photo({ src, alt, ratio = 'aspect-[3/2]' }: { src: string; alt: string; ratio?: string }) {
  return (
    <div className={`${ratio} overflow-hidden rounded-media bg-fill`}>
      <img src={src} alt={alt} loading="lazy" className="size-full object-cover mix-blend-multiply" />
    </div>
  );
}

const ROW_CLASS = 'flex items-center gap-3 border-t border-line px-4 py-3';

export function FragmentRow({
  number,
  warn = false,
  name,
  meta,
  distance,
  tag,
}: {
  number: number;
  warn?: boolean;
  name: string;
  meta: string;
  distance: string;
  tag?: ReactNode;
}) {
  return (
    <div className={ROW_CLASS}>
      <span
        className={`grid size-7 shrink-0 place-items-center rounded-full text-xs font-bold text-white ${warn ? 'bg-warning' : 'bg-brand'}`}
      >
        {number}
      </span>
      <div className="min-w-0 flex-1">
        <p className="flex flex-wrap items-center gap-1.5 font-semibold">
          {name} {tag}
        </p>
        <p className="text-[0.9375rem] text-ink-muted">{meta}</p>
      </div>
      <span className="tabular-nums text-base font-extrabold tracking-[-0.02em]">{distance}</span>
    </div>
  );
}

export function Marquee({ items, label }: { items: readonly string[]; label: string }) {
  const itemClass =
    'inline-flex min-h-11 items-center rounded-control bg-fill px-5 text-base font-semibold whitespace-nowrap';
  return (
    <div role="group" aria-label={label} className="overflow-hidden">
      <div
        className={`flex w-max motion-safe:animate-marquee motion-safe:hover:[animation-play-state:paused] motion-reduce:w-auto`}
      >
        <ul className="flex shrink-0 gap-3 pr-3 motion-reduce:flex-wrap">
          {items.map((item) => (
            <li key={item} className={itemClass}>
              {item}
            </li>
          ))}
        </ul>
        <ul aria-hidden="true" className="flex shrink-0 gap-3 pr-3 motion-reduce:hidden">
          {items.map((item) => (
            <li key={item} className={itemClass}>
              {item}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
