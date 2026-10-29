import { useEffect, useState } from 'react';
import { regionNameAt } from '../../map/regionName';
import type { LatLng } from '../../map/types';

export type RegionLookup = { status: 'loading' } | { status: 'ready'; name: string } | { status: 'none' };

type Result = { key: string; name: string | null };

export function useRegionNameAt(position: LatLng): RegionLookup {
  const { lat, lng } = position;
  const key = `${lat},${lng}`;
  const [result, setResult] = useState<Result | null>(null);

  useEffect(() => {
    let active = true;
    regionNameAt({ lat, lng }).then(
      (name) => {
        if (active) setResult({ key, name });
      },
      () => {
        if (active) setResult({ key, name: null });
      },
    );
    return () => {
      active = false;
    };
  }, [key, lat, lng]);

  if (result === null || result.key !== key) return { status: 'loading' };
  return result.name === null ? { status: 'none' } : { status: 'ready', name: result.name };
}
