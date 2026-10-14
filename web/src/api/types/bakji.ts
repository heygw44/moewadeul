import type { ParkWarning } from './spot';

export type SignalLevel = 'NONE' | 'WEAK' | 'GOOD';

export interface BakjiReporter {
  memberId: number;
  // 제보자가 탈퇴했으면 서버가 익명화한 닉네임을 준다.
  nickname: string;
}

export interface BakjiDetail {
  description: string | null;
  hasWater: boolean;
  hasToilet: boolean;
  // 제보자가 통신 상태를 남기지 않았으면 null이다.
  signalLevel: SignalLevel | null;
  confirmationCount: number;
  reporter: BakjiReporter;
}

export type GroundType = 'SOIL' | 'GRASS' | 'GRAVEL' | 'SAND' | 'ROCK' | 'DECK';

// 모르는 선택 항목(description, signalLevel, groundType)은 필드를 빼고 보낸다.
export interface BakjiCreateRequest {
  name: string;
  lat: number;
  lng: number;
  description?: string;
  hasWater: boolean;
  hasToilet: boolean;
  signalLevel?: SignalLevel;
  groundType?: GroundType;
}

export interface DuplicateCandidate {
  spotId: number;
  name: string;
  distanceM: number;
}

export interface BakjiSubmissionResponse {
  spotId: number;
  parkWarning: ParkWarning;
  guide: string;
  duplicateCandidates: DuplicateCandidate[];
}

export interface BakjiConfirmationResponse {
  confirmationCount: number;
}

export type BakjiReportReason = 'ILLEGAL_AREA' | 'CLOSED' | 'FALSE_INFO';

export interface BakjiProblemReportRequest {
  reason: BakjiReportReason;
  content?: string;
}
