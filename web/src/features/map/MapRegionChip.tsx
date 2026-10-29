import type { RegionLabel } from '../../map/regionLabel';

export function MapRegionChip({
  label,
  hiddenOnMobile = false,
}: {
  label: RegionLabel | null;
  hiddenOnMobile?: boolean;
}) {
  let name = '이 지역';
  if (label?.kind === 'named') name = label.name;
  else if (label?.kind === 'wide') name = '여러 시·도';

  return (
    <div
      className={`pointer-events-none absolute inset-x-0 bottom-[calc(var(--sheet-top)+2rem)] z-10 justify-center px-4 lg:bottom-4 lg:flex lg:justify-start ${hiddenOnMobile ? 'hidden' : 'flex'}`}
    >
      <p
        aria-live="polite"
        className="inline-flex h-9 min-w-0 max-w-full items-center gap-1.5 rounded-control bg-surface px-3.5 text-sm text-ink shadow-raise"
      >
        <span className="shrink-0 text-ink-muted">지금 보는 곳 ·</span>
        <span className="min-w-0 truncate font-semibold">{name}</span>
      </p>
    </div>
  );
}
