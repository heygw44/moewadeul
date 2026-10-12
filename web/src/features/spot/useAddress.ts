import { useEffect, useState } from 'react';
import { addressAt } from '../../map/addressName';
import type { LatLng } from '../../map/types';

export type AddressLookup = { status: 'loading' } | { status: 'ready'; address: string } | { status: 'none' };

type Result = { key: string; address: string | null };

// 좌표의 주소를 찾는다. 주소가 없거나 찾지 못하면 none이다.
// 결과를 좌표 키와 함께 기억하고, 지금 좌표와 키가 다르면 찾는 중으로 본다. 그래서 위치가 바뀌면 바로 찾는 중이 되고,
// 늦게 도착한 이전 위치의 응답은 키가 달라서 쓰이지 않는다.
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
