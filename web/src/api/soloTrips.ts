import { apiFetch } from './client';
import type { SoloTrip, SoloTripCreateRequest } from './types';

export function createSoloTrip(req: SoloTripCreateRequest, idempotencyKey: string): Promise<{ soloTripId: number }> {
  return apiFetch<{ soloTripId: number }>('/api/solo-trips', { method: 'POST', body: req, idempotencyKey });
}

export function fetchMySoloTrips(signal?: AbortSignal): Promise<SoloTrip[]> {
  return apiFetch<SoloTrip[]>('/api/me/solo-trips', { signal });
}

export function deleteSoloTrip(soloTripId: number): Promise<void> {
  return apiFetch<void>(`/api/solo-trips/${soloTripId}`, { method: 'DELETE' });
}
