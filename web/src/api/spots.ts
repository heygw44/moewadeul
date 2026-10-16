import { apiFetch } from './client';
import type {
  NearbySpot,
  Page,
  SpotAreaQuery,
  SpotAreaResponse,
  SpotDetail,
  SpotNearbyQuery,
  SpotWeatherResponse,
  Weather,
} from './types';

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
      settings: query.settings,
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
    query: {
      lat: query.lat,
      lng: query.lng,
      radiusKm: query.radiusKm,
      types: query.types,
      settings: query.settings,
      hasWater: query.hasWater,
      hasToilet: query.hasToilet,
      excludeWarning: query.excludeWarning,
      hideClosed: query.hideClosed,
      page: query.page,
      size: query.size,
    },
    signal,
  });
}

export function fetchSpotDetail(spotId: number, signal?: AbortSignal): Promise<SpotDetail> {
  return apiFetch<SpotDetail>(`/api/spots/${spotId}`, { signal });
}

// 장소의 날씨와 오늘 출몰시각을 받는다. 기상청이 실패하면 서버가 200에 weather: null을 준다.
// 상세와 따로 받는 이유는 날씨가 외부 API를 기다려서 상세의 공원 경계·출처 같은 근거까지 늦게 뜨기 때문이다.
export async function fetchSpotWeather(spotId: number, signal?: AbortSignal): Promise<Weather | null> {
  const response = await apiFetch<SpotWeatherResponse>(`/api/spots/${spotId}/weather`, { signal });
  return response.weather;
}
