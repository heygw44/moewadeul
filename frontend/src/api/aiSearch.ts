import { apiFetch } from './client';
import type { AiInterpretResponse } from './types';

export function interpretSpotQuery(query: string, signal?: AbortSignal): Promise<AiInterpretResponse> {
  return apiFetch<AiInterpretResponse>('/api/spots/ai-interpret', { method: 'POST', body: { query }, signal });
}
