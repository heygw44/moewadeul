import { apiFetch } from './client';
import type {
  ImageUpload,
  ImageUploadRequest,
  MemoryCreateRequest,
  MemoryDetail,
  MemorySummary,
  MemoryUpdateRequest,
} from './types';

export function createMemory(req: MemoryCreateRequest): Promise<{ memoryId: number }> {
  return apiFetch<{ memoryId: number }>('/api/memories', { method: 'POST', body: req });
}

// 남의 비공개 기록이면 서버가 없는 기록과 같은 404로 응답한다.
export function getMemory(memoryId: number, signal?: AbortSignal): Promise<MemoryDetail> {
  return apiFetch<MemoryDetail>(`/api/memories/${memoryId}`, { signal });
}

export function updateMemory(memoryId: number, req: MemoryUpdateRequest): Promise<MemoryDetail> {
  return apiFetch<MemoryDetail>(`/api/memories/${memoryId}`, { method: 'PATCH', body: req });
}

export function deleteMemory(memoryId: number): Promise<void> {
  return apiFetch<void>(`/api/memories/${memoryId}`, { method: 'DELETE' });
}

export function requestMemoryImageUpload(req: ImageUploadRequest): Promise<ImageUpload> {
  return apiFetch<ImageUpload>('/api/memories/images', { method: 'POST', body: req });
}

// 내 기록을 공개 범위와 상관없이 모두 방문일이 늦은 순서로 준다. 지도에 한 번에 그리려고 서버가 페이지 없이 준다.
export function fetchMyMemories(signal?: AbortSignal): Promise<MemorySummary[]> {
  return apiFetch<MemorySummary[]>('/api/me/memories', { signal });
}
