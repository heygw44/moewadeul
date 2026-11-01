import { useCallback, useEffect, useRef, useState } from 'react';
import type { RegionCandidate } from '../../map/regionSearch';
import type { LatLng, MapView } from '../../map/types';

export function useSearchPin(map: MapView | null) {
  const [searchPin, setSearchPin] = useState<LatLng | null>(null);
  const moveLevelRef = useRef<number | null>(null);

  useEffect(() => {
    map?.showSearchPin(searchPin);
  }, [map, searchPin]);

  useEffect(() => {
    if (!map) return;
    const offIdle = map.onIdle(() => {
      if (moveLevelRef.current === map.getLevel()) moveLevelRef.current = null;
    });
    const offMoveStart = map.onMoveStart(() => {
      if (moveLevelRef.current === null) setSearchPin(null);
    });
    return () => {
      offIdle();
      offMoveStart();
    };
  }, [map]);

  const moveToRegion = useCallback(
    (candidate: RegionCandidate) => {
      if (!map) return;
      moveLevelRef.current = candidate.level;
      map.setCenter(candidate.position, candidate.level);
      if (map.getLevel() === candidate.level) moveLevelRef.current = null;
      setSearchPin(candidate.pinned ? candidate.position : null);
    },
    [map],
  );

  const clearSearchPin = useCallback(() => setSearchPin(null), []);

  return { moveToRegion, clearSearchPin };
}
