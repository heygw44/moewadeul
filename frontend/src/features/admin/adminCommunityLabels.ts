import { ApiError } from '../../api/client';
import type {
  AdminCommunityComment,
  AdminCommunityPost,
  AdminCommunityStatus,
  CommunityReportReason,
} from '../../api/types';
import { REPORT_REASON_OPTIONS } from '../community/communityLabels';
import type { CommunityNoun } from '../community/communityLabels';

export type AdminCommunityItem = AdminCommunityPost | AdminCommunityComment;

export type ReviewAction = 'hide' | 'restore';

export const STATUS_TABS: ReadonlyArray<{ value: AdminCommunityStatus; label: string }> = [
  { value: 'PENDING_REVIEW', label: '검토 대기' },
  { value: 'HIDDEN', label: '숨김' },
];

export const STATUS_LABELS: Record<AdminCommunityStatus, string> = {
  PENDING_REVIEW: '검토 대기',
  HIDDEN: '숨김',
};

export const EMPTY_COPY: Record<CommunityNoun, Record<AdminCommunityStatus, { title: string; description: string }>> = {
  글: {
    PENDING_REVIEW: { title: '검토를 기다리는 글이 없어요', description: '신고가 쌓인 글이 여기에 모여요.' },
    HIDDEN: { title: '숨긴 글이 없어요', description: '숨긴 글이 여기에 모여요.' },
  },
  댓글: {
    PENDING_REVIEW: { title: '검토를 기다리는 댓글이 없어요', description: '신고가 쌓인 댓글이 여기에 모여요.' },
    HIDDEN: { title: '숨긴 댓글이 없어요', description: '숨긴 댓글이 여기에 모여요.' },
  },
};

export const RESTORE_NOTE = '복구하면 이전 신고는 세지 않아요. 새 신고가 5건 쌓여야 다시 검토 대기가 돼요.';

export function reasonLabel(reason: CommunityReportReason): string {
  return REPORT_REASON_OPTIONS.find((option) => option.value === reason)?.label ?? reason;
}

export function reasonCountsText(counts: AdminCommunityItem['reasonCounts']): string {
  const parts = REPORT_REASON_OPTIONS.flatMap((option) => {
    const count = counts[option.value] ?? 0;
    return count > 0 ? [`${option.label} ${count}`] : [];
  });
  return parts.length > 0 ? parts.join(', ') : '없음';
}

export function isStaleItemError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 404 || error.status === 409);
}
