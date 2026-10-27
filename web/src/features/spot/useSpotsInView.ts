import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchSpotsInArea } from '../../api/spots';
import { serverTraceId, toUserMessage } from '../../api/errors';
import type { SpotCluster, SpotMarker, SpotSetting, SpotType } from '../../api/types';
import type { MapView } from '../../map/types';
import { distanceKm } from '../../lib/geo';
import { NO_SPOT_FILTERS } from './spotFilters';
import type { SpotFilters } from './spotFilters';

type AreaStatus = 'idle' | 'loading' | 'ready' | 'error';

export type ListedSpot = SpotMarker & { distanceKm: number };

export type SpotsInView = {
  markers: ListedSpot[];
  clusters: SpotCluster[];
  status: AreaStatus;
  error: string | null;
  traceId: string | null;
  retry(): void;
};

type AreaState = {
  markers: ListedSpot[];
  clusters: SpotCluster[];
  status: AreaStatus;
  error: string | null;
  traceId: string | null;
};

const INITIAL_STATE: AreaState = { markers: [], clusters: [], status: 'idle', error: null, traceId: null };

export function useSpotsInView(
  map: MapView | null,
  enabled = true,
  filters: SpotFilters = NO_SPOT_FILTERS,
): SpotsInView {
  const [state, setState] = useState<AreaState>(INITIAL_STATE);
  const controllerRef = useRef<AbortController | null>(null);
  const loadRef = useRef<() => void>(() => {});

  const typesKey = [...filters.types].sort().join(',');
  const settingsKey = [...filters.settings].sort().join(',');
  const { hasWater, hasToilet, excludeWarning, hideClosed } = filters;

  useEffect(() => {
    if (!map || !enabled) return;
    const filterQuery = {
      types: typesKey === '' ? undefined : (typesKey.split(',') as SpotType[]),
      settings: settingsKey === '' ? undefined : (settingsKey.split(',') as SpotSetting[]),
      hasWater: hasWater || undefined,
      hasToilet: hasToilet || undefined,
      excludeWarning: excludeWarning || undefined,
      hideClosed: hideClosed || undefined,
    };
    const request = () => {
      controllerRef.current?.abort();
      const controller = new AbortController();
      controllerRef.current = controller;
      const { sw, ne } = map.getBounds();

      fetchSpotsInArea(
        { swLat: sw.lat, swLng: sw.lng, neLat: ne.lat, neLng: ne.lng, zoom: map.getLevel(), ...filterQuery },
        controller.signal,
      ).then(
        (response) => {
          if (controller.signal.aborted) return;
          const center = map.getCenter();
          const listed = response.markers
            .map((marker) => ({ ...marker, distanceKm: distanceKm(center, { lat: marker.lat, lng: marker.lng }) }))
            .sort((a, b) => a.distanceKm - b.distanceKm);
          map.showMarkers(
            listed.map((marker, index) => ({
              id: marker.spotId,
              kind: marker.type,
              name: marker.name,
              position: { lat: marker.lat, lng: marker.lng },
              warning: marker.parkWarning,
              closed: marker.closedNow,
              label: String(index + 1),
            })),
            response.clusters.map((cluster) => ({
              position: { lat: cluster.lat, lng: cluster.lng },
              count: cluster.count,
            })),
          );
          setState({ markers: listed, clusters: response.clusters, status: 'ready', error: null, traceId: null });
        },
        (error: unknown) => {
          if (controller.signal.aborted) return;
          setState((previous) => ({
            ...previous,
            status: 'error',
            error: toUserMessage(error),
            traceId: serverTraceId(error) ?? null,
          }));
        },
      );
    };

    const reload = () => {
      setState((previous) => ({ ...previous, status: 'loading', error: null, traceId: null }));
      request();
    };

    loadRef.current = reload;
    const unsubscribe = map.onIdle(reload);
    request();

    return () => {
      unsubscribe();
      controllerRef.current?.abort();
      controllerRef.current = null;
      loadRef.current = () => {};
      setState(INITIAL_STATE);
    };
  }, [map, enabled, typesKey, settingsKey, hasWater, hasToilet, excludeWarning, hideClosed]);

  const retry = useCallback(() => loadRef.current(), []);

  const status: AreaStatus = map && enabled && state.status === 'idle' ? 'loading' : state.status;
  return { ...state, status, retry };
}
