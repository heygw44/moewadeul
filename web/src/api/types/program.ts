import type { Page } from './page';

export type ProgramStatus = 'UPCOMING' | 'OPEN' | 'CLOSED' | 'CANCELED';

export interface ProgramSummary {
  programId: number;
  title: string;
  locationText: string;
  // 지도 장소와 연결하지 않은 행사는 null이다.
  spotId: number | null;
  startAt: string;
  endAt: string;
  applyOpenAt: string;
  applyCloseAt: string;
  capacity: number;
  remainingSeats: number;
  fee: number;
  overnight: boolean;
  status: ProgramStatus;
}

// 목록 응답에는 가장 가까운 신청 오픈 시각이 더 들어 있다. 신청이 열릴 행사가 없으면 필드가 없다.
export interface ProgramPage extends Page<ProgramSummary> {
  nextApplyOpenAt?: string;
}

export type ProgramApplicationStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'CANCELED' | 'EXPIRED';

export interface ProgramMyApplication {
  applicationId: number;
  status: ProgramApplicationStatus;
  paymentDueAt: string | null;
}

export interface ProgramDetail extends ProgramSummary {
  description: string;
  paymentDeadlineMinutes: number;
  // 비로그인이거나 신청이 없으면 필드가 없다.
  myApplication?: ProgramMyApplication;
}

export interface ProgramApplyResponse {
  applicationId: number;
  status: ProgramApplicationStatus;
  paymentDueAt: string;
  amount: number;
}

export interface ProgramPayResponse {
  applicationId: number;
  status: ProgramApplicationStatus;
  paidAt: string;
}

export interface ProgramCancelResponse {
  status: ProgramApplicationStatus;
  refunded: boolean;
}

export type ProgramCancelReason = 'USER' | 'EXPIRED' | 'SANCTIONED' | 'PROGRAM_CANCELED' | 'WITHDRAWN';

export interface MyProgramApplicationItem {
  applicationId: number;
  status: ProgramApplicationStatus;
  paymentDueAt: string | null;
  confirmedAt: string | null;
  canceledAt: string | null;
  cancelReason: ProgramCancelReason | null;
  createdAt: string;
  program: {
    programId: number;
    title: string;
    locationText: string;
    startAt: string;
    endAt: string;
    fee: number;
    status: ProgramStatus;
  };
}
