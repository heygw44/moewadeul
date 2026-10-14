// 글 검색 대상. 댓글은 검색하지 않는다.
export type CommunitySearchType = 'TITLE_CONTENT' | 'TITLE' | 'CONTENT' | 'AUTHOR';

export type CommunityReportReason = 'SPAM' | 'ABUSE' | 'ILLEGAL_CAMPING' | 'PRIVACY' | 'MONEY_SCAM' | 'OTHER';

export interface CommunityAuthor {
  memberId: number;
  nickname: string;
}

// 연결한 장소가 ACTIVE일 때만 서버가 준다.
export interface CommunityPostSpot {
  spotId: number;
  name: string;
}

interface CommunityPostBase {
  postId: number;
  title: string;
  author: CommunityAuthor;
  spot?: CommunityPostSpot;
  likeCount: number;
  commentCount: number;
  // 작성자가 아닌 사람이 상세를 연 횟수
  viewCount: number;
  createdAt: string;
}

export interface CommunityPostSummary extends CommunityPostBase {
  // 본문 앞 100자
  excerpt: string;
  thumbnailUrl?: string;
  imageCount: number;
}

export interface CommunityPostImage {
  imageId: number;
  // 서버가 응답마다 새로 발급하는 사전 서명 URL이고 1시간 동안 쓸 수 있다.
  url: string;
}

export interface CommunityPostDetail extends CommunityPostBase {
  content: string;
  images: CommunityPostImage[];
  updatedAt: string;
  // 로그인한 요청에만 있다.
  likedByMe?: boolean;
}

export interface CommunityPostCreateRequest {
  title: string;
  content: string;
  spotId?: number;
  imageIds: number[];
}

// 보내지 않은 필드는 그대로 두고, spotId: null은 장소 연결을 끊는다.
export interface CommunityPostUpdateRequest {
  title?: string;
  content?: string;
  spotId?: number | null;
  imageIds?: number[];
}

// 삭제된 댓글은 보이는 답글이 있을 때만 deleted: true로 오고, author와 content는 빠진다.
export interface CommunityComment {
  commentId: number;
  author?: CommunityAuthor;
  content?: string;
  deleted: boolean;
  createdAt?: string;
  updatedAt?: string;
  replies: CommunityComment[];
}

export interface CommunityCommentCreateRequest {
  content: string;
  parentId?: number;
}

export interface CommunityLikeResponse {
  liked: boolean;
  likeCount: number;
}

export interface CommunityReportRequest {
  reason: CommunityReportReason;
  content?: string;
}
