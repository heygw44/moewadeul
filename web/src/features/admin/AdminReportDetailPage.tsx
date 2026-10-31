import { useEffect, useState } from 'react';
import { actionReport, dismissReport, fetchAdminMemberReport, liftSanction, startReportReview } from '../../api/admin';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import type {
  AdminReportDetail,
  AdminSanctionHistoryItem,
  ConfirmableSanctionType,
  SanctionLiftReason,
} from '../../api/types';
import { Link } from '../../app/router';
import { StatusText } from '../../components/StatusText';
import { Button } from '../../components/Button';
import { Checkbox } from '../../components/Checkbox';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { Dialog } from '../../components/Dialog';
import { Evidence } from '../../components/Evidence';
import { ErrorNotice } from '../../components/ErrorNotice';
import { PageCard } from '../../components/PageLayout';
import { SelectField } from '../../components/SelectField';
import { Skeleton } from '../../components/Skeleton';
import { TextArea } from '../../components/TextArea';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime, formatLocalDate } from '../../lib/datetime';
import {
  REPORT_KIND_LABELS,
  REPORT_STATUS_META,
  REPORT_TYPE_LABELS,
  SANCTION_STATUS_LABELS,
  SANCTION_TYPE_LABELS,
} from '../report/reportLabels';
import { BASECAMP_STATUS_META } from '../basecamp/basecampLabels';
import { AdminPage } from './AdminGate';
import { buttonClassName } from '../../components/buttonClass';

const REASON_MAX = 500;
const NOTE_MAX = 1000;
const NO_SANCTION = 'NONE';

const SANCTION_OPTIONS = [
  { value: NO_SANCTION, label: '없음' },
  { value: 'WARNING', label: '경고' },
  { value: 'SUSPEND_7D', label: '7일 정지' },
  { value: 'SUSPEND_30D', label: '30일 정지' },
  { value: 'PERMANENT', label: '영구 정지' },
];

const LIFT_REASONS: ReadonlyArray<ChoiceOption<SanctionLiftReason> & { description: string }> = [
  {
    value: 'END_EARLY',
    label: '조기 종료',
    description: '기간만 일찍 끝내요. 제재 이력에는 남아 다음 제재 단계와 신뢰 회원 판정에 반영돼요.',
  },
  { value: 'REVOKE', label: '처분 취소', description: '제재가 잘못 내려졌어요. 제재 이력에서 빠져요.' },
];

type Failure = { message: string; error: unknown };

function toFailure(error: unknown): Failure {
  return { message: toUserMessage(error), error };
}

type DetailState =
  { status: 'loading' } | ({ status: 'error' } & Failure) | { status: 'ready'; detail: AdminReportDetail };

export function AdminReportDetailPage({ reportId }: { reportId: number }) {
  return (
    <AdminPage title="신고 상세" current="/admin/reports">
      <ReportDetail reportId={reportId} />
    </AdminPage>
  );
}

function ReportDetail({ reportId }: { reportId: number }) {
  const [state, setState] = useState<DetailState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const showSkeleton = useDelayedFlag(state.status === 'loading');

  useEffect(() => {
    const controller = new AbortController();
    fetchAdminMemberReport(reportId, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setState({ status: 'ready', detail });
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) setState({ status: 'error', ...toFailure(caught) });
      },
    );
    return () => controller.abort();
  }, [reportId, attempt]);

  function reload() {
    setAttempt((value) => value + 1);
  }

  if (state.status === 'loading') return showSkeleton ? <Skeleton className="h-60 w-full" /> : null;

  if (state.status === 'error') {
    return (
      <div role="alert" className="flex flex-col gap-3">
        <ErrorNotice title="신고를 불러오지 못했어요" error={state.error} message={state.message} />
        <div className="flex flex-wrap gap-2">
          <Button
            variant="secondary"
            onClick={() => {
              setState({ status: 'loading' });
              reload();
            }}
          >
            다시 불러오기
          </Button>
          <Link to="/admin/reports" className={buttonClassName('secondary')}>
            신고 목록
          </Link>
        </div>
      </div>
    );
  }

  const { detail } = state;
  return (
    <div className="flex flex-col gap-4">
      <SummaryCard detail={detail} />
      {detail.basecamp && <BasecampCard basecamp={detail.basecamp} />}
      {detail.companionReview && <ReviewCard review={detail.companionReview} />}
      <ResultCard detail={detail} />
      <ActionCard detail={detail} onChanged={reload} />
      <SanctionHistoryCard items={detail.sanctionHistory} onChanged={reload} />
    </div>
  );
}

function SummaryCard({ detail }: { detail: AdminReportDetail }) {
  const status = REPORT_STATUS_META[detail.status];
  return (
    <PageCard title={`${REPORT_TYPE_LABELS[detail.type]} (${REPORT_KIND_LABELS[detail.kind]})`}>
      <div className="flex flex-wrap items-center gap-2">
        {detail.urgent && <StatusText tone="danger">긴급</StatusText>}
        <StatusText tone={status.tone}>{status.label}</StatusText>
      </div>
      <Evidence
        items={[
          {
            label: '신고자',
            value: (
              <Link to={`/members/${detail.reporter.memberId}`} className="font-semibold text-accent">
                {detail.reporter.nickname}
              </Link>
            ),
          },
          {
            label: '신고 대상',
            value: (
              <Link to={`/members/${detail.target.memberId}`} className="font-semibold text-accent">
                {detail.target.nickname}
              </Link>
            ),
          },
          { label: '접수 시각', value: formatKstDateTime(detail.createdAt) },
        ]}
      />
      <div className="flex flex-col gap-1">
        <p className="text-sm font-medium text-ink">신고 내용</p>
        <p className="whitespace-pre-line break-words text-base text-ink">{detail.content}</p>
      </div>
    </PageCard>
  );
}

function BasecampCard({ basecamp }: { basecamp: NonNullable<AdminReportDetail['basecamp']> }) {
  return (
    <PageCard title="관련 베이스캠프">
      <Evidence
        items={[
          { label: '제목', value: basecamp.title },
          { label: '상태', value: BASECAMP_STATUS_META[basecamp.status].label },
          { label: '출발일', value: formatLocalDate(basecamp.startDate) },
        ]}
      />
      <div>
        <Link
          to={`/basecamps/${basecamp.basecampId}`}
          className="inline-flex min-h-11 items-center font-semibold text-accent"
        >
          베이스캠프 보기
        </Link>
      </div>
    </PageCard>
  );
}

function ReviewCard({ review }: { review: NonNullable<AdminReportDetail['companionReview']> }) {
  return (
    <PageCard title="신고된 동행 후기">
      <div className="flex flex-wrap items-center gap-2">
        {review.hidden && <StatusText tone="neutral">숨김</StatusText>}
        <StatusText tone={review.rejoinWanted ? 'accent' : 'neutral'}>
          {review.rejoinWanted ? '다시 동행하고 싶어요' : '다시 동행하고 싶지 않아요'}
        </StatusText>
      </div>
      {review.tags.length > 0 && <p className="text-sm text-ink-muted">태그: {review.tags.join(' · ')}</p>}
      {review.comment ? (
        <p className="whitespace-pre-line break-words text-base text-ink">{review.comment}</p>
      ) : (
        <p className="text-sm text-ink-muted">남긴 코멘트가 없어요.</p>
      )}
    </PageCard>
  );
}

function ResultCard({ detail }: { detail: AdminReportDetail }) {
  if (detail.handledAt === null && detail.resultNote === null && detail.handledBy === null) return null;
  return (
    <PageCard title="처리 결과">
      <Evidence
        items={[
          ...(detail.handledBy !== null ? [{ label: '처리한 관리자', value: `회원 번호 ${detail.handledBy}` }] : []),
          ...(detail.handledAt ? [{ label: '처리 시각', value: formatKstDateTime(detail.handledAt) }] : []),
        ]}
      />
      {detail.resultNote && <p className="whitespace-pre-line break-words text-base text-ink">{detail.resultNote}</p>}
    </PageCard>
  );
}

function ActionCard({ detail, onChanged }: { detail: AdminReportDetail; onChanged: () => void }) {
  if (detail.status === 'RECEIVED') return <StartReviewCard reportId={detail.reportId} onChanged={onChanged} />;
  if (detail.status === 'IN_REVIEW') return <TakeActionCard detail={detail} onChanged={onChanged} />;
  return null;
}

function StartReviewCard({ reportId, onChanged }: { reportId: number; onChanged: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Failure | null>(null);

  async function start() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await startReportReview(reportId);
      onChanged();
    } catch (caught) {
      setError(toFailure(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <PageCard title="처리">
      <p className="text-sm text-ink-muted">검토를 시작하면 조치나 기각을 고를 수 있어요.</p>
      {error && (
        <div role="alert">
          <ErrorNotice title="검토를 시작하지 못했어요" error={error.error} message={error.message} />
        </div>
      )}
      <div>
        <Button loading={busy} onClick={() => void start()}>
          검토 시작
        </Button>
      </div>
    </PageCard>
  );
}

type FormErrors = { reason?: string; note?: string };

function toFormErrors(error: unknown): { fields: FormErrors; rest: Failure | null } {
  if (!(error instanceof ApiError) || error.fieldErrors.length === 0) {
    return { fields: {}, rest: toFailure(error) };
  }
  const fields: FormErrors = {};
  const others: string[] = [];
  for (const { field, reason } of error.fieldErrors) {
    const name = field.split('.').pop();
    if (name === 'reason') fields.reason = reason;
    else if (name === 'note') fields.note = reason;
    else others.push(reason);
  }
  return { fields, rest: others.length > 0 ? { message: others.join(' '), error } : null };
}

function TakeActionCard({ detail, onChanged }: { detail: AdminReportDetail; onChanged: () => void }) {
  const [sanction, setSanction] = useState<string>(NO_SANCTION);
  const [reason, setReason] = useState('');
  const [hideReview, setHideReview] = useState(false);
  const [note, setNote] = useState('');
  const [confirmAction, setConfirmAction] = useState(false);
  const [dismissing, setDismissing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<FormErrors>({});
  const [formError, setFormError] = useState<Failure | null>(null);

  const hasSanction = sanction !== NO_SANCTION;
  const trimmedReason = reason.trim();
  const reasonMissing = hasSanction && trimmedReason.length === 0;
  const nothingChosen = !hasSanction && !hideReview;
  const disabledReason = nothingChosen
    ? '조치하려면 제재를 고르거나 신고된 후기 숨기기를 켜 주세요.'
    : reasonMissing
      ? '제재 사유를 적어 주세요.'
      : undefined;

  async function submit() {
    if (busy || disabledReason) return;
    setBusy(true);
    setErrors({});
    setFormError(null);
    try {
      await actionReport(detail.reportId, {
        sanction: hasSanction ? { type: sanction as ConfirmableSanctionType, reason: trimmedReason } : null,
        hideReview: detail.kind === 'REVIEW' && hideReview,
        note: note.trim() === '' ? undefined : note.trim(),
      });
      onChanged();
    } catch (caught) {
      const { fields, rest } = toFormErrors(caught);
      setErrors(fields);
      setFormError(rest);
      setConfirmAction(false);
      setBusy(false);
    }
  }

  return (
    <PageCard title="처리">
      <p className="text-sm text-ink-muted">
        제재 단계는 이전 제재 이력에 따라 정해져요. 고른 단계가 맞지 않으면 저장되지 않아요. 오류 안내에 맞춰 다시 골라
        주세요. 심각한 위반이면 영구 정지를 바로 고를 수 있어요.
      </p>
      {formError && (
        <div role="alert">
          <ErrorNotice title="조치하지 못했어요" error={formError.error} message={formError.message} />
        </div>
      )}
      <SelectField
        label="제재"
        value={sanction}
        options={SANCTION_OPTIONS}
        disabled={busy}
        onChange={(event) => setSanction(event.target.value)}
      />
      {hasSanction && (
        <TextArea
          label="제재 사유"
          name="reason"
          maxLength={REASON_MAX}
          value={reason}
          disabled={busy}
          error={errors.reason}
          hint="회원에게 그대로 보여요. 1~500자로 적어 주세요."
          onChange={(event) => setReason(event.target.value)}
        />
      )}
      {detail.kind === 'REVIEW' && (
        <Checkbox
          label="신고된 후기 숨기기"
          checked={hideReview}
          disabled={busy}
          onChange={(event) => setHideReview(event.target.checked)}
        />
      )}
      <TextArea
        label="처리 메모 (선택)"
        name="note"
        maxLength={NOTE_MAX}
        value={note}
        disabled={busy}
        error={errors.note}
        onChange={(event) => setNote(event.target.value)}
      />
      <div className="flex flex-wrap items-start gap-2">
        <Button
          variant={hasSanction ? 'danger' : 'primary'}
          loading={busy && !confirmAction}
          disabled={disabledReason !== undefined}
          disabledReason={disabledReason}
          onClick={() => (hasSanction ? setConfirmAction(true) : void submit())}
        >
          조치
        </Button>
        <Button variant="secondary" disabled={busy} onClick={() => setDismissing(true)}>
          기각
        </Button>
      </div>
      {confirmAction && (
        <Dialog title="제재를 확정할까요?" onClose={() => !busy && setConfirmAction(false)}>
          <div className="flex flex-col gap-4">
            <p className="text-base text-ink">
              {detail.target.nickname}님에게 {SANCTION_TYPE_LABELS[sanction as ConfirmableSanctionType]}를 확정해요.
              {sanction === 'WARNING'
                ? ' 경고만으로 활동이 막히지는 않지만 다음 제재 단계에 반영돼요.'
                : ' 정지는 확정하는 즉시 적용되고 이 회원은 진행 중인 베이스캠프에서도 빠져요.'}
            </p>
            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="secondary" disabled={busy} onClick={() => setConfirmAction(false)}>
                취소
              </Button>
              <Button variant="danger" loading={busy} onClick={() => void submit()}>
                확정
              </Button>
            </div>
          </div>
        </Dialog>
      )}
      {dismissing && (
        <DismissDialog
          reportId={detail.reportId}
          onClose={() => setDismissing(false)}
          onDone={() => {
            setDismissing(false);
            onChanged();
          }}
        />
      )}
    </PageCard>
  );
}

function DismissDialog({ reportId, onClose, onDone }: { reportId: number; onClose: () => void; onDone: () => void }) {
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [noteError, setNoteError] = useState<string | undefined>();
  const [error, setError] = useState<Failure | null>(null);

  async function dismiss() {
    if (busy) return;
    setBusy(true);
    setNoteError(undefined);
    setError(null);
    try {
      await dismissReport(reportId, { note: note.trim() === '' ? undefined : note.trim() });
      onDone();
    } catch (caught) {
      const { fields, rest } = toFormErrors(caught);
      setNoteError(fields.note);
      setError(rest);
      setBusy(false);
    }
  }

  return (
    <Dialog title="신고를 기각할까요?" onClose={() => !busy && onClose()}>
      <div className="flex flex-col gap-4">
        <p className="text-base text-ink">신고를 받아들이지 않아요. 이 신고로 임시 정지가 걸려 있으면 함께 풀려요.</p>
        {error && (
          <div role="alert">
            <ErrorNotice title="기각하지 못했어요" error={error.error} message={error.message} />
          </div>
        )}
        <TextArea
          label="처리 메모 (선택)"
          name="note"
          maxLength={NOTE_MAX}
          value={note}
          disabled={busy}
          error={noteError}
          onChange={(event) => setNote(event.target.value)}
        />
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" disabled={busy} onClick={onClose}>
            취소
          </Button>
          <Button loading={busy} onClick={() => void dismiss()}>
            기각
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

function SanctionHistoryCard({ items, onChanged }: { items: AdminSanctionHistoryItem[]; onChanged: () => void }) {
  const [lifting, setLifting] = useState<AdminSanctionHistoryItem | null>(null);

  return (
    <PageCard title="대상 회원의 제재 이력">
      {items.length === 0 ? (
        <p className="text-sm text-ink-muted">제재 이력이 없어요.</p>
      ) : (
        <ul className="flex flex-col gap-1">
          {items.map((item) => (
            <li key={item.sanctionId} className="flex flex-col gap-2 rounded-row bg-surface p-4">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-base font-semibold text-ink">{SANCTION_TYPE_LABELS[item.type]}</span>
                {item.level !== null && (
                  <span className="font-mono text-sm tabular-nums text-ink-muted">{item.level}단계</span>
                )}
                <StatusText tone={item.status === 'ACTIVE' ? 'danger' : 'neutral'}>
                  {SANCTION_STATUS_LABELS[item.status]}
                </StatusText>
              </div>
              <p className="whitespace-pre-line break-words text-sm text-ink">{item.reason}</p>
              <p className="font-mono text-sm tabular-nums text-ink-muted">
                {formatKstDateTime(item.startsAt)}
                {item.endsAt ? ` ~ ${formatKstDateTime(item.endsAt)}` : ''}
                {item.liftedAt ? ` · 해제 ${formatKstDateTime(item.liftedAt)}` : ''}
              </p>
              {item.status === 'ACTIVE' && (
                <div>
                  <Button variant="secondary" onClick={() => setLifting(item)}>
                    해제
                  </Button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      {lifting && (
        <LiftDialog
          sanction={lifting}
          onClose={() => setLifting(null)}
          onDone={() => {
            setLifting(null);
            onChanged();
          }}
        />
      )}
    </PageCard>
  );
}

function LiftDialog({
  sanction,
  onClose,
  onDone,
}: {
  sanction: AdminSanctionHistoryItem;
  onClose: () => void;
  onDone: () => void;
}) {
  const [reason, setReason] = useState<SanctionLiftReason | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Failure | null>(null);

  async function lift() {
    if (busy || reason === null) return;
    setBusy(true);
    setError(null);
    try {
      await liftSanction(sanction.sanctionId, reason);
      onDone();
    } catch (caught) {
      setError(toFailure(caught));
      setBusy(false);
    }
  }

  return (
    <Dialog title="제재를 해제할까요?" onClose={() => !busy && onClose()}>
      <div className="flex flex-col gap-4">
        <p className="text-base text-ink">
          {SANCTION_TYPE_LABELS[sanction.type]}를 해제해요. 이미 빠진 베이스캠프와 취소된 결제 대기 신청은 되돌아가지
          않아요.
        </p>
        <ChoiceGroup
          legend="해제 사유"
          name="lift-reason"
          options={LIFT_REASONS}
          value={reason}
          disabled={busy}
          hint={LIFT_REASONS.find((option) => option.value === reason)?.description ?? '해제 사유를 골라 주세요.'}
          onChange={setReason}
        />
        {error && (
          <div role="alert">
            <ErrorNotice title="해제하지 못했어요" error={error.error} message={error.message} />
          </div>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" disabled={busy} onClick={onClose}>
            취소
          </Button>
          <Button variant="danger" loading={busy} disabled={reason === null} onClick={() => void lift()}>
            해제
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
