import type { SelfAgeGroup, SelfGender } from '../../api/types';
import type { StatusTone } from '../../components/StatusText';
import type { IconName } from '../../components/iconPaths';

export const AGE_GROUP_LABELS: Record<SelfAgeGroup, string> = {
  TWENTIES: '20대',
  THIRTIES: '30대',
  FORTIES: '40대',
  FIFTIES: '50대',
  SIXTIES_PLUS: '60대 이상',
};

export const GENDER_LABELS: Record<SelfGender, string> = {
  FEMALE: '여성',
  MALE: '남성',
};

const TRUST_LEVEL_BADGES: ReadonlyArray<{ label: string; tone: StatusTone; icon?: IconName }> = [
  { label: '이메일 인증', tone: 'neutral' },
  { label: '본인확인', tone: 'info', icon: 'check' },
  { label: '신뢰 회원', tone: 'accent' },
];

export function trustLevelBadge(level: number): { label: string; tone: StatusTone; icon?: IconName } {
  return TRUST_LEVEL_BADGES[level] ?? TRUST_LEVEL_BADGES[0]!;
}

const TRUST_LEVEL_CAPTIONS = ['이메일 인증을 마친 회원', '본인확인을 마친 성인 회원', '동행 기록이 쌓인 신뢰 회원'];

export function trustLevelCaption(level: number): string {
  return TRUST_LEVEL_CAPTIONS[level] ?? TRUST_LEVEL_CAPTIONS[0]!;
}

export const POSITIVE_COMPANION_TAGS = ['ON_TIME', 'LEAVE_NO_TRACE', 'CONSIDERATE', 'WELL_PREPARED'] as const;
export const NEGATIVE_COMPANION_TAGS = ['LATE', 'NO_SHOW', 'LITTERING'] as const;

const COMPANION_TAG_LABELS: Record<string, string> = {
  ON_TIME: '시간 약속',
  LEAVE_NO_TRACE: '흔적 남기지 않기 실천',
  CONSIDERATE: '배려',
  WELL_PREPARED: '준비성',
  LATE: '지각',
  NO_SHOW: '무단 불참',
  LITTERING: '쓰레기 방치',
};

export function companionTagLabel(tag: string): string {
  return COMPANION_TAG_LABELS[tag] ?? tag;
}
