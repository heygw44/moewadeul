import type { AdminAuditAction, AdminAuditTargetType, SyncJobType } from '../../api/types';
import { REPORT_STATUS_META, SANCTION_STATUS_LABELS, SANCTION_TYPE_LABELS } from '../report/reportLabels';

export const AUDIT_ACTION_LABELS: Record<AdminAuditAction, string> = {
  REPORT_START_REVIEW: '신고 검토 시작',
  REPORT_ACTION: '신고 조치',
  REPORT_DISMISS: '신고 기각',
  SANCTION_LIFT: '제재 해제',
  SPOT_HIDE: '박지 숨김',
  SPOT_RESTORE: '박지 복구',
  COMMUNITY_HIDE: '커뮤니티 숨김',
  COMMUNITY_RESTORE: '커뮤니티 복구',
  MEMORY_HIDE: '기록 숨김',
  MEMORY_RESTORE: '기록 복구',
  SYNC_JOB_RUN: '데이터 불러오기 실행',
  PROGRAM_CREATE: '행사 등록',
  PROGRAM_UPDATE: '행사 수정',
  PROGRAM_CANCEL: '행사 취소',
};

export const AUDIT_TARGET_LABELS: Record<AdminAuditTargetType, string> = {
  MEMBER_REPORT: '신고',
  SANCTION: '제재',
  SPOT: '박지',
  COMMUNITY_POST: '커뮤니티 글',
  COMMUNITY_COMMENT: '커뮤니티 댓글',
  MEMORY: '기록',
  PROGRAM: '행사',
  SYNC_JOB_RUN: '데이터 불러오기',
};

export const AUDIT_TARGET_TYPES: readonly AdminAuditTargetType[] = [
  'MEMBER_REPORT',
  'SANCTION',
  'SPOT',
  'COMMUNITY_POST',
  'COMMUNITY_COMMENT',
  'MEMORY',
  'PROGRAM',
  'SYNC_JOB_RUN',
];

const AUDIT_TARGET_PATHS: Record<AdminAuditTargetType, ((targetId: number) => string) | null> = {
  MEMBER_REPORT: (targetId) => `/admin/reports/${targetId}`,
  SANCTION: null,
  SPOT: null,
  COMMUNITY_POST: null,
  COMMUNITY_COMMENT: null,
  MEMORY: null,
  PROGRAM: (targetId) => `/admin/programs/${targetId}/applications`,
  SYNC_JOB_RUN: null,
};

export function auditTargetPath(targetType: AdminAuditTargetType, targetId: number): string | null {
  return AUDIT_TARGET_PATHS[targetType]?.(targetId) ?? null;
}

const MODERATION_STATUS_LABELS: Record<string, string> = {
  ACTIVE: '공개 중',
  PENDING_REVIEW: '검토 대기',
  HIDDEN: '숨김',
  DELETED: '삭제',
};

const PROGRAM_STATUS_LABELS: Record<string, string> = {
  SCHEDULED: '진행 예정',
  CANCELED: '취소',
};

// 같은 ACTIVE라도 대상에 따라 뜻이 달라서(제재는 적용 중, 글은 공개 중) 대상 종류별로 따로 둔다.
const STATUS_LABELS: Record<AdminAuditTargetType, Readonly<Record<string, string>>> = {
  MEMBER_REPORT: Object.fromEntries(Object.entries(REPORT_STATUS_META).map(([code, meta]) => [code, meta.label])),
  SANCTION: SANCTION_STATUS_LABELS,
  SPOT: MODERATION_STATUS_LABELS,
  COMMUNITY_POST: MODERATION_STATUS_LABELS,
  COMMUNITY_COMMENT: MODERATION_STATUS_LABELS,
  MEMORY: MODERATION_STATUS_LABELS,
  PROGRAM: PROGRAM_STATUS_LABELS,
  SYNC_JOB_RUN: {},
};

const SPOT_TYPE_LABELS: Record<string, string> = {
  CAMPSITE: '야영장',
  FOREST: '휴양림',
  BAKJI: '박지',
};

const SYNC_JOB_LABELS: Record<SyncJobType, string> = {
  GOCAMPING: '고캠핑',
  FOREST: '국립 자연휴양림',
  FOREST_STANDARD: '공립·사립 휴양림',
  PARK_BOUNDARY: '공원 경계',
  BAKJI_REJUDGE: '박지 경고 다시 확인',
};

const PROGRAM_FIELD_LABELS: Record<string, string> = {
  title: '제목',
  description: '설명',
  spotId: '장소',
  locationText: '장소 설명',
  startAt: '시작 시각',
  endAt: '끝나는 시각',
  capacity: '정원',
  fee: '참가비',
  applyOpenAt: '신청 시작',
  applyCloseAt: '신청 마감',
  paymentDeadlineMinutes: '결제 기한',
  overnight: '숙박 여부',
};

type FieldSpec =
  | { label: string; kind: 'status' }
  | { label: string; kind: 'text'; names: Readonly<Record<string, string>> }
  | { label: string; kind: 'number'; unit: string }
  | { label: string; kind: 'flag'; yes: string; no: string }
  | { label: string; kind: 'list'; names: Readonly<Record<string, string>> };

const STATUS_SPEC = { kind: 'status' } as const;
const CAPACITY_SPEC = { kind: 'number', unit: '명' } as const;

const DETAIL_FIELDS: ReadonlyMap<string, FieldSpec> = new Map<string, FieldSpec>([
  ['fromStatus', { label: '이전 상태', ...STATUS_SPEC }],
  ['toStatus', { label: '이후 상태', ...STATUS_SPEC }],
  ['reviewedReportCount', { label: '함께 검토 처리된 신고', kind: 'number', unit: '건' }],
  ['hideReview', { label: '신고된 후기 숨김', kind: 'flag', yes: '함께 숨김', no: '숨기지 않음' }],
  ['sanctionId', { label: '제재 번호', kind: 'number', unit: '' }],
  ['sanctionType', { label: '제재 종류', kind: 'text', names: SANCTION_TYPE_LABELS }],
  ['level', { label: '제재 단계', kind: 'number', unit: '단계' }],
  ['liftedSanctionId', { label: '함께 해제한 제재 번호', kind: 'number', unit: '' }],
  ['memberId', { label: '대상 회원 번호', kind: 'number', unit: '' }],
  ['spotType', { label: '장소 종류', kind: 'text', names: SPOT_TYPE_LABELS }],
  ['jobType', { label: '작업', kind: 'text', names: SYNC_JOB_LABELS }],
  ['capacity', { label: '정원', ...CAPACITY_SPEC }],
  ['overnight', { label: '숙박', kind: 'flag', yes: '있음', no: '없음' }],
  ['changedFields', { label: '바뀐 항목', kind: 'list', names: PROGRAM_FIELD_LABELS }],
  ['capacityBefore', { label: '이전 정원', ...CAPACITY_SPEC }],
  ['capacityAfter', { label: '이후 정원', ...CAPACITY_SPEC }],
  ['canceledApplicationCount', { label: '취소된 신청', kind: 'number', unit: '건' }],
  ['refundedPaymentCount', { label: '환불된 결제', kind: 'number', unit: '건' }],
]);

// 이전과 이후 값이 함께 있으면 "이전 → 이후" 한 줄로 합친다.
const BEFORE_AFTER_PAIRS = [
  { before: 'fromStatus', after: 'toStatus', label: '상태' },
  { before: 'capacityBefore', after: 'capacityAfter', label: '정원' },
] as const;

export type AuditDetailEntry = { key: string; label: string; text: string };

function plainText(value: unknown): string {
  if (typeof value === 'string') return value;
  if (value === null || value === undefined) return '없음';
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value) ?? '없음';
}

function namedText(names: Readonly<Record<string, string>>, code: string): string {
  return Object.hasOwn(names, code) ? (names[code] ?? code) : code;
}

// detail은 서버가 조치마다 다르게 채우는 JSON이라서, 값의 모양을 여기서 한 번만 확인한다.
// 기대한 모양이 아니면 꾸미지 않고 있는 그대로 보인다.
function formatValue(spec: FieldSpec, value: unknown, targetType: AdminAuditTargetType): string {
  switch (spec.kind) {
    case 'status':
      return typeof value === 'string' ? namedText(STATUS_LABELS[targetType], value) : plainText(value);
    case 'text':
      return typeof value === 'string' ? namedText(spec.names, value) : plainText(value);
    case 'number':
      return typeof value === 'number' ? `${value.toLocaleString('ko-KR')}${spec.unit}` : plainText(value);
    case 'flag':
      return typeof value === 'boolean' ? (value ? spec.yes : spec.no) : plainText(value);
    case 'list':
      return Array.isArray(value) && value.every((item): item is string => typeof item === 'string')
        ? value.map((item) => namedText(spec.names, item)).join(', ')
        : plainText(value);
  }
}

function formatEntry(key: string, value: unknown, targetType: AdminAuditTargetType): AuditDetailEntry {
  const spec = DETAIL_FIELDS.get(key);
  if (spec === undefined) return { key, label: key, text: plainText(value) };
  return { key, label: spec.label, text: formatValue(spec, value, targetType) };
}

export function summarizeAuditDetail(
  detail: Record<string, unknown> | null,
  targetType: AdminAuditTargetType,
): AuditDetailEntry[] {
  if (detail === null) return [];
  const entries: AuditDetailEntry[] = [];
  const merged = new Set<string>();
  for (const [key, value] of Object.entries(detail)) {
    if (merged.has(key)) continue;
    const pair = BEFORE_AFTER_PAIRS.find((candidate) => candidate.before === key || candidate.after === key);
    if (pair !== undefined && pair.before in detail && pair.after in detail) {
      merged.add(pair.before).add(pair.after);
      const before = formatEntry(pair.before, detail[pair.before], targetType);
      const after = formatEntry(pair.after, detail[pair.after], targetType);
      entries.push({ key: pair.before, label: pair.label, text: `${before.text} → ${after.text}` });
      continue;
    }
    entries.push(formatEntry(key, value, targetType));
  }
  return entries;
}
