import type { CommunityReportReason, CommunityReportRequest } from './community';

export type MemoryVisibility = 'PRIVATE' | 'PUBLIC';

export type MemoryStatus = 'ACTIVE' | 'PENDING_REVIEW' | 'HIDDEN';

export type MemoryReportRequest = CommunityReportRequest;

export type AdminMemoryStatus = 'PENDING_REVIEW' | 'HIDDEN';

export interface AdminMemorySummary {
  memoryId: number;
  author: MemoryAuthor;
  approxLat: number;
  approxLng: number;
  visitedDate: string;
  nights: number;
  content?: string;
  images: MemoryImage[];
  status: AdminMemoryStatus;
  reportCount: number;
  reasonCounts: Record<CommunityReportReason, number>;
  recentReports: { reason: CommunityReportReason; content: string | null; createdAt: string }[];
  statusChangedAt: string;
}

export interface MemoryAuthor {
  memberId: number;
  nickname: string;
}

export interface MemoryLinkedSpot {
  spotId: number;
  name: string;
}

export interface MemoryImage {
  imageId: number;
  url: string;
}

export interface MemoryDetail {
  memoryId: number;
  author: MemoryAuthor;
  spot?: MemoryLinkedSpot;
  lat?: number;
  lng?: number;
  approxLat?: number;
  approxLng?: number;
  visitedDate: string;
  nights: number;
  content?: string;
  visibility: MemoryVisibility;
  status?: MemoryStatus;
  images: MemoryImage[];
  likeCount: number;
  likedByMe?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MemorySummary {
  memoryId: number;
  spot?: MemoryLinkedSpot;
  lat: number;
  lng: number;
  visitedDate: string;
  nights: number;
  excerpt?: string;
  visibility: MemoryVisibility;
  status: MemoryStatus;
  thumbnailUrl?: string;
  imageCount: number;
  createdAt: string;
}

export interface MemoryFeedItem {
  memoryId: number;
  author: MemoryAuthor;
  spot?: MemoryLinkedSpot;
  approxLat: number;
  approxLng: number;
  visitedDate: string;
  nights: number;
  excerpt?: string;
  thumbnailUrl?: string;
  imageCount: number;
  likeCount: number;
  likedByMe?: boolean;
  createdAt: string;
}

export interface MemberMemorySummary {
  memoryId: number;
  spot?: MemoryLinkedSpot;
  approxLat: number;
  approxLng: number;
  visitedDate: string;
  nights: number;
  excerpt?: string;
  thumbnailUrl?: string;
  imageCount: number;
  likeCount: number;
  createdAt: string;
}

export interface MemoryLikeResponse {
  liked: boolean;
  likeCount: number;
}

export interface MemoryCreateRequest {
  lat: number;
  lng: number;
  visitedDate: string;
  nights: number;
  content?: string;
  visibility?: MemoryVisibility;
  spotId?: number;
  imageIds?: number[];
}

export interface MemoryUpdateRequest {
  lat?: number;
  lng?: number;
  visitedDate?: string;
  nights?: number;
  content?: string | null;
  visibility?: MemoryVisibility;
  spotId?: number | null;
  imageIds?: number[];
}
