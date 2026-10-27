import type { SelfAgeGroup, SelfGender } from './member';
import type { SpotType } from './spot';

export type BasecampStatus = 'RECRUITING' | 'CLOSED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELED';

export type BasecampRelation = 'NONE' | 'APPLICANT' | 'MEMBER' | 'LEADER';

export type JoinUnmetReason =
  'TRUST_LEVEL' | 'AGE_GROUP' | 'GENDER' | 'DATE_CONFLICT' | 'ALREADY_JOINED' | 'REAPPLY_NOT_ALLOWED';

export interface JoinCondition {
  minTrustLevel: number | null;
  ageGroupMin: number | null;
  ageGroupMax: number | null;
  sameGenderOnly: boolean;
}

export interface BasecampSpotSummary {
  spotId: number;
  name: string;
  type: SpotType;
  lat: number;
  lng: number;
}

export interface BasecampSearchItem {
  basecampId: number;
  title: string;
  spot: BasecampSpotSummary;
  startDate: string;
  endDate: string;
  capacity: number;
  headcount: number;
  status: BasecampStatus;
  joinCondition: JoinCondition;
  canApply?: boolean;
  unmetReasons?: JoinUnmetReason[];
}

export interface BasecampSearchQuery {
  swLat: number;
  swLng: number;
  neLat: number;
  neLng: number;
  fromDate?: string;
  toDate?: string;
  hasVacancy?: boolean;
  types?: SpotType[];
  nights?: number[];
  maxTrustLevel?: number;
  noAgeLimit?: boolean;
  noSameGenderOnly?: boolean;
  page?: number;
  size?: number;
}

export interface BasecampDetailSpot {
  spotId: number;
  name: string;
  type: SpotType;
  lat: number;
  lng: number;
}

export interface BasecampLeader {
  memberId: number;
  nickname: string;
  trustLevel?: number;
}

export interface BasecampMember {
  memberId: number;
  nickname: string;
  role: 'LEADER' | 'MEMBER';
  ageGroup?: SelfAgeGroup | null;
  ageGroupVerified?: boolean;
  gender?: SelfGender | null;
  genderVerified?: boolean;
  trustLevel?: number;
  completedCompanions?: number;
}

export interface BasecampDetail {
  basecampId: number;
  title: string;
  description: string;
  spot?: BasecampDetailSpot;
  startDate: string;
  endDate: string;
  capacity: number;
  headcount: number;
  status: BasecampStatus;
  joinCondition: JoinCondition;
  leader: BasecampLeader;
  members: BasecampMember[];
  myRelation: BasecampRelation;
  contactInfo?: string;
  safetyNotice: string;
  canApply?: boolean;
  unmetReasons?: JoinUnmetReason[];
}

export interface BasecampOpenRequest {
  spotId: number;
  title: string;
  description: string;
  startDate: string;
  endDate: string;
  capacity: number;
  joinCondition?: JoinCondition;
}

export interface BasecampOpenResponse {
  basecampId: number;
  status: BasecampStatus;
}

export interface BasecampApplyResponse {
  applicationId: number;
  status: 'PENDING';
}

export type BasecampApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELED' | 'EXPIRED';

export interface BasecampApplicant {
  memberId: number;
  nickname: string;
  ageGroup?: SelfAgeGroup | null;
  ageGroupVerified?: boolean;
  gender?: SelfGender | null;
  genderVerified?: boolean;
  trustLevel?: number;
  completedCompanions?: number;
}

export interface BasecampApplicationItem {
  applicationId: number;
  status: BasecampApplicationStatus;
  message: string | null;
  appliedAt: string;
  applicant: BasecampApplicant;
}

export interface BasecampApproveResponse {
  headcount: number;
  status: BasecampStatus;
}

export interface BasecampRejectResponse {
  applicationId: number;
  status: BasecampApplicationStatus;
}

export interface BasecampStatusResponse {
  basecampId: number;
  status: BasecampStatus;
}

export type KickReason = 'NO_CONTACT' | 'CONDITION_MISMATCH' | 'INAPPROPRIATE_BEHAVIOR' | 'OTHER';

export type MyBasecampRelation = 'LEADER' | 'MEMBER' | 'APPLICANT';

export interface MyBasecampsQuery {
  relation?: MyBasecampRelation;
  status?: BasecampStatus;
  page?: number;
  size?: number;
}

export interface MyBasecampItem {
  basecampId: number;
  title: string;
  spot: BasecampDetailSpot;
  startDate: string;
  endDate: string;
  capacity: number;
  headcount: number;
  status: BasecampStatus;
  myRelation: MyBasecampRelation;
}
