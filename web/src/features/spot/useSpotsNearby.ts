import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchSpotsNearby } from '../../api/spots';
import { serverTraceId, toUserMessage } from '../../api/errors';
import type { NearbySpot, SpotSetting, SpotType } from '../../api/types';
import type { LatLng, MapView } from '../../map/types';
import type { SpotFilters } from './spotFilters';

const PAGE_SIZE = 50;

type NearbyStatus = 'idle' | 'loading' | 'ready' | 'error';

export type RadiusArea = { center: LatLng; radiusKm: number };

export type SpotsNearby = {
  items: NearbySpot[];
  hasNext: boolean;
  status: NearbyStatus;
  error: string | null;
  traceId: string | null;
  loadingMore: boolean;
  moreError: string | null;
  retry(): void;
  loadMore(): void;
};

type NearbyState = {
  items: NearbySpot[];
  page: number;
  hasNext: boolean;
  status: NearbyStatus;
  error: string | null;
  traceId: string | null;
  loadingMore: boolean;
  moreError: string | null;
};

const INITIAL_STATE: NearbyState = {
  items: [],
  page: 0,
  hasNext: false,
  status: 'idle',
  error: null,
  traceId: null,
  loadingMore: false,
  moreError: null,
};

export function useSpotsNearby(
  map: MapView | null,
  radius: RadiusArea | null,
  filters: SpotFilters,
  enabled: boolean,
): SpotsNearby {
  const [state, setState] = useState<NearbyState>(INITIAL_STATE);
  const controllerRef = useRef<AbortController | null>(null);
  const moreControllerRef = useRef<AbortController | null>(null);
  const stateRef = useRef<NearbyState>(INITIAL_STATE);
  const loadRef = useRef<() => void>(() => {});
  const moreRef = useRef<() => void>(() => {});

  const typesKey = [...filters.types].sort().join(',');
  const settingsKey = [...filters.settings].sort().join(',');
  const { hasWater, hasToilet, excludeWarning, hideClosed } = filters;
  const centerLat = radius?.center.lat;
  const centerLng = radius?.center.lng;
  const radiusKm = radius?.radiusKm;

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  useEffect(() => {
    if (!map || !enabled || centerLat === undefined || centerLng === undefined || radiusKm === undefined) return;
    const query = {
      lat: centerLat,
      lng: centerLng,
      radiusKm,
      types: typesKey === '' ? undefined : (typesKey.split(',') as SpotType[]),
      settings: settingsKey === '' ? undefined : (settingsKey.split(',') as SpotSetting[]),
      hasWater: hasWater || undefined,
      hasToilet: hasToilet || undefined,
      excludeWarning: excludeWarning || undefined,
      hideClosed: hideClosed || undefined,
      size: PAGE_SIZE,
    };

    const draw = (items: NearbySpot[]) =>
      map.showMarkers(
        items.map((item, index) => ({
          id: item.spotId,
          kind: item.type,
          name: item.name,
          position: { lat: item.lat, lng: item.lng },
          warning: item.parkWarning,
          closed: item.closedNow,
          label: String(index + 1),
        })),
        [],
      );

    map.showRadius({ center: { lat: centerLat, lng: centerLng }, radiusKm });

    const request = () => {
      controllerRef.current?.abort();
      moreControllerRef.current?.abort();
      const controller = new AbortController();
      controllerRef.current = controller;

      fetchSpotsNearby({ ...query, page: 0 }, controller.signal).then(
        (response) => {
          if (controller.signal.aborted) return;
          draw(response.content);
          setState({
            items: response.content,
            page: response.page,
            hasNext: response.hasNext,
            status: 'ready',
            error: null,
            traceId: null,
            loadingMore: false,
            moreError: null,
          });
        },
        (error: unknown) => {
          if (controller.signal.aborted) return;
          setState((previous) => ({
            ...previous,
            status: 'error',
            error: toUserMessage(error),
            traceId: serverTraceId(error) ?? null,
            loadingMore: false,
          }));
        },
      );
    };

    const reload = () => {
      setState((previous) => ({ ...previous, status: 'loading', error: null, moreError: null, traceId: null }));
      request();
    };

    const loadMore = () => {
      const current = stateRef.current;
      if (!current.hasNext || current.loadingMore || current.status !== 'ready') return;
      const controller = new AbortController();
      moreControllerRef.current = controller;
      setState((previous) => ({ ...previous, loadingMore: true, moreError: null, traceId: null }));

      fetchSpotsNearby({ ...query, page: current.page + 1 }, controller.signal).then(
        (response) => {
          if (controller.signal.aborted) return;
          const known = new Set(stateRef.current.items.map((item) => item.spotId));
          const items = [...stateRef.current.items, ...response.content.filter((item) => !known.has(item.spotId))];
          draw(items);
          setState({
            items,
            page: response.page,
            hasNext: response.hasNext,
            status: 'ready',
            error: null,
            traceId: null,
            loadingMore: false,
            moreError: null,
          });
        },
        (error: unknown) => {
          if (controller.signal.aborted) return;
          setState((previous) => ({
            ...previous,
            loadingMore: false,
            moreError: toUserMessage(error),
            traceId: serverTraceId(error) ?? null,
          }));
        },
      );
    };

    loadRef.current = reload;
    moreRef.current = loadMore;
    request();

    return () => {
      controllerRef.current?.abort();
      moreControllerRef.current?.abort();
      controllerRef.current = null;
      moreControllerRef.current = null;
      loadRef.current = () => {};
      moreRef.current = () => {};
      map.showRadius(null);
      setState(INITIAL_STATE);
    };
  }, [
    map,
    enabled,
    centerLat,
    centerLng,
    radiusKm,
    typesKey,
    settingsKey,
    hasWater,
    hasToilet,
    excludeWarning,
    hideClosed,
  ]);

  const retry = useCallback(() => loadRef.current(), []);
  const loadMore = useCallback(() => moreRef.current(), []);

  const active = map !== null && enabled && radius !== null;
  const status: NearbyStatus = active && state.status === 'idle' ? 'loading' : state.status;
  return { ...state, status, retry, loadMore };
}
