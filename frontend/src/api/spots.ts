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

export async function fetchSpotWeather(spotId: number, signal?: AbortSignal): Promise<Weather | null> {
  const response = await apiFetch<SpotWeatherResponse>(`/api/spots/${spotId}/weather`, { signal });
  return response.weather;
}
