import type { CommunityReportReason, CommunityReportRequest } from './community';

// 추억 기록. 공개 범위는 PRIVATE(작성자만), PUBLIC(누구나)이다.
export type MemoryVisibility = 'PRIVATE' | 'PUBLIC';

// 신고와 관리자 조치에 따른 상태. 검토 대기·숨김 기록은 작성자만 본다.
export type MemoryStatus = 'ACTIVE' | 'PENDING_REVIEW' | 'HIDDEN';

// 사유 값은 커뮤니티 신고와 같다.
export type MemoryReportRequest = CommunityReportRequest;

export type AdminMemoryStatus = 'PENDING_REVIEW' | 'HIDDEN';

// 관리자 검토 목록 항목. 관리자에게도 정확한 좌표는 주지 않는다.
export interface AdminMemorySummary {
  memoryId: number;
  author: MemoryAuthor;
  approxLat: number;
  approxLng: number;
  visitedDate: string;
  nights: number;
  // 글이 없으면 서버가 뺀다.
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
  // 연결한 장소가 지도에 보이지 않거나 연결하지 않았으면 서버가 뺀다.
  spot?: MemoryLinkedSpot;
  // 정확한 좌표는 작성자에게만 준다.
  lat?: number;
  lng?: number;
  // 작성자가 아닌 사람에게는 소수 둘째 자리(약 1km)로 줄인 좌표만 준다.
  approxLat?: number;
  approxLng?: number;
  visitedDate: string;
  nights: number;
  // 글이 없으면 서버가 뺀다.
  content?: string;
  visibility: MemoryVisibility;
  // 작성자에게만 준다. 다른 사람은 정상(ACTIVE) 기록만 볼 수 있어서 서버가 뺀다.
  status?: MemoryStatus;
  images: MemoryImage[];
  likeCount: number;
  // 비로그인 요청에는 서버가 뺀다.
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

// 공개 피드 항목. 좌표는 약 1km로 줄인 값이고, 공개 기록만 온다.
export interface MemoryFeedItem {
  memoryId: number;
  author: MemoryAuthor;
  spot?: MemoryLinkedSpot;
  approxLat: number;
  approxLng: number;
  visitedDate: string;
  nights: number;
  excerpt?: string;
  images: MemoryImage[];
  likeCount: number;
  likedByMe?: boolean;
  createdAt: string;
}

// 다른 회원의 기록 항목. 공개 기록만 오고 좌표는 약 1km로 줄인 값이다.
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

// 보내지 않은 필드는 서버가 그대로 둔다. spotId와 content는 null로 보내면 연결을 끊거나 글을 지운다.
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
