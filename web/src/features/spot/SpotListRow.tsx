import { Link } from '../../app/router';
import type { SpotMarker } from '../../api/types';
import { Icon } from '../../components/icons';
import { MetaLine, StatusText } from '../../components/StatusText';
import { SPOT_TYPE_META } from './spotLabels';

type SpotListRowProps = {
  marker: SpotMarker;
};

// 지도 마커를 누르지 못하는 사람도 같은 장소를 고를 수 있도록, 목록 행 하나가 마커 하나와 짝을 이룬다.
export function SpotListRow({ marker }: SpotListRowProps) {
  const meta = SPOT_TYPE_META[marker.type];

  return (
    <li>
      <Link
        to={`/spots/${marker.spotId}`}
        className="flex min-h-14 items-center gap-3 border-b border-line px-4 py-3 hover:bg-fill focus-visible:-outline-offset-2"
      >
        <span className={`flex size-8 shrink-0 items-center justify-center rounded-control ${meta.tileClass}`}>
          <Icon name={meta.icon} size={18} />
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-1">
          <span className="truncate text-base text-ink">{marker.name}</span>
          {/* 장소 유형은 왼쪽 아이콘이 이미 알린다. 그래서 판단에 필요한 경고와 휴장만 글로 적는다. */}
          <MetaLine
            items={[
              marker.parkWarning && (
                <StatusText tone="warning" icon="alert">
                  공원 경계 경고
                </StatusText>
              ),
              marker.closedNow && (
                <StatusText tone="closed" icon="closed">
                  휴장 중
                </StatusText>
              ),
            ]}
          />
        </span>
      </Link>
    </li>
  );
}
