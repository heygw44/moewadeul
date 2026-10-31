import { apiFetch } from './client';
import type {
  ImageUpload,
  ImageUploadRequest,
  MemoryCreateRequest,
  MemberMemorySummary,
  MemoryDetail,
  MemoryFeedItem,
  MemoryLikeResponse,
  MemoryReportRequest,
  MemorySummary,
  MemoryUpdateRequest,
  Page,
} from './types';

export function createMemory(
  req: MemoryCreateRequest,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<{ memoryId: number }> {
  return apiFetch<{ memoryId: number }>('/api/memories', { method: 'POST', body: req, idempotencyKey, signal });
}

export function getMemory(memoryId: number, signal?: AbortSignal): Promise<MemoryDetail> {
  return apiFetch<MemoryDetail>(`/api/memories/${memoryId}`, { signal });
}

export function updateMemory(memoryId: number, req: MemoryUpdateRequest, signal?: AbortSignal): Promise<MemoryDetail> {
  return apiFetch<MemoryDetail>(`/api/memories/${memoryId}`, { method: 'PATCH', body: req, signal });
}

export function deleteMemory(memoryId: number): Promise<void> {
  return apiFetch<void>(`/api/memories/${memoryId}`, { method: 'DELETE' });
}

export function requestMemoryImageUpload(req: ImageUploadRequest): Promise<ImageUpload> {
  return apiFetch<ImageUpload>('/api/memories/images', { method: 'POST', body: req });
}

export function fetchMyMemories(signal?: AbortSignal): Promise<MemorySummary[]> {
  return apiFetch<MemorySummary[]>('/api/me/memories', { signal });
}

export function fetchMemoryFeed(page: number, size: number, signal?: AbortSignal): Promise<Page<MemoryFeedItem>> {
  return apiFetch<Page<MemoryFeedItem>>(`/api/memories/feed?page=${page}&size=${size}`, { signal });
}

export function fetchMemberMemories(memberId: number, signal?: AbortSignal): Promise<MemberMemorySummary[]> {
  return apiFetch<MemberMemorySummary[]>(`/api/members/${memberId}/memories`, { signal });
}

export function likeMemory(memoryId: number): Promise<MemoryLikeResponse> {
  return apiFetch<MemoryLikeResponse>(`/api/memories/${memoryId}/like`, { method: 'PUT' });
}

export function unlikeMemory(memoryId: number): Promise<MemoryLikeResponse> {
  return apiFetch<MemoryLikeResponse>(`/api/memories/${memoryId}/like`, { method: 'DELETE' });
}

export function reportMemory(memoryId: number, req: MemoryReportRequest): Promise<void> {
  return apiFetch<void>(`/api/memories/${memoryId}/reports`, { method: 'POST', body: req });
}
