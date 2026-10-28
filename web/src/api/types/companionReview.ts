export interface PendingCompanionTarget {
  memberId: number;
  nickname: string;
}

export interface PendingCompanionReview {
  basecampId: number;
  basecampTitle: string;
  completedAt: string;
  deadline: string;
  targets: PendingCompanionTarget[];
}

export interface CompanionReviewCreateRequest {
  revieweeId: number;
  rejoinWanted: boolean;
  tags?: string[];
  comment?: string;
}

export interface RevealedReceivedCompanionReview {
  reviewId: number;
  basecampId: number;
  basecampTitle: string;
  reviewer: { memberId: number; nickname: string };
  rejoinWanted: boolean;
  tags: string[];
  comment: string | null;
  createdAt: string;
  revealed: true;
}

export interface SealedReceivedCompanionReview {
  basecampId: number;
  revealed: false;
}

export type ReceivedCompanionReview = RevealedReceivedCompanionReview | SealedReceivedCompanionReview;
