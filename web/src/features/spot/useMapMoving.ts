import { useEffect, useState } from 'react';
import type { MapView } from '../../map/types';

export type MapMoving = {
  // 사용자가 지도를 끌거나 확대·축소하는 중이다.
  moving: boolean;
  // 지도 위 오버레이를 숨길지다. 움직이기 시작할 때 keepVisible이 참이면 숨기지 않는다.
  hidden: boolean;
};

const STILL: MapMoving = { moving: false, hidden: false };

// 지도가 움직이기 시작하면 참이 되고, 멈추면(idle) 거짓으로 돌아온다.
export function useMapMoving(map: MapView | null, keepVisible: () => boolean): MapMoving {
  const [state, setState] = useState<MapMoving>(STILL);

  useEffect(() => {
    if (!map) return;
    const stopMoveStart = map.onMoveStart(() => setState({ moving: true, hidden: !keepVisible() }));
    const stopIdle = map.onIdle(() => setState(STILL));
    return () => {
      stopMoveStart();
      stopIdle();
      setState(STILL);
    };
  }, [map, keepVisible]);

  return state;
}
