export type MemberStatus = 'UNVERIFIED' | 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN';

export type MemberRole = 'USER' | 'ADMIN';

export type SelfAgeGroup = 'TWENTIES' | 'THIRTIES' | 'FORTIES' | 'FIFTIES' | 'SIXTIES_PLUS';

export type SelfGender = 'FEMALE' | 'MALE';

export interface SignupRequest {
  email: string;
  password: string;
  passwordConfirm: string;
  nickname: string;
}

export interface SignupResponse {
  memberId: number;
  status: MemberStatus;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  memberId: number;
  nickname: string;
  status: MemberStatus;
  role: MemberRole;
}

export interface Me {
  memberId: number;
  email: string;
  nickname: string;
  status: MemberStatus;
  role: MemberRole;
  // 회원이 스스로 밝히지 않았으면 null이다.
  selfAgeGroup: SelfAgeGroup | null;
  selfGender: SelfGender | null;
  identityVerified: boolean;
  trustLevel: number;
}

// 본인확인

export interface IdentityVerificationRequest {
  birthYear: number;
  gender: SelfGender;
  // 시연용 식별 문자열이다. 같은 값이면 서버가 같은 사람으로 본다.
  demoIdentityKey: string;
}

export interface IdentityVerificationResponse {
  identityVerified: boolean;
  // 성인 기준에 못 미치면 false이고 신뢰 단계는 0이다.
  adult: boolean;
  trustLevel: number;
}

// 내 정보 수정. 보내지 않은 필드는 서버가 그대로 두고, 자기 신고 값에 null을 보내면 지운다.
export interface MeUpdateRequest {
  nickname?: string;
  selfAgeGroup?: SelfAgeGroup | null;
  selfGender?: SelfGender | null;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
  newPasswordConfirm: string;
}

// 재설정 메일 링크의 토큰으로 새 비밀번호를 정한다.
export interface PasswordResetConfirmRequest {
  token: string;
  newPassword: string;
  newPasswordConfirm: string;
}

// 신뢰 단계

export interface TrustProgress {
  // 받은 동행 후기가 없으면 rejoinRate.current는 null이다.
  current: number | null;
  required: number;
}

export interface NextTrustLevel {
  level: number;
  completedCompanions: TrustProgress;
  rejoinRate: TrustProgress;
  noRecentSanction: boolean;
}

export interface MyTrust {
  trustLevel: number;
  identityVerified: boolean;
  // 단계 2 회원의 응답에는 이 필드가 없다.
  nextLevel?: NextTrustLevel;
}

// 회원 프로필. 비로그인 요청에는 memberId와 nickname만 오고, 나머지 필드는 응답에서 빠진다.
export interface CompanionReviewSummary {
  // 받은 동행 후기가 없으면 null이다.
  rejoinRate: number | null;
  topTags: string[];
}

export interface MemberProfile {
  memberId: number;
  nickname: string;
  ageGroup?: SelfAgeGroup | null;
  ageGroupVerified?: boolean;
  gender?: SelfGender | null;
  genderVerified?: boolean;
  trustLevel?: number;
  completedCompanions?: number;
  companionReviewSummary?: CompanionReviewSummary;
}
