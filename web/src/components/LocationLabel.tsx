import type { LatLng } from '../map/types';
import { useAddress } from './useAddress';

export function LocationLabel({ position }: { position: LatLng }) {
  const lookup = useAddress(position);

  if (lookup.status === 'loading') {
    return <p className="text-sm text-ink-muted">주소를 찾고 있어요</p>;
  }
  if (lookup.status === 'ready') {
    return <p className="break-keep wrap-break-word text-base text-ink">{lookup.address}</p>;
  }
  return (
    <p className="font-mono text-sm tabular-nums text-ink">
      {position.lat.toFixed(5)}, {position.lng.toFixed(5)}
    </p>
  );
}
