export type CommunitySearchType = 'TITLE_CONTENT' | 'TITLE' | 'CONTENT' | 'AUTHOR';

export type CommunityReportReason = 'SPAM' | 'ABUSE' | 'ILLEGAL_CAMPING' | 'PRIVACY' | 'MONEY_SCAM' | 'OTHER';

export interface CommunityAuthor {
  memberId: number;
  nickname: string;
}

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
  viewCount: number;
  createdAt: string;
}

export interface CommunityPostSummary extends CommunityPostBase {
  excerpt: string;
  thumbnailUrl?: string;
  imageCount: number;
}

export interface CommunityPostImage {
  imageId: number;
  url: string;
}

export interface CommunityPostDetail extends CommunityPostBase {
  content: string;
  images: CommunityPostImage[];
  updatedAt: string;
  likedByMe?: boolean;
}

export interface CommunityPostCreateRequest {
  title: string;
  content: string;
  spotId?: number;
  imageIds: number[];
}

export interface CommunityPostUpdateRequest {
  title?: string;
  content?: string;
  spotId?: number | null;
  imageIds?: number[];
}

export interface CommunityComment {
  commentId: number;
  author?: CommunityAuthor;
  replyTo?: CommunityAuthor;
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
