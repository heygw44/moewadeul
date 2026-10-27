import { useEffect, useState } from 'react';
import { addressAt } from '../../map/addressName';
import type { LatLng } from '../../map/types';

export type AddressLookup = { status: 'loading' } | { status: 'ready'; address: string } | { status: 'none' };

type Result = { key: string; address: string | null };

export function useAddress(position: LatLng | null): AddressLookup {
  const key = position ? `${position.lat},${position.lng}` : null;
  const lat = position?.lat;
  const lng = position?.lng;
  const [result, setResult] = useState<Result | null>(null);

  useEffect(() => {
    if (key === null || lat === undefined || lng === undefined) return;
    let active = true;
    addressAt({ lat, lng }).then(
      (address) => {
        if (active) setResult({ key, address });
      },
      () => {
        if (active) setResult({ key, address: null });
      },
    );
    return () => {
      active = false;
    };
  }, [key, lat, lng]);

  if (key === null || result === null || result.key !== key) return { status: 'loading' };
  return result.address === null ? { status: 'none' } : { status: 'ready', address: result.address };
}
