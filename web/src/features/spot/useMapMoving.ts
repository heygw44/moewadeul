import { useEffect, useState } from 'react';
import type { MapView } from '../../map/types';

export type MapMoving = {
  moving: boolean;
  hidden: boolean;
};

const STILL: MapMoving = { moving: false, hidden: false };

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
