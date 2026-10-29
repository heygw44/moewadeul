import { useEffect, useEffectEvent } from 'react';
import { navigate } from '../../app/router';
import { searchRegions } from '../../map/regionSearch';
import type { RegionCandidate } from '../../map/regionSearch';
import type { MapView } from '../../map/types';

export function useRegionQueryLink(
  map: MapView | null,
  enabled: boolean,
  onRegion: (candidate: RegionCandidate) => void,
) {
  const selectRegion = useEffectEvent(onRegion);
  useEffect(() => {
    if (!map || !enabled) return;
    const query = new URLSearchParams(window.location.search).get('q')?.trim();
    if (!query) return;
    navigate('/map', { replace: true });
    let cancelled = false;
    searchRegions(query)
      .then(([first]) => {
        if (!cancelled && first) selectRegion(first);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [map, enabled]);
}
