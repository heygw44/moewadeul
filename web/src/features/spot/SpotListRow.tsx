import { Link } from '../../app/router';
import type { SpotMarker } from '../../api/types';
import { MetaLine, StatusText } from '../../components/StatusText';
import { SPOT_TYPE_META } from './spotLabels';

type SpotListRowProps = {
  marker: SpotMarker;
  // 반경 검색 결과에서만 넘긴다. 기준점에서 떨어진 거리(km)를 이름 오른쪽에 적는다.
  distanceKm?: number;
};

// 지도 마커를 누르지 못하는 사람도 같은 장소를 고를 수 있도록, 목록 행 하나가 마커 하나와 짝을 이룬다.
// 행은 선 없이 흰 면 한 장이고, 첫 줄은 이름과 거리, 둘째 줄은 유형과 판단에 필요한 경고·휴장이다.
export function SpotListRow({ marker, distanceKm }: SpotListRowProps) {
  const meta = SPOT_TYPE_META[marker.type];

  return (
    <li>
      <Link
        to={`/spots/${marker.spotId}`}
        className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-0.5 rounded-control bg-surface px-4 py-3.5 transition-colors hover:bg-fill focus-visible:-outline-offset-2"
      >
        <span className="min-w-0 truncate text-[17px] font-bold text-ink">{marker.name}</span>
        {distanceKm !== undefined ? (
          <span className="text-right text-sm text-ink-muted tabular-nums">{distanceKm}km</span>
        ) : (
          <span />
        )}
        <MetaLine
          className="col-span-2"
          items={[
            <span>{meta.label}</span>,
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
      </Link>
    </li>
  );
}
