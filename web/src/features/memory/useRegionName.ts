import { useEffect, useState } from 'react';
import { regionNameAt } from '../../map/regionName';
import type { LatLng } from '../../map/types';

export type RegionLookup = { status: 'loading' } | { status: 'ready'; name: string } | { status: 'none' };

type Result = { key: string; name: string | null };

// 좌표의 읍·면·동 이름을 찾는다. 행정구역이 없거나 찾지 못하면 none이다.
// 결과를 좌표 키와 함께 기억하고, 지금 좌표와 키가 다르면 찾는 중으로 본다. 그래서 위치가 바뀌면 바로 찾는 중이 되고,
// 늦게 도착한 이전 위치의 응답은 키가 달라서 쓰이지 않는다.
export function useRegionName(position: LatLng): RegionLookup {
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
