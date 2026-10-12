import type { LatLng } from '../../map/types';
import { useAddress } from './useAddress';

// 지도에서 찍은 위치를 주소로 보여 준다. 좌표는 사용자가 어디인지 알아보기 어려워서,
// 바다처럼 주소가 없거나 주소를 찾지 못했을 때만 대신 보여 준다.
export function LocationLabel({ position }: { position: LatLng }) {
  const lookup = useAddress(position);

  if (lookup.status === 'loading') {
    return <p className="text-sm text-ink-muted">주소를 찾고 있어요</p>;
  }
  if (lookup.status === 'ready') {
    // "진상미로1588번길"처럼 붙은 낱말 중간에서 줄이 바뀌지 않게 띄어쓰기에서만 줄을 바꾸고, 칸보다 긴 낱말만 자른다.
    return <p className="break-keep wrap-break-word text-base text-ink">{lookup.address}</p>;
  }
  return (
    <p className="font-mono text-sm tabular-nums text-ink">
      {position.lat.toFixed(5)}, {position.lng.toFixed(5)}
    </p>
  );
}
