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
  selfAgeGroup: SelfAgeGroup | null;
  selfGender: SelfGender | null;
  identityVerified: boolean;
  trustLevel: number;
}

export interface IdentityVerificationRequest {
  birthYear: number;
  gender: SelfGender;
  demoIdentityKey: string;
}

export interface IdentityVerificationResponse {
  identityVerified: boolean;
  adult: boolean;
  trustLevel: number;
}

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

export interface PasswordResetConfirmRequest {
  token: string;
  newPassword: string;
  newPasswordConfirm: string;
}

export interface TrustProgress {
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
  nextLevel?: NextTrustLevel;
}

export interface CompanionReviewSummary {
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
