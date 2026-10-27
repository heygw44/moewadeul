import { apiFetch } from './client';
import type { CompanionReviewCreateRequest, Page, PendingCompanionReview, ReceivedCompanionReview } from './types';

export const RECEIVED_REVIEW_PAGE_SIZE = 10;

export function fetchPendingCompanionReviews(signal?: AbortSignal): Promise<PendingCompanionReview[]> {
  return apiFetch<PendingCompanionReview[]>('/api/me/companion-reviews/pending', { signal });
}

export function createCompanionReview(
  basecampId: number,
  request: CompanionReviewCreateRequest,
): Promise<{ reviewId: number }> {
  return apiFetch<{ reviewId: number }>(`/api/basecamps/${basecampId}/companion-reviews`, {
    method: 'POST',
    body: request,
  });
}

export function fetchReceivedCompanionReviews(
  page: number,
  signal?: AbortSignal,
): Promise<Page<ReceivedCompanionReview>> {
  return apiFetch<Page<ReceivedCompanionReview>>('/api/me/companion-reviews/received', {
    query: { page, size: RECEIVED_REVIEW_PAGE_SIZE },
    signal,
  });
}
