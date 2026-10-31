import { apiFetch } from './client';
import type {
  CommunityComment,
  CommunityCommentCreateRequest,
  CommunityImageUpload,
  CommunityImageUploadRequest,
  CommunityLikeResponse,
  CommunityPostCreateRequest,
  CommunityPostDetail,
  CommunityPostSummary,
  CommunityPostUpdateRequest,
  CommunityReportRequest,
  CommunitySearchType,
  Page,
} from './types';

const COMMUNITY_POST_PAGE_SIZE = 20;
const COMMUNITY_COMMENT_PAGE_SIZE = 20;

export interface CommunityPostQuery {
  spotId?: number;
  popular?: boolean;
  searchType?: CommunitySearchType;
  keyword?: string;
}

export function listPosts(
  filter: CommunityPostQuery,
  page: number,
  signal?: AbortSignal,
): Promise<Page<CommunityPostSummary>> {
  return apiFetch<Page<CommunityPostSummary>>('/api/community/posts', {
    query: {
      spotId: filter.spotId,
      popular: filter.popular || undefined,
      searchType: filter.keyword ? filter.searchType : undefined,
      keyword: filter.keyword,
      page,
      size: COMMUNITY_POST_PAGE_SIZE,
    },
    signal,
  });
}

export function getPost(postId: number, signal?: AbortSignal): Promise<CommunityPostDetail> {
  return apiFetch<CommunityPostDetail>(`/api/community/posts/${postId}`, { signal });
}

export function createPost(
  req: CommunityPostCreateRequest,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<{ postId: number }> {
  return apiFetch<{ postId: number }>('/api/community/posts', { method: 'POST', body: req, idempotencyKey, signal });
}

export function updatePost(
  postId: number,
  req: CommunityPostUpdateRequest,
  signal?: AbortSignal,
): Promise<CommunityPostDetail> {
  return apiFetch<CommunityPostDetail>(`/api/community/posts/${postId}`, { method: 'PATCH', body: req, signal });
}

export function deletePost(postId: number): Promise<void> {
  return apiFetch<void>(`/api/community/posts/${postId}`, { method: 'DELETE' });
}

export function listComments(postId: number, page: number, signal?: AbortSignal): Promise<Page<CommunityComment>> {
  return apiFetch<Page<CommunityComment>>(`/api/community/posts/${postId}/comments`, {
    query: { page, size: COMMUNITY_COMMENT_PAGE_SIZE },
    signal,
  });
}

export function createComment(
  postId: number,
  req: CommunityCommentCreateRequest,
  idempotencyKey: string,
): Promise<{ commentId: number }> {
  return apiFetch<{ commentId: number }>(`/api/community/posts/${postId}/comments`, {
    method: 'POST',
    body: req,
    idempotencyKey,
  });
}

export function updateComment(commentId: number, req: { content: string }): Promise<CommunityComment> {
  return apiFetch<CommunityComment>(`/api/community/comments/${commentId}`, { method: 'PATCH', body: req });
}

export function deleteComment(commentId: number): Promise<void> {
  return apiFetch<void>(`/api/community/comments/${commentId}`, { method: 'DELETE' });
}

export function likePost(postId: number): Promise<CommunityLikeResponse> {
  return apiFetch<CommunityLikeResponse>(`/api/community/posts/${postId}/like`, { method: 'PUT' });
}

export function unlikePost(postId: number): Promise<CommunityLikeResponse> {
  return apiFetch<CommunityLikeResponse>(`/api/community/posts/${postId}/like`, { method: 'DELETE' });
}

export function reportPost(postId: number, req: CommunityReportRequest): Promise<void> {
  return apiFetch<void>(`/api/community/posts/${postId}/reports`, { method: 'POST', body: req });
}

export function reportComment(commentId: number, req: CommunityReportRequest): Promise<void> {
  return apiFetch<void>(`/api/community/comments/${commentId}/reports`, { method: 'POST', body: req });
}

export function requestImageUpload(req: CommunityImageUploadRequest): Promise<CommunityImageUpload> {
  return apiFetch<CommunityImageUpload>('/api/community/images', { method: 'POST', body: req });
}
