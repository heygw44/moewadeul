import { apiFetch } from './client';
import type { NearbySpot, Page, SpotAreaQuery, SpotAreaResponse, SpotDetail, SpotNearbyQuery } from './types';

// 지도 화면 영역 안의 장소를 받는다. 장소가 너무 많으면 서버가 markers를 비우고 clusters로 묶어서 준다.
export function fetchSpotsInArea(query: SpotAreaQuery, signal?: AbortSignal): Promise<SpotAreaResponse> {
  return apiFetch<SpotAreaResponse>('/api/spots', {
    query: {
      swLat: query.swLat,
      swLng: query.swLng,
      neLat: query.neLat,
      neLng: query.neLng,
      zoom: query.zoom,
      types: query.types,
      hasWater: query.hasWater,
      hasToilet: query.hasToilet,
      excludeWarning: query.excludeWarning,
      hideClosed: query.hideClosed,
    },
    signal,
  });
}

// 기준 좌표에서 반경 안의 장소를 가까운 순서로 받는다. 서버는 휴장 중인 장소도 빼지 않는다.
export function fetchSpotsNearby(query: SpotNearbyQuery, signal?: AbortSignal): Promise<Page<NearbySpot>> {
  return apiFetch<Page<NearbySpot>>('/api/spots/nearby', {
    query: { lat: query.lat, lng: query.lng, radiusKm: query.radiusKm },
    signal,
  });
}

export function fetchSpotDetail(spotId: number, signal?: AbortSignal): Promise<SpotDetail> {
  return apiFetch<SpotDetail>(`/api/spots/${spotId}`, { signal });
}
