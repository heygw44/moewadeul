import { useId, useState } from 'react';
import type { ReactNode } from 'react';
import type { AdminCommunityComment, AdminCommunityPost, AdminCommunityStatus } from '../../api/types';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { ErrorNotice } from '../../components/ErrorNotice';
import { StatusText } from '../../components/StatusText';
import { formatKstDateTime } from '../../lib/datetime';
import {
  RESTORE_NOTE,
  STATUS_LABELS,
  hideConfirmText,
  reasonCountsText,
  reasonLabel,
  type AdminCommunityItem,
  type CommunityNoun,
  type ReviewAction,
} from './adminCommunityLabels';

export type ActionResult = 'done' | 'failed';

export interface CardProps<T extends AdminCommunityItem> {
  item: T;
  tab: AdminCommunityStatus;
  failure: unknown;
  onAction: (action: ReviewAction) => Promise<ActionResult>;
}

interface ReviewCardProps extends Omit<CardProps<AdminCommunityItem>, 'item'> {
  item: AdminCommunityItem;
  noun: CommunityNoun;
  heading: string;
  facts: ReadonlyArray<{ label: string; value: string }>;
  children: ReactNode;
}

export function PostCard({ item, ...rest }: CardProps<AdminCommunityPost>) {
  return (
    <ReviewCard
      {...rest}
      item={item}
      noun="글"
      heading={item.title}
      facts={[{ label: '작성자', value: item.author.nickname }]}
    >
      <p className="whitespace-pre-line break-words text-base text-ink">{item.content}</p>
    </ReviewCard>
  );
}

export function CommentCard({ item, ...rest }: CardProps<AdminCommunityComment>) {
  return (
    <ReviewCard
      {...rest}
      item={item}
      noun="댓글"
      heading={`${item.author.nickname}님의 댓글`}
      facts={[{ label: '달린 글', value: `글 번호 ${item.postId}` }]}
    >
      <p className="whitespace-pre-line break-words text-base text-ink">{item.content}</p>
    </ReviewCard>
  );
}

function ReviewCard({ item, noun, heading, facts, tab, failure, onAction, children }: ReviewCardProps) {
  const [confirmHide, setConfirmHide] = useState(false);
  const [busy, setBusy] = useState<ReviewAction | null>(null);
  const restoreNoteId = useId();

  async function run(action: ReviewAction) {
    if (busy) return;
    setBusy(action);
    const result = await onAction(action);
    if (result === 'failed') {
      setConfirmHide(false);
      setBusy(null);
    }
  }

  return (
    <li className="flex min-w-0 flex-col gap-3 rounded-sheet bg-fill p-5">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="min-w-0 break-words text-lg font-semibold text-ink">{heading}</h3>
        <StatusText tone="neutral">{STATUS_LABELS[item.status]}</StatusText>
      </div>
      {children}
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
        {facts.map((fact) => (
          <FactRow key={fact.label} label={fact.label} value={fact.value} />
        ))}
        <dt className="text-ink-muted">검토 전 신고</dt>
        <dd className="min-w-0 break-words text-ink">
          <span className="font-mono tabular-nums">{item.reportCount}건</span> ({reasonCountsText(item.reasonCounts)})
        </dd>
        <dt className="text-ink-muted">상태 변경</dt>
        <dd className="font-mono tabular-nums text-ink">{formatKstDateTime(item.statusChangedAt)}</dd>
      </dl>
      {item.recentReports.length > 0 && (
        <div className="flex flex-col gap-2">
          <p className="text-sm font-medium text-ink">최근 신고</p>
          <ul className="flex flex-col gap-1" aria-label="최근 신고">
            {item.recentReports.map((report, index) => (
              <li key={index} className="rounded-row bg-surface px-4 py-2.5 text-sm">
                <p className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold text-ink">{reasonLabel(report.reason)}</span>
                  <span className="font-mono tabular-nums text-ink-muted">{formatKstDateTime(report.createdAt)}</span>
                </p>
                {report.content && <p className="mt-1 whitespace-pre-line break-words text-ink">{report.content}</p>}
              </li>
            ))}
          </ul>
        </div>
      )}
      {failure !== null && (
        <div role="alert">
          <ErrorNotice title="상태를 바꾸지 못했어요" error={failure} />
        </div>
      )}
      <div className="flex flex-wrap items-center gap-2">
        {tab === 'PENDING_REVIEW' && (
          <Button variant="danger" disabled={busy !== null} onClick={() => setConfirmHide(true)}>
            숨기기
          </Button>
        )}
        <Button
          variant="secondary"
          loading={busy === 'restore'}
          disabled={busy === 'hide'}
          aria-describedby={restoreNoteId}
          onClick={() => void run('restore')}
        >
          복구
        </Button>
      </div>
      <p id={restoreNoteId} className="text-sm text-ink-muted">
        {RESTORE_NOTE}
      </p>
      {confirmHide && (
        <Dialog title={`${noun}을 숨길까요?`} onClose={() => busy === null && setConfirmHide(false)}>
          <div className="flex flex-col gap-4">
            <p className="text-base text-ink">{hideConfirmText(noun, item.author.nickname)}</p>
            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="secondary" disabled={busy !== null} onClick={() => setConfirmHide(false)}>
                취소
              </Button>
              <Button variant="danger" loading={busy === 'hide'} onClick={() => void run('hide')}>
                숨기기
              </Button>
            </div>
          </div>
        </Dialog>
      )}
    </li>
  );
}

function FactRow({ label, value }: { label: string; value: string }) {
  return (
    <>
      <dt className="text-ink-muted">{label}</dt>
      <dd className="min-w-0 break-words text-ink">{value}</dd>
    </>
  );
}
