import { useEffect, useEffectEvent, useState } from 'react';
import type { RefObject } from 'react';
import { SHEET_SNAP_DVH } from '../../components/sheetSnap';
import type { SheetSnap } from '../../components/sheetSnap';
import type { LatLng, MapView, SpotMarkerData } from '../../map/types';
import { DESKTOP_QUERY } from './useIsDesktop';

const SPOT_FOCUS_LEVEL = 7;
const MIN_OPEN_SPAN_PX = 48;

function openMapSpan(
  host: HTMLElement,
  controls: HTMLElement | null,
  snap: SheetSnap,
): { top: number; bottom: number } {
  const rect = host.getBoundingClientRect();
  if (window.matchMedia(DESKTOP_QUERY).matches) return { top: 0, bottom: rect.height };
  const sheetTop = window.innerHeight * (1 - SHEET_SNAP_DVH[snap] / 100);
  const bottom = Math.min(rect.height, sheetTop - rect.top);
  const top = controls ? Math.max(0, controls.getBoundingClientRect().bottom - rect.top) : 0;
  if (bottom - top >= MIN_OPEN_SPAN_PX) return { top, bottom };
  return bottom >= MIN_OPEN_SPAN_PX ? { top: 0, bottom } : { top: 0, bottom: rect.height };
}

function centerPlacing(map: MapView, position: LatLng, level: number, mapHeight: number, targetY: number): LatLng {
  if (mapHeight <= 0) return position;
  const { sw, ne } = map.getBounds();
  const latPerPx = ((ne.lat - sw.lat) / mapHeight) * 2 ** (level - map.getLevel());
  return { lat: position.lat - (mapHeight / 2 - targetY) * latPerPx, lng: position.lng };
}

type SpotFocusLayout = {
  hostRef: RefObject<HTMLElement | null>;
  controlsRef: RefObject<HTMLElement | null>;
  snap: SheetSnap;
};

export function useSpotFocus(
  map: MapView | null,
  focusKey: string | null,
  hideSelection: boolean,
  { hostRef, controlsRef, snap }: SpotFocusLayout,
) {
  const [spotFocus, setSpotFocus] = useState<{ key: string; marker: SpotMarkerData } | null>(null);
  if (spotFocus !== null && spotFocus.key !== focusKey) setSpotFocus(null);
  const focusedSpot = spotFocus?.marker ?? null;

  const focusSpot = (marker: SpotMarkerData | null) => {
    if (focusKey !== null && marker !== null) {
      setSpotFocus((previous) => (previous?.key === focusKey ? previous : { key: focusKey, marker }));
    }
  };

  const moveToSpot = useEffectEvent((position: LatLng) => {
    const host = hostRef.current;
    if (!map || !host) return;
    map.relayout();
    const level = Math.min(map.getLevel(), SPOT_FOCUS_LEVEL);
    const { top, bottom } = openMapSpan(host, controlsRef.current, snap);
    map.panTo(centerPlacing(map, position, level, host.getBoundingClientRect().height, (top + bottom) / 2), level);
  });
  useEffect(() => {
    if (map && focusedSpot) moveToSpot(focusedSpot.position);
  }, [map, focusedSpot]);

  useEffect(() => {
    map?.showSelectedSpot(hideSelection ? null : focusedSpot);
  }, [map, focusedSpot, hideSelection]);

  return { focusedSpot, focusSpot };
}
