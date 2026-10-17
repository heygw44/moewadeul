import { useId, useState } from 'react';
import { Icon } from '../../components/icons';
import { readStored, writeStored } from '../../lib/storage';

const OPEN_KEY = 'moewadeul.mapLegendOpen';
const DESKTOP_QUERY = '(min-width: 64rem)';
// 핀은 번호 원이라 유형 대신 색과 고리로 나눈다. 야영장과 휴양림은 같은 숲색이고, 목록 행에 유형을 글로 적는다.
const LEGEND_ITEMS: ReadonlyArray<{ key: string; label: string; swatch: string }> = [
  { key: 'public', label: '야영장·휴양림', swatch: 'bg-brand' },
  { key: 'bakji', label: '박지', swatch: 'bg-bakji' },
  { key: 'warning', label: '공원 경계 경고', swatch: 'bg-surface ring-[2.5px] ring-inset ring-warning-line' },
];

// 사용자가 한 번 접거나 펼치면 그 상태를 따른다. 처음에는 넓은 화면에서 펼치고, 폰에서는 지도를 덜 가리게 접어 둔다.
function initialOpen(): boolean {
  const stored = readStored(OPEN_KEY);
  if (stored !== null) return stored === '1';
  return window.matchMedia(DESKTOP_QUERY).matches;
}

// 지도 왼쪽 아래의 핀 범례다. 지도 핀과 같은 색의 작은 원을 그린다.
// 모바일에서는 시트 위 지역 칩(높이 36px)의 한 줄 위에 둔다. 펼치면 위로만 커져서 칩을 가리지 않는다.
// 시트 윗변 높이는 지도 칸이 정하는 CSS 변수 --sheet-top으로 받는다. hiddenOnMobile이면 폰에서만 숨긴다.
export function MapLegend({ hiddenOnMobile = false }: { hiddenOnMobile?: boolean }) {
  const [open, setOpen] = useState(initialOpen);
  const listId = useId();

  function toggle() {
    const next = !open;
    writeStored(OPEN_KEY, next ? '1' : '0');
    setOpen(next);
  }

  // 둥근 모서리 밖으로 버튼 hover 바탕이 삐져나가지 않게 상자에서 자른다. 그래서 포커스 링은 버튼 안쪽에 그린다.
  return (
    <div
      className={`pointer-events-auto absolute bottom-[calc(var(--sheet-top)+4.75rem)] left-4 z-10 flex-col overflow-hidden rounded-sheet border border-transparent bg-surface text-ink shadow-raise lg:bottom-[3.75rem] lg:flex lg:flex-row lg:items-center ${hiddenOnMobile ? 'hidden' : 'flex'}`}
    >
      <button
        type="button"
        onClick={toggle}
        aria-expanded={open}
        aria-controls={listId}
        className="inline-flex min-h-11 items-center gap-1.5 px-3 text-sm font-semibold hover:bg-fill focus-visible:-outline-offset-2"
      >
        범례
        <Icon name={open ? 'chevronLeft' : 'chevronRight'} size={16} className="shrink-0 text-ink-muted" />
      </button>
      {open && (
        <ul
          id={listId}
          className="flex flex-col gap-1.5 border-t border-line px-3 pt-2 pb-2.5 lg:flex-row lg:gap-4 lg:border-t-0 lg:border-l lg:py-0 lg:pr-4"
        >
          {LEGEND_ITEMS.map((item) => (
            <li key={item.key} className="flex items-center gap-2 text-sm">
              <span aria-hidden="true" className={`block size-3 shrink-0 rounded-full ${item.swatch}`} />
              {item.label}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
