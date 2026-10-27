import { apiFetch } from './client';
import type {
  ImageUpload,
  ImageUploadRequest,
  Page,
  SpotReview,
  SpotReviewCreateRequest,
  SpotReviewUpdateRequest,
} from './types';

export const SPOT_REVIEW_PAGE_SIZE = 10;

export function fetchSpotReviews(spotId: number, page: number, signal?: AbortSignal): Promise<Page<SpotReview>> {
  return apiFetch<Page<SpotReview>>(`/api/spots/${spotId}/reviews`, {
    query: { page, size: SPOT_REVIEW_PAGE_SIZE },
    signal,
  });
}

export function createSpotReview(spotId: number, req: SpotReviewCreateRequest): Promise<{ reviewId: number }> {
  return apiFetch<{ reviewId: number }>(`/api/spots/${spotId}/reviews`, { method: 'POST', body: req });
}

export function updateSpotReview(reviewId: number, req: SpotReviewUpdateRequest): Promise<SpotReview> {
  return apiFetch<SpotReview>(`/api/reviews/${reviewId}`, { method: 'PATCH', body: req });
}

export function deleteSpotReview(reviewId: number): Promise<void> {
  return apiFetch<void>(`/api/reviews/${reviewId}`, { method: 'DELETE' });
}

export function requestReviewImageUpload(req: ImageUploadRequest): Promise<ImageUpload> {
  return apiFetch<ImageUpload>('/api/reviews/images', { method: 'POST', body: req });
}
