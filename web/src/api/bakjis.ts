import { apiFetch } from './client';
import type {
  BakjiConfirmationResponse,
  BakjiCreateRequest,
  BakjiProblemReportRequest,
  BakjiSubmissionResponse,
  BakjiUpdateRequest,
} from './types';

// 제보는 이메일 인증을 마친 회원만 할 수 있다. 등록은 중복 후보가 있어도 이미 끝난 상태로 응답한다.
export function createBakji(body: BakjiCreateRequest, signal?: AbortSignal): Promise<BakjiSubmissionResponse> {
  return apiFetch<BakjiSubmissionResponse>('/api/bakjis', { method: 'POST', body, signal });
}

// 제보한 회원만 고칠 수 있다. 응답은 제보 결과와 같은 모양이고, 좌표를 바꿨으면 서버가 경계 경고와 중복 후보를 새 좌표로 다시 계산한다.
export function updateBakji(
  spotId: number,
  body: BakjiUpdateRequest,
  signal?: AbortSignal,
): Promise<BakjiSubmissionResponse> {
  return apiFetch<BakjiSubmissionResponse>(`/api/bakjis/${spotId}`, { method: 'PATCH', body, signal });
}

// 제보한 회원만 지울 수 있다. 서버는 본문 없이 204만 주고, 지운 박지는 지도와 상세에서 사라진다.
export function deleteBakji(spotId: number): Promise<void> {
  return apiFetch<void>(`/api/bakjis/${spotId}`, { method: 'DELETE' });
}

export function confirmBakji(spotId: number, signal?: AbortSignal): Promise<BakjiConfirmationResponse> {
  return apiFetch<BakjiConfirmationResponse>(`/api/bakjis/${spotId}/confirmations`, { method: 'POST', signal });
}

// 서버는 본문 없이 201만 준다.
export function reportBakji(spotId: number, body: BakjiProblemReportRequest, signal?: AbortSignal): Promise<void> {
  return apiFetch<void>(`/api/bakjis/${spotId}/reports`, { method: 'POST', body, signal });
}
