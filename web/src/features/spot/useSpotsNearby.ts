import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchSpotsNearby } from '../../api/spots';
import { toUserMessage } from '../../api/errors';
import type { NearbySpot, SpotSetting, SpotType } from '../../api/types';
import type { LatLng, MapView } from '../../map/types';
import type { SpotFilters } from './spotFilters';

// 첫 페이지와 "더 보기"가 한 번에 받는 건수다. 서버가 허용하는 최대값이다.
const PAGE_SIZE = 50;

type NearbyStatus = 'idle' | 'loading' | 'ready' | 'error';

// 반경 검색의 기준점과 반경이다.
export type RadiusArea = { center: LatLng; radiusKm: number };

export type SpotsNearby = {
  items: NearbySpot[];
  hasNext: boolean;
  status: NearbyStatus;
  error: string | null;
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
  loadingMore: boolean;
  moreError: string | null;
};

const INITIAL_STATE: NearbyState = {
  items: [],
  page: 0,
  hasNext: false,
  status: 'idle',
  error: null,
  loadingMore: false,
  moreError: null,
};

// 기준점에서 반경 안의 장소를 가까운 순서로 받아 지도에 그리고, 반경 원도 함께 그린다.
// 기준점이 고정이라서 지도를 움직여도 다시 받지 않는다. 조건이나 반경이 바뀌면 앞선 요청을 취소하고 처음부터 받는다.
// radius가 null이거나 enabled가 false이면 아무것도 보내지 않고, 그려 둔 반경 원은 지운다.
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

  // 유형·입지 목록은 렌더마다 새 배열일 수 있어서, 효과가 같은 조건으로 다시 돌지 않게 글자로 바꿔 비교한다.
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

    // 요청만 보낸다. 상태는 응답이 온 뒤에만 바꾼다.
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
            loadingMore: false,
            moreError: null,
          });
        },
        (error: unknown) => {
          // 취소한 요청의 실패는 새 요청이 이어받으므로 화면에 반영하지 않는다.
          if (controller.signal.aborted) return;
          setState((previous) => ({ ...previous, status: 'error', error: toUserMessage(error), loadingMore: false }));
        },
      );
    };

    const reload = () => {
      setState((previous) => ({ ...previous, status: 'loading', error: null, moreError: null }));
      request();
    };

    const loadMore = () => {
      const current = stateRef.current;
      if (!current.hasNext || current.loadingMore || current.status !== 'ready') return;
      const controller = new AbortController();
      moreControllerRef.current = controller;
      setState((previous) => ({ ...previous, loadingMore: true, moreError: null }));

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
            loadingMore: false,
            moreError: null,
          });
        },
        (error: unknown) => {
          if (controller.signal.aborted) return;
          setState((previous) => ({ ...previous, loadingMore: false, moreError: toUserMessage(error) }));
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
  }, [map, enabled, centerLat, centerLng, radiusKm, typesKey, settingsKey, hasWater, hasToilet, excludeWarning, hideClosed]);

  const retry = useCallback(() => loadRef.current(), []);
  const loadMore = useCallback(() => moreRef.current(), []);

  // 켜진 뒤 첫 응답 전에는 idle로 남아 있으므로 불러오는 중으로 돌려준다.
  const active = map !== null && enabled && radius !== null;
  const status: NearbyStatus = active && state.status === 'idle' ? 'loading' : state.status;
  return { ...state, status, retry, loadMore };
}
