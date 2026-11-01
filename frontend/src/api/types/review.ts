export interface SpotReviewAuthor {
  memberId: number;
  nickname: string;
}

export interface SpotReview {
  reviewId: number;
  author: SpotReviewAuthor;
  visitedDate: string;
  rating: number;
  content: string;
  images: { imageId: number; url: string }[];
  createdAt: string;
}

export interface SpotReviewCreateRequest {
  visitedDate: string;
  rating: number;
  content: string;
  imageIds?: number[];
}

export interface SpotReviewUpdateRequest {
  rating: number;
  content: string;
  imageIds?: number[];
}
