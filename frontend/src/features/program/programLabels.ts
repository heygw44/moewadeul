import type { ProgramListStatus } from '../../api/programs';
import type { ProgramApplicationStatus, ProgramCancelReason, ProgramStatus } from '../../api/types';
import type { StatusTone } from '../../components/StatusText';

export const PROGRAM_STATUS_META: Record<ProgramStatus, { label: string; tone: StatusTone }> = {
  UPCOMING: { label: '신청 예정', tone: 'info' },
  OPEN: { label: '신청 받는 중', tone: 'accent' },
  CLOSED: { label: '마감', tone: 'neutral' },
  CANCELED: { label: '행사 취소', tone: 'neutral' },
};

export const APPLICATION_STATUS_META: Record<ProgramApplicationStatus, { label: string; tone: StatusTone }> = {
  PENDING_PAYMENT: { label: '결제 대기', tone: 'warning' },
  CONFIRMED: { label: '확정', tone: 'info' },
  CANCELED: { label: '취소', tone: 'neutral' },
  EXPIRED: { label: '만료', tone: 'neutral' },
};

export const CANCEL_REASON_LABELS: Record<ProgramCancelReason, string> = {
  USER: '직접 취소',
  EXPIRED: '결제 기한 만료',
  SANCTIONED: '이용 정지',
  PROGRAM_CANCELED: '행사 취소',
  WITHDRAWN: '회원 탈퇴',
};

export const PROGRAM_FILTERS: ReadonlyArray<{ value: ProgramListStatus | 'ALL'; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'UPCOMING', label: '신청 예정' },
  { value: 'OPEN', label: '신청 받는 중' },
  { value: 'CLOSED', label: '마감' },
];

export const APPLICATION_FILTERS: ReadonlyArray<{ value: ProgramApplicationStatus | 'ALL'; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'PENDING_PAYMENT', label: '결제 대기' },
  { value: 'CONFIRMED', label: '확정' },
  { value: 'CANCELED', label: '취소' },
  { value: 'EXPIRED', label: '만료' },
];

export function feeText(fee: number): string {
  return fee === 0 ? '무료' : `${fee.toLocaleString('ko-KR')}원`;
}

export function seatsText(remainingSeats: number, capacity: number): string {
  return `${remainingSeats}/${capacity}`;
}

export function countdownText(dueAtIso: string, nowMs: number, maxSeconds: number): string {
  const remaining = Math.floor((Date.parse(dueAtIso) - nowMs) / 1000);
  const seconds = Math.max(0, Math.min(maxSeconds, remaining));
  const minutes = Math.floor(seconds / 60);
  return `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}
