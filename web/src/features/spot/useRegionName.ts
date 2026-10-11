import { useEffect, useState } from 'react';
import { regionNameAt } from '../../map/regionName';
import type { MapView } from '../../map/types';

// 지도 가운데가 속한 행정구역 이름이다. 아직 모르거나 찾지 못하면 null이다.
export function useRegionName(map: MapView | null): string | null {
  const [name, setName] = useState<string | null>(null);

  useEffect(() => {
    if (!map) return;
    // 앞선 조회가 늦게 끝나도 마지막 조회 결과만 쓰려고 요청마다 번호를 붙인다. 구독을 끊은 뒤 온 응답도 버린다.
    let latest = 0;
    let active = true;
    const lookup = () => {
      const request = ++latest;
      const accept = (found: string | null) => {
        if (active && request === latest) setName(found);
      };
      regionNameAt(map.getCenter(), map.getLevel()).then(accept, () => accept(null));
    };
    const unsubscribe = map.onIdle(lookup);
    lookup();
    return () => {
      active = false;
      unsubscribe();
      setName(null);
    };
  }, [map]);

  return name;
}
