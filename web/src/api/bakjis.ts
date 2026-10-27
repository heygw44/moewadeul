import { apiFetch } from './client';
import type {
  BakjiConfirmationResponse,
  BakjiCreateRequest,
  BakjiProblemReportRequest,
  BakjiSubmissionResponse,
  BakjiUpdateRequest,
} from './types';

export function createBakji(body: BakjiCreateRequest, signal?: AbortSignal): Promise<BakjiSubmissionResponse> {
  return apiFetch<BakjiSubmissionResponse>('/api/bakjis', { method: 'POST', body, signal });
}

export function updateBakji(
  spotId: number,
  body: BakjiUpdateRequest,
  signal?: AbortSignal,
): Promise<BakjiSubmissionResponse> {
  return apiFetch<BakjiSubmissionResponse>(`/api/bakjis/${spotId}`, { method: 'PATCH', body, signal });
}

export function deleteBakji(spotId: number): Promise<void> {
  return apiFetch<void>(`/api/bakjis/${spotId}`, { method: 'DELETE' });
}

export function confirmBakji(spotId: number, signal?: AbortSignal): Promise<BakjiConfirmationResponse> {
  return apiFetch<BakjiConfirmationResponse>(`/api/bakjis/${spotId}/confirmations`, { method: 'POST', signal });
}

export function reportBakji(spotId: number, body: BakjiProblemReportRequest, signal?: AbortSignal): Promise<void> {
  return apiFetch<void>(`/api/bakjis/${spotId}/reports`, { method: 'POST', body, signal });
}
