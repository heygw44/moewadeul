import type { RegionLabel } from '../../map/regionLabel';

// 지도 아래 가운데에 지금 보는 지역을 늘 띄운다. 시트를 접어 목록 머리가 작게 보일 때도 지도에서 어디를 보는지 알 수 있게 하기 위해서다.
// 목록 머리와 같은 값을 쓰고, 이름이 바뀌면 스크린 리더가 읽게 한다. 지도를 움직이는 동안에도 숨기지 않는다.
// 모바일에서는 시트 윗변(지도 칸의 CSS 변수 --sheet-top) 위 카카오 로고 줄(19px)을 비운 자리 가운데에, 데스크톱에서는 지도 왼쪽 아래에 두고, 범례는 둘 다 그 위 줄에 둔다.
// hiddenOnMobile이면 폰에서만 숨긴다.
// 범례와 같은 줄에 두면 지도 폭이 좁을 때 둘이 겹친다. 오른쪽 아래는 지도 저작권 표시 자리라 비워 둔다.
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
