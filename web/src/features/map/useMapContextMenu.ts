import { useCallback, useEffect, useState } from 'react';
import type { RefObject } from 'react';
import type { LatLng, MapView } from '../../map/types';
import { DESKTOP_QUERY } from './useIsDesktop';

export type ContextMenu = { position: LatLng; host: HTMLElement };

export function visibleMapHeight(host: HTMLElement | null): number | undefined {
  if (!host || window.matchMedia(DESKTOP_QUERY).matches) return undefined;
  const sheet = host.closest('[data-map-root]')?.querySelector('[data-sheet]');
  if (!sheet) return undefined;
  return sheet.getBoundingClientRect().top - host.getBoundingClientRect().top;
}

export function useMapContextMenu(
  map: MapView | null,
  enabled: boolean,
  hostRef: RefObject<HTMLElement | null>,
  resetKey: string | null,
) {
  const [contextMenu, setContextMenu] = useState<ContextMenu | null>(null);
  const closeContextMenu = useCallback(() => {
    map?.showMenu(null);
    setContextMenu(null);
  }, [map]);

  useEffect(() => {
    if (!map || !enabled) return;
    const offContextMenu = map.onContextMenu((position) => {
      const host = map.showMenu(position, visibleMapHeight(hostRef.current));
      setContextMenu(host ? { position, host } : null);
    });
    const offMoveStart = map.onMoveStart(closeContextMenu);
    const offClick = map.onMapClick(closeContextMenu);
    return () => {
      offContextMenu();
      offMoveStart();
      offClick();
      closeContextMenu();
    };
  }, [map, enabled, closeContextMenu, hostRef, resetKey]);

  return { contextMenu, closeContextMenu };
}
