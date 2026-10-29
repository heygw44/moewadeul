import type { LatLng } from '../../map/types';
import { useRegionNameAt } from './useRegionNameAt';

export function ApproxLocationLabel({ position }: { position: LatLng }) {
  const lookup = useRegionNameAt(position);

  if (lookup.status === 'loading') {
    return <p className="text-sm text-ink-muted">위치를 찾고 있어요</p>;
  }
  if (lookup.status === 'ready') {
    return <p className="break-keep wrap-break-word text-base text-ink">{lookup.name}</p>;
  }
  return (
    <p className="font-mono text-sm tabular-nums text-ink">
      {position.lat.toFixed(2)}, {position.lng.toFixed(2)}
    </p>
  );
}
