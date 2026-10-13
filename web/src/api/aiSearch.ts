import { apiFetch } from './client';
import type { AiInterpretResponse } from './types';

// 문장을 장소 검색 조건으로 바꾼다. 서버는 조건만 돌려주고, 장소는 이 조건으로 기존 조회 API를 다시 불러 받는다.
export function interpretSpotQuery(query: string, signal?: AbortSignal): Promise<AiInterpretResponse> {
  return apiFetch<AiInterpretResponse>('/api/spots/ai-interpret', { method: 'POST', body: { query }, signal });
}
