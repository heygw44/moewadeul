import { useCallback, useEffect, useRef, useState } from 'react';
import { serverTraceId, toUserMessage } from '../../api/errors';
import { searchBasecamps } from '../../api/basecamps';
import type { BasecampSearchItem, SpotType } from '../../api/types';
import type { MapView, SpotMarkerData } from '../../map/types';
import type { BasecampFilters } from './basecampFilters';

export type { BasecampFilters } from './basecampFilters';

const PAGE_SIZE = 50;

type SearchStatus = 'idle' | 'loading' | 'ready' | 'error';

export type BasecampsInView = {
  items: BasecampSearchItem[];
  hasNext: boolean;
  status: SearchStatus;
  error: string | null;
  traceId: string | null;
  loadingMore: boolean;
  moreError: string | null;
  retry(): void;
  loadMore(): void;
};

type SearchState = {
  items: BasecampSearchItem[];
  page: number;
  hasNext: boolean;
  status: SearchStatus;
  error: string | null;
  traceId: string | null;
  loadingMore: boolean;
  moreError: string | null;
};

const INITIAL_STATE: SearchState = {
  items: [],
  page: 0,
  hasNext: false,
  status: 'idle',
  error: null,
  traceId: null,
  loadingMore: false,
  moreError: null,
};

type Area = { swLat: number; swLng: number; neLat: number; neLng: number };

function toMarkers(items: BasecampSearchItem[]): SpotMarkerData[] {
  const bySpot = new Map<number, SpotMarkerData>();
  for (const { spot } of items) {
    const existing = bySpot.get(spot.spotId);
    if (existing) {
      existing.count = (existing.count ?? 0) + 1;
    } else {
      bySpot.set(spot.spotId, {
        id: spot.spotId,
        kind: spot.type,
        name: spot.name,
        position: { lat: spot.lat, lng: spot.lng },
        warning: false,
        closed: false,
        count: 1,
      });
    }
  }
  return [...bySpot.values()];
}

export function useBasecampsInView(
  map: MapView | null,
  enabled: boolean,
  filters: BasecampFilters,
  viewerKey: number | null,
): BasecampsInView {
  const [state, setState] = useState<SearchState>(INITIAL_STATE);
  const controllerRef = useRef<AbortController | null>(null);
  const moreControllerRef = useRef<AbortController | null>(null);
  const areaRef = useRef<Area | null>(null);
  const stateRef = useRef<SearchState>(INITIAL_STATE);
  const loadRef = useRef<() => void>(() => {});
  const moreRef = useRef<() => void>(() => {});

  const { fromDate, toDate, hasVacancy, maxTrustLevel, noAgeLimit, noSameGenderOnly } = filters;
  const typesKey = [...filters.types].sort().join(',');
  const nightsKey = [...filters.nights].sort((a, b) => a - b).join(',');

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  useEffect(() => {
    if (!map || !enabled) return;
    const filterQuery = {
      fromDate: fromDate === '' ? undefined : fromDate,
      toDate: toDate === '' ? undefined : toDate,
      hasVacancy: hasVacancy ? true : undefined,
      types: typesKey === '' ? undefined : (typesKey.split(',') as SpotType[]),
      nights: nightsKey === '' ? undefined : nightsKey.split(',').map(Number),
      maxTrustLevel: maxTrustLevel ?? undefined,
      noAgeLimit: noAgeLimit || undefined,
      noSameGenderOnly: noSameGenderOnly || undefined,
      size: PAGE_SIZE,
    };

    const request = () => {
      controllerRef.current?.abort();
      moreControllerRef.current?.abort();
      const controller = new AbortController();
      controllerRef.current = controller;
      const { sw, ne } = map.getBounds();
      const area: Area = { swLat: sw.lat, swLng: sw.lng, neLat: ne.lat, neLng: ne.lng };
      areaRef.current = area;

      searchBasecamps({ ...area, ...filterQuery, page: 0 }, controller.signal).then(
        (response) => {
          if (controller.signal.aborted) return;
          map.showMarkers(toMarkers(response.content), []);
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
      const area = areaRef.current;
      if (!area || !current.hasNext || current.loadingMore || current.status !== 'ready') return;
      const controller = new AbortController();
      moreControllerRef.current = controller;
      setState((previous) => ({ ...previous, loadingMore: true, moreError: null, traceId: null }));

      searchBasecamps({ ...area, ...filterQuery, page: current.page + 1 }, controller.signal).then(
        (response) => {
          if (controller.signal.aborted) return;
          const known = new Set(stateRef.current.items.map((item) => item.basecampId));
          const items = [...stateRef.current.items, ...response.content.filter((item) => !known.has(item.basecampId))];
          map.showMarkers(toMarkers(items), []);
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
    const unsubscribe = map.onIdle(reload);
    request();

    return () => {
      unsubscribe();
      controllerRef.current?.abort();
      moreControllerRef.current?.abort();
      controllerRef.current = null;
      moreControllerRef.current = null;
      areaRef.current = null;
      loadRef.current = () => {};
      moreRef.current = () => {};
      setState(INITIAL_STATE);
    };
  }, [
    map,
    enabled,
    fromDate,
    toDate,
    hasVacancy,
    typesKey,
    nightsKey,
    maxTrustLevel,
    noAgeLimit,
    noSameGenderOnly,
    viewerKey,
  ]);

  const retry = useCallback(() => loadRef.current(), []);
  const loadMore = useCallback(() => moreRef.current(), []);

  const status: SearchStatus = map && enabled && state.status === 'idle' ? 'loading' : state.status;
  return { ...state, status, retry, loadMore };
}
