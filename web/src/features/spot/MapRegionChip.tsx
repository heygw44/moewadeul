import type { RegionLabel } from '../../map/regionLabel';

// 지도 아래 가운데에 지금 보는 지역을 늘 띄운다. 시트를 접어 목록 머리가 작게 보일 때도 지도에서 어디를 보는지 알 수 있게 하기 위해서다.
// 목록 머리와 같은 값을 쓰고, 이름이 바뀌면 스크린 리더가 읽게 한다. 지도를 움직이는 동안에도 숨기지 않는다.
// 모바일에서는 접힌 시트 바로 위에 두고, 범례는 그 위 줄에 둔다.
export function MapRegionChip({ label }: { label: RegionLabel | null }) {
  let name = '이 지역';
  if (label?.kind === 'named') name = label.name;
  else if (label?.kind === 'wide') name = '여러 시·도';

  return (
    <div className="pointer-events-none absolute inset-x-0 bottom-[calc(28dvh+var(--bottom-nav-h)+0.75rem)] z-10 flex justify-center px-4 lg:bottom-4">
      <p
        aria-live="polite"
        className="inline-flex h-9 min-w-0 max-w-full items-center gap-1.5 rounded-control border border-contour bg-card px-3 text-sm text-ink"
      >
        <span className="shrink-0 text-ink-muted">지금 보는 곳 ·</span>
        <span className="min-w-0 truncate font-semibold">{name}</span>
      </p>
    </div>
  );
}
