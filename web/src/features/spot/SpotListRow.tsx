import type { MouseEvent } from 'react';
import { Link } from '../../app/router';
import type { SpotMarker } from '../../api/types';
import { MetaLine, StatusText } from '../../components/StatusText';
import { formatDistanceKm } from '../../lib/geo';
import { SPOT_TYPE_META } from './spotLabels';

type SpotListRowProps = {
  marker: SpotMarker;
  // 목록 순서 번호다. 지도 핀에 같은 번호가 적힌다.
  number: number;
  // 지도 가운데(반경 검색이면 기준점)에서 떨어진 거리(km)다.
  distanceKm: number;
  selected?: boolean;
  // 넘기면 행을 눌렀을 때 상세로 가지 않고 이 함수를 부른다. 데스크톱에서 지도 위 근거 판을 띄울 때 쓴다.
  onPreview?: (spotId: number) => void;
  // large는 랜딩 안내판처럼 행을 넉넉하게 두고 거리를 크게 적는다.
  size?: 'default' | 'large';
};

// 지도 마커를 누르지 못하는 사람도 같은 장소를 고를 수 있도록, 목록 행 하나가 마커 하나와 짝을 이룬다.
// 탐방로 안내판의 갈림길 행처럼 왼쪽에 번호 원, 가운데 이름, 오른쪽에 굵은 거리를 둔다.
export function SpotListRow({
  marker,
  number,
  distanceKm,
  selected = false,
  onPreview,
  size = 'default',
}: SpotListRowProps) {
  const large = size === 'large';
  const meta = SPOT_TYPE_META[marker.type];
  const roundel = marker.type === 'BAKJI' ? 'bg-bakji' : 'bg-brand';
  const distance = formatDistanceKm(distanceKm);

  function handleClick(event: MouseEvent<HTMLAnchorElement>) {
    // 새 탭으로 여는 클릭은 그대로 상세 주소로 보낸다.
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
        className={`grid min-h-16 items-center hover:bg-fill focus-visible:-outline-offset-2 ${
          large
            ? 'grid-cols-[2.25rem_minmax(0,1fr)_auto] gap-4 px-5 py-4 lg:px-7'
            : 'grid-cols-[1.75rem_minmax(0,1fr)_auto] gap-3 px-4 py-3'
        } ${selected ? 'bg-accent-soft' : ''}`}
      >
        <span
          aria-hidden="true"
          className={`flex items-center justify-center rounded-full font-mono font-extrabold tabular-nums text-white ${
            large ? 'size-9 text-base' : 'size-7 text-sm'
          } ${roundel}`}
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
        <span
          className={`font-mono font-extrabold tabular-nums text-ink ${
            large ? 'text-[1.75rem] tracking-[-0.03em] lg:text-[2rem]' : 'text-lg tracking-[-0.02em]'
          }`}
        >
          {distance}
          <span className={`ml-0.5 font-semibold text-ink-muted ${large ? 'text-sm' : 'text-xs'}`}>km</span>
        </span>
      </Link>
    </li>
  );
}
