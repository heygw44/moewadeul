import type { ParkWarning } from './spot';

export type SignalLevel = 'NONE' | 'WEAK' | 'GOOD';

export interface BakjiReporter {
  memberId: number;
  nickname: string;
}

export interface BakjiDetail {
  description: string | null;
  hasWater: boolean;
  hasToilet: boolean;
  signalLevel: SignalLevel | null;
  confirmationCount: number;
  reporter: BakjiReporter;
  notice?: string;
}

export type GroundType = 'SOIL' | 'GRASS' | 'GRAVEL' | 'SAND' | 'ROCK' | 'DECK';

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

export interface BakjiUpdateRequest {
  name?: string;
  lat?: number;
  lng?: number;
  description?: string | null;
  hasWater?: boolean;
  hasToilet?: boolean;
  signalLevel?: SignalLevel | null;
  groundType?: GroundType | null;
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
  notice?: string;
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
