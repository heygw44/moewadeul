import type { SelfAgeGroup, SelfGender } from './member';
import type { SpotType } from './spot';

export type BasecampStatus = 'RECRUITING' | 'CLOSED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELED';

// 요청자와 이 베이스캠프의 관계. 비로그인 요청자는 늘 NONE이다.
export type BasecampRelation = 'NONE' | 'APPLICANT' | 'MEMBER' | 'LEADER';

// 합류 신청을 못 하는 이유. 서버가 이 순서로 모두 알려 준다.
export type JoinUnmetReason =
  | 'TRUST_LEVEL'
  | 'AGE_GROUP'
  | 'GENDER'
  | 'DATE_CONFLICT'
  | 'ALREADY_JOINED'
  | 'REAPPLY_NOT_ALLOWED';

// 합류 조건. 연령대 범위는 20, 30, 40, 50, 60(60대 이상) 중 하나씩이고, 조건이 없는 항목은 null이다.
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
  // 로그인한 요청자에게만 온다. 비로그인이면 두 필드가 응답에서 빠진다.
  canApply?: boolean;
  unmetReasons?: JoinUnmetReason[];
}

// 지도 영역으로 찾는다. 날짜는 한국 날짜(YYYY-MM-DD)이고 양 끝을 포함한다.
export interface BasecampSearchQuery {
  swLat: number;
  swLng: number;
  neLat: number;
  neLng: number;
  fromDate?: string;
  toDate?: string;
  hasVacancy?: boolean;
  // 비우면 서버가 모든 유형을 준다.
  types?: SpotType[];
  // 박 수(1~3). 비우면 모든 박 수
  nights?: number[];
  // 합류 조건의 최소 신뢰 단계가 이 값 이하인 것만 받는다.
  maxTrustLevel?: number;
  noAgeLimit?: boolean;
  noSameGenderOnly?: boolean;
  // 0부터 시작한다.
  page?: number;
  size?: number;
}

export interface BasecampDetailSpot {
  spotId: number;
  name: string;
  type: SpotType;
}

export interface BasecampLeader {
  memberId: number;
  nickname: string;
  // 로그인한 요청자에게만 온다.
  trustLevel?: number;
}

// 비로그인 응답은 memberId, nickname, role만 오고, 나머지 프로필 필드는 응답에서 빠진다.
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
  spot: BasecampDetailSpot;
  startDate: string;
  endDate: string;
  capacity: number;
  headcount: number;
  status: BasecampStatus;
  joinCondition: JoinCondition;
  leader: BasecampLeader;
  members: BasecampMember[];
  myRelation: BasecampRelation;
  // 확정된 베이스캠프의 멤버에게만 오고, 등록된 값이 없으면 빠진다.
  contactInfo?: string;
  safetyNotice: string;
  // 로그인한 요청자가 모집 중인 베이스캠프를 볼 때만 온다.
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
  // 조건이 없으면 보내지 않는다.
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

// 캠프 리더 관리

export type BasecampApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELED' | 'EXPIRED';

// 신청자 프로필은 상세의 멤버 항목과 같은 규칙이라 프로필 필드가 빠질 수 있다.
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

// 승인하면 서버가 베이스캠프의 인원과 상태를 돌려준다.
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

// 내 베이스캠프

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
