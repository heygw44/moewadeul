import { useId, useState } from 'react';
import { Icon } from '../../components/icons';
import { readStored, writeStored } from '../../lib/storage';
import { KIND_STYLE, PIN_PATH } from '../../map/markers';
import type { MarkerKind } from '../../map/types';

const OPEN_KEY = 'moewadeul.mapLegendOpen';
const DESKTOP_QUERY = '(min-width: 64rem)';
const LEGEND_KINDS: ReadonlyArray<MarkerKind> = ['CAMPSITE', 'FOREST', 'BAKJI'];

// 사용자가 한 번 접거나 펼치면 그 상태를 따른다. 처음에는 넓은 화면에서 펼치고, 폰에서는 지도를 덜 가리게 접어 둔다.
function initialOpen(): boolean {
  const stored = readStored(OPEN_KEY);
  if (stored !== null) return stored === '1';
  return window.matchMedia(DESKTOP_QUERY).matches;
}

// 지도 왼쪽 아래의 핀 유형 범례다. 핀은 지도 마커와 같은 모양·색을 작게 그린다.
// 모바일에서는 접힌 시트 위 지역 칩(높이 36px)의 한 줄 위에 둔다. 펼치면 위로만 커져서 칩을 가리지 않는다. 시트를 올리면 시트가 덮는다.
export function MapLegend() {
  const [open, setOpen] = useState(initialOpen);
  const listId = useId();

  function toggle() {
    const next = !open;
    writeStored(OPEN_KEY, next ? '1' : '0');
    setOpen(next);
  }

  return (
    <div className="pointer-events-auto absolute bottom-[calc(28dvh+var(--bottom-nav-h)+3.5rem)] left-4 z-10 flex flex-col rounded-control border border-line bg-surface text-ink lg:bottom-4">
      <button
        type="button"
        onClick={toggle}
        aria-expanded={open}
        aria-controls={listId}
        className="inline-flex min-h-11 items-center gap-1.5 px-3 text-sm font-semibold hover:bg-fill"
      >
        범례
        <Icon name={open ? 'chevronLeft' : 'chevronRight'} size={16} className="shrink-0 text-ink-muted" />
      </button>
      {open && (
        <ul id={listId} className="flex flex-col gap-1.5 border-t border-line px-3 pb-2.5 pt-2">
          {LEGEND_KINDS.map((kind) => {
            const style = KIND_STYLE[kind];
            return (
              <li key={kind} className="flex items-center gap-2 text-sm">
                <svg className="block shrink-0" width="18" height="22" viewBox="0 0 36 44" aria-hidden="true">
                  <path className={style.fill} d={PIN_PATH} />
                  <Icon name={style.icon} x={8} y={8} size={20} className="text-white" />
                </svg>
                {style.label}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
