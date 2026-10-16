import { Link } from '../../app/router';
import { formatLocalDate } from '../../lib/datetime';
import { memoryCellId, memoryCellLabel } from './memoryItems';
import type { MemoryCellItem } from './memoryItems';
import { nightsLabel } from './memoryLabels';

// 사진이 있으면 사진, 없으면 방문일과 박 수를 보여 주는 3열 정사각 격자다. 주는 순서 그대로 늘어놓는다.
// 칸을 누르면 기록 상세로 간다. 지도에서 고른 기록(selectedId)은 숲색 테두리로 드러낸다.
export function MemoryGrid({ items, selectedId }: { items: MemoryCellItem[]; selectedId: number | null }) {
  return (
    <ul aria-label="기록 목록" className="grid grid-cols-3 gap-1">
      {items.map((item) => {
        const selected = item.id === selectedId;
        return (
          <li key={item.id} id={memoryCellId(item.id)} className="flex min-w-0 flex-col gap-1 scroll-mt-20">
            <Link
              to={`/memories/${item.id}`}
              aria-current={selected ? 'true' : undefined}
              className={`block aspect-square w-full overflow-hidden rounded-control border bg-surface ${
                selected ? 'border-accent ring-2 ring-accent' : 'border-line'
              }`}
            >
              {item.thumbnailUrl ? (
                <img
                  src={item.thumbnailUrl}
                  alt={memoryCellLabel(item)}
                  loading="lazy"
                  className="h-full w-full object-cover"
                />
              ) : (
                <span className="flex h-full w-full flex-col justify-between p-2 text-left">
                  <span aria-hidden="true" className="font-mono text-xs tabular-nums text-ink">
                    {formatLocalDate(item.visitedDate)}
                  </span>
                  <span className="text-sm font-semibold text-ink">
                    <span className="sr-only">{memoryCellLabel(item)}. </span>
                    <span aria-hidden="true">{nightsLabel(item.nights)}</span>
                  </span>
                </span>
              )}
            </Link>
            {item.isPrivate && <span className="text-xs text-ink-muted">나만 보기</span>}
            {item.hiddenLabel && <span className="text-xs font-semibold text-warning">{item.hiddenLabel}</span>}
          </li>
        );
      })}
    </ul>
  );
}
