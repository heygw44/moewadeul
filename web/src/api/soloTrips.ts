import { apiFetch } from './client';
import type { SoloTrip, SoloTripCreateRequest } from './types';

export function createSoloTrip(req: SoloTripCreateRequest): Promise<{ soloTripId: number }> {
  return apiFetch<{ soloTripId: number }>('/api/solo-trips', { method: 'POST', body: req });
}

// 종료일이 지나지 않은 내 일정을 출발일 순서로 준다. 최대 3개라서 서버가 페이지 없이 배열로 준다.
export function fetchMySoloTrips(signal?: AbortSignal): Promise<SoloTrip[]> {
  return apiFetch<SoloTrip[]>('/api/me/solo-trips', { signal });
}

export function deleteSoloTrip(soloTripId: number): Promise<void> {
  return apiFetch<void>(`/api/solo-trips/${soloTripId}`, { method: 'DELETE' });
}
