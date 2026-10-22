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

export function createMemory(req: MemoryCreateRequest, idempotencyKey: string): Promise<{ memoryId: number }> {
  return apiFetch<{ memoryId: number }>('/api/memories', { method: 'POST', body: req, idempotencyKey });
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

// 공개 기록을 최신순으로 준다. 비로그인도 볼 수 있다.
export function fetchMemoryFeed(page: number, size: number, signal?: AbortSignal): Promise<Page<MemoryFeedItem>> {
  return apiFetch<Page<MemoryFeedItem>>(`/api/memories/feed?page=${page}&size=${size}`, { signal });
}

// 다른 회원의 공개 기록만 준다. 회원이 없거나 탈퇴했으면 404다.
export function fetchMemberMemories(memberId: number, signal?: AbortSignal): Promise<MemberMemorySummary[]> {
  return apiFetch<MemberMemorySummary[]>(`/api/members/${memberId}/memories`, { signal });
}

export function likeMemory(memoryId: number): Promise<MemoryLikeResponse> {
  return apiFetch<MemoryLikeResponse>(`/api/memories/${memoryId}/like`, { method: 'PUT' });
}

export function unlikeMemory(memoryId: number): Promise<MemoryLikeResponse> {
  return apiFetch<MemoryLikeResponse>(`/api/memories/${memoryId}/like`, { method: 'DELETE' });
}

// 공개 기록만 신고할 수 있다. 같은 기록을 다시 신고하면 서버가 MEMORY_ALREADY_REPORTED로 거부한다.
export function reportMemory(memoryId: number, req: MemoryReportRequest): Promise<void> {
  return apiFetch<void>(`/api/memories/${memoryId}/reports`, { method: 'POST', body: req });
}
