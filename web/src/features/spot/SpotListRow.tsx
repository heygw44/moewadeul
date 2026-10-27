import type { MouseEvent } from 'react';
import { Link } from '../../app/router';
import type { SpotMarker } from '../../api/types';
import { MetaLine, StatusText } from '../../components/StatusText';
import { formatDistanceKm } from '../../lib/geo';
import { SPOT_TYPE_META } from './spotLabels';

type SpotListRowProps = {
  marker: SpotMarker;
  number: number;
  distanceKm: number;
  selected?: boolean;
  onPreview?: (spotId: number) => void;
};

export function SpotListRow({ marker, number, distanceKm, selected = false, onPreview }: SpotListRowProps) {
  const meta = SPOT_TYPE_META[marker.type];
  const roundel = marker.type === 'BAKJI' ? 'bg-bakji' : 'bg-brand';
  const distance = formatDistanceKm(distanceKm);

  function handleClick(event: MouseEvent<HTMLAnchorElement>) {
    if (!onPreview || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    onPreview(marker.spotId);
  }

  return (
    <li>
      <Link
        to={`/spots/${marker.spotId}`}
        onClick={handleClick}
        aria-current={selected ? 'true' : undefined}
        className={`grid min-h-16 grid-cols-[1.875rem_minmax(0,1fr)_auto] items-center gap-3 rounded-row px-3 py-3.5 hover:bg-fill focus-visible:-outline-offset-2 ${
          selected ? 'bg-fill' : ''
        }`}
      >
        <span
          aria-hidden="true"
          className={`flex size-[1.875rem] items-center justify-center rounded-full font-mono text-sm font-extrabold tabular-nums text-white ${roundel}`}
        >
          {number}
        </span>
        <span className="flex min-w-0 flex-col gap-0.5">
          <span className="truncate text-base font-bold tracking-[-0.01em] text-ink">
            <span className="sr-only">{number}번 </span>
            {marker.name}
          </span>
          <MetaLine
            className="text-[0.8125rem]"
            items={[
              meta.label,
              marker.parkWarning && (
                <StatusText tone="warning" icon="alert">
                  공원 경계 안일 수 있어요
                </StatusText>
              ),
              marker.closedNow && (
                <StatusText tone="neutral" icon="closed">
                  <span className="text-ink">휴장 중</span>
                </StatusText>
              ),
            ]}
          />
        </span>
        <span className="font-mono text-lg font-extrabold tracking-[-0.02em] tabular-nums text-ink">
          {distance}
          <span className="ml-0.5 text-xs font-semibold text-ink-muted">km</span>
        </span>
      </Link>
    </li>
  );
}
