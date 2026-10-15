import type { LatLng } from '../../map/types';
import { useRegionName } from './useRegionName';

// 대략 위치를 읍·면·동까지만 보여 준다. 번지까지 보이면 정확한 위치처럼 읽히기 때문이다.
// 행정구역을 찾지 못했을 때(바다 등)만 소수 둘째 자리로 줄인 좌표를 대신 보여 준다.
export function ApproxLocationLabel({ position }: { position: LatLng }) {
  const lookup = useRegionName(position);

  if (lookup.status === 'loading') {
    return <p className="text-sm text-ink-muted">위치를 찾고 있어요</p>;
  }
  if (lookup.status === 'ready') {
    return <p className="break-keep wrap-break-word text-base text-ink">{lookup.name}</p>;
  }
  return (
    <p className="text-sm tabular-nums text-ink">
      {position.lat.toFixed(2)}, {position.lng.toFixed(2)}
    </p>
  );
}
