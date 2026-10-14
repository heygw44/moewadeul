export interface SpotReviewAuthor {
  memberId: number;
  nickname: string;
}

export interface SpotReview {
  reviewId: number;
  author: SpotReviewAuthor;
  // 한국 날짜(YYYY-MM-DD)
  visitedDate: string;
  rating: number;
  content: string;
  // 후기 안 순서의 이미지. url은 서버가 발급한 사전 서명 조회 URL이다. 없으면 빈 배열이다.
  images: { imageId: number; url: string }[];
  createdAt: string;
}

export interface SpotReviewCreateRequest {
  visitedDate: string;
  rating: number;
  content: string;
  // 붙일 이미지 ID. 최대 3개이고 배열 순서가 후기 안 순서다.
  imageIds?: number[];
}

export interface SpotReviewUpdateRequest {
  rating: number;
  content: string;
  // 보내면 이미지 목록을 통째로 바꾸고, 보내지 않으면 그대로 둔다.
  imageIds?: number[];
}
