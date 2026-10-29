import { useEffect, useState } from 'react';
import { regionLabelInView } from '../../map/regionName';
import type { RegionLabel } from '../../map/regionLabel';
import type { MapView } from '../../map/types';

export function useRegionLabelInView(map: MapView | null): RegionLabel | null {
  const [label, setLabel] = useState<RegionLabel | null>(null);

  useEffect(() => {
    if (!map) return;
    let latest = 0;
    let active = true;
    const lookup = () => {
      const request = ++latest;
      const accept = (found: RegionLabel | null) => {
        if (active && request === latest) setLabel(found);
      };
      regionLabelInView(map.getBounds(), map.getCenter(), map.getLevel()).then(accept, () => accept(null));
    };
    const unsubscribe = map.onIdle(lookup);
    lookup();
    return () => {
      active = false;
      unsubscribe();
      setLabel(null);
    };
  }, [map]);

  return label;
}
