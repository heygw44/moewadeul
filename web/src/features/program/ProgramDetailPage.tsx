import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { createIdempotentRunner } from '../../api/idempotency';
import {
  applyProgram,
  cancelApplication,
  getProgram,
  payApplication,
  subscribeVacancyAlert,
  unsubscribeVacancyAlert,
} from '../../api/programs';
import type { ProgramDetail } from '../../api/types';
import { Link } from '../../app/router';
import { BackLink } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { Notice } from '../../components/Notice';
import { PageLayout } from '../../components/PageLayout';
import { MetaLine, StatusText } from '../../components/StatusText';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { buttonClassName, SECONDARY_LINK_CLASS } from '../../components/buttonClass';
import { useDocumentTitle } from '../../components/useDocumentTitle';
import { formatKstDateTime } from '../../lib/datetime';
import { withNext } from '../../app/session/nextPath';
import { useSession } from '../../app/session/session';
import { useViewerEpoch } from '../../app/session/useViewerEpoch';
import { countdownText, feeText, PROGRAM_STATUS_META, seatsText } from './programLabels';
import { useApplyPhaseRefresh } from './useApplyPhaseRefresh';
import { useNow } from './useNow';

type LoadResult = { programId: number; detail: ProgramDetail } | { programId: number; error: unknown };

const LINK_BUTTON_CLASS = buttonClassName('primary');
const BOARD_ROW_CLASS =
  'grid grid-cols-[6.5rem_minmax(0,1fr)] gap-3 px-4 py-2.5 text-[0.9375rem] sm:grid-cols-[8rem_minmax(0,1fr)] sm:px-5';

export function ProgramDetailPage({ programId }: { programId: number }) {
  const viewerEpoch = useViewerEpoch();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<LoadResult | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getProgram(programId, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setResult({ programId, detail });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ programId, error });
      },
    );
    return () => controller.abort();
  }, [programId, attempt, viewerEpoch]);

  const current = result?.programId === programId ? result : null;
  const showSkeleton = useDelayedFlag(current === null);
  const loadedTitle = current !== null && 'detail' in current ? current.detail.title : null;

  useDocumentTitle(loadedTitle ? `${loadedTitle} · 뫼와들` : '공식 행사 · 뫼와들');

  const reload = useCallback(() => setAttempt((value) => value + 1), []);

  let body: ReactNode;
  if (current === null) {
    body = showSkeleton ? (
      <div className="flex flex-col gap-3">
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-40 w-full" />
      </div>
    ) : null;
  } else if ('detail' in current) {
    body = <DetailContent detail={current.detail} onChanged={reload} />;
  } else if (current.error instanceof ApiError && current.error.code === 'NOT_FOUND') {
    body = (
      <EmptyState
        framed
        title="행사를 찾을 수 없어요"
        description="삭제됐거나 주소가 잘못됐어요."
        action={
          <Link to="/programs" className={SECONDARY_LINK_CLASS}>
            행사 목록으로
          </Link>
        }
      />
    );
  } else {
    body = (
      <div className="flex flex-col gap-3">
        <Notice tone="danger" title="행사를 불러오지 못했어요">
          {toUserMessage(current.error)}
        </Notice>
        <div>
          <Button variant="secondary" onClick={reload}>
            다시 불러오기
          </Button>
        </div>
      </div>
    );
  }

  const detail = current !== null && 'detail' in current ? current.detail : null;
  useApplyPhaseRefresh(detail, reload);
  const meta = detail ? (
    <MetaLine
      items={[
        detail.status !== 'CANCELED' && (
          <StatusText tone={PROGRAM_STATUS_META[detail.status].tone}>
            {PROGRAM_STATUS_META[detail.status].label}
          </StatusText>
        ),
        detail.overnight && '숙박',
      ]}
    />
  ) : undefined;

  return (
    <PageLayout
      title={detail?.title ?? '공식 행사'}
      breadcrumb={<BackLink to="/programs">공식 행사</BackLink>}
      meta={meta}
      titleInContent={detail !== null}
    >
      <div aria-busy={current === null}>{body}</div>
    </PageLayout>
  );
}

function DetailContent({ detail, onChanged }: { detail: ProgramDetail; onChanged: () => void }) {
  return (
    <div className="flex flex-col gap-4">
      <article className="flex flex-col gap-4">
        <header className="text-ink">
          {detail.status !== 'CANCELED' && (
            <p className="inline-flex items-center gap-1.5 text-[0.8125rem] font-bold">
              <span
                aria-hidden="true"
                className={`size-2 rounded-full ${detail.status === 'OPEN' ? 'bg-brand' : 'bg-ink-faint'}`}
              />
              {PROGRAM_STATUS_META[detail.status].label}
              {detail.overnight && <span className="font-normal text-ink-muted"> · 숙박</span>}
            </p>
          )}
          <p
            aria-hidden="true"
            className="mt-1 text-2xl leading-tight font-extrabold tracking-[-0.03em] break-words sm:text-[1.875rem]"
          >
            {detail.title}
          </p>
          <p className="mt-1.5 text-sm text-ink-muted">
            {detail.spotId !== null ? (
              <Link to={`/spots/${detail.spotId}`} className="text-accent underline underline-offset-2">
                {detail.locationText}
              </Link>
            ) : (
              detail.locationText
            )}
          </p>
          <dl className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-[1fr_1fr_auto]">
            <div className="rounded-sheet bg-fill px-4 py-3">
              <dt className="text-[0.8125rem] text-ink-muted">시작</dt>
              <dd className="text-sm font-extrabold tabular-nums sm:text-[0.9375rem]">
                {formatKstDateTime(detail.startAt)}
              </dd>
            </div>
            <div className="rounded-sheet bg-fill px-4 py-3">
              <dt className="text-[0.8125rem] text-ink-muted">끝</dt>
              <dd className="text-sm font-extrabold tabular-nums sm:text-[0.9375rem]">
                {formatKstDateTime(detail.endAt)}
              </dd>
            </div>
            <div className="col-span-2 rounded-sheet bg-fill px-4 py-3 sm:col-span-1">
              <dt className="text-[0.8125rem] text-ink-muted">남은 자리</dt>
              <dd className="font-mono text-[0.9375rem] font-extrabold tabular-nums">
                {seatsText(detail.remainingSeats, detail.capacity)}
              </dd>
            </div>
          </dl>
        </header>

        {detail.status === 'CANCELED' && (
          <Notice tone="danger" title="관리자가 행사를 취소했어요">
            <p>신청한 회원에게 취소 알림을 보냈어요. 결제한 금액은 환불돼요.</p>
          </Notice>
        )}

        <p className="whitespace-pre-line text-base leading-relaxed text-ink">{detail.description}</p>

        <section aria-labelledby="program-apply-info" className="flex flex-col gap-2">
          <h2 id="program-apply-info" className="text-[1.1875rem] font-extrabold tracking-[-0.02em] text-ink">
            신청 정보
          </h2>
          <dl className="rounded-sheet bg-fill py-2">
            <div className={BOARD_ROW_CLASS}>
              <dt className="text-ink-muted">신청 기간</dt>
              <dd className="font-semibold tabular-nums text-ink">
                {formatKstDateTime(detail.applyOpenAt)} ~ {formatKstDateTime(detail.applyCloseAt)}
              </dd>
            </div>
            <div className={BOARD_ROW_CLASS}>
              <dt className="text-ink-muted">정원</dt>
              <dd className="font-semibold tabular-nums text-ink">{detail.capacity}명</dd>
            </div>
            <div className={BOARD_ROW_CLASS}>
              <dt className="text-ink-muted">참가비</dt>
              <dd className="font-semibold tabular-nums text-ink">{feeText(detail.fee)}</dd>
            </div>
            <div className={BOARD_ROW_CLASS}>
              <dt className="text-ink-muted">결제 기한</dt>
              <dd className="font-semibold text-ink">신청 후 {detail.paymentDeadlineMinutes}분 안에 결제</dd>
            </div>
            {detail.overnight && (
              <div className={BOARD_ROW_CLASS}>
                <dt className="text-ink-muted">참가 자격</dt>
                <dd className="font-semibold text-ink">숙박 행사는 본인확인을 마친 회원만 신청할 수 있어요.</dd>
              </div>
            )}
          </dl>
        </section>
      </article>

      {detail.status !== 'CANCELED' && <ActionArea detail={detail} onChanged={onChanged} />}
    </div>
  );
}

type Busy = 'apply' | 'pay' | 'cancel' | 'alert' | null;

const FAIL_TITLES: Record<Exclude<Busy, null>, string> = {
  apply: '신청하지 못했어요',
  pay: '결제하지 못했어요',
  cancel: '신청을 취소하지 못했어요',
  alert: '빈자리 알림을 바꾸지 못했어요',
};

const HOUR_MS = 60 * 60 * 1000;
const REFUND_BEFORE_START_HOURS = 72;

function ActionArea({ detail, onChanged }: { detail: ProgramDetail; onChanged: () => void }) {
  const session = useSession();
  const [busy, setBusy] = useState<Busy>(null);
  const [error, setError] = useState<{ kind: Exclude<Busy, null>; apiError: ApiError } | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [soldOutDetail, setSoldOutDetail] = useState<ProgramDetail | null>(null);
  const [alertOn, setAlertOn] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [applyRunner] = useState(createIdempotentRunner);
  const [payRunner] = useState(createIdempotentRunner);

  const mine = detail.myApplication;
  const pending = mine?.status === 'PENDING_PAYMENT';
  const confirmed = mine?.status === 'CONFIRMED';
  const now = useNow(pending || confirmed);
  const dueAt = pending ? (mine.paymentDueAt ?? null) : null;
  const payExpired = dueAt !== null && now >= Date.parse(dueAt);

  useEffect(() => {
    if (!payExpired) return;
    const timer = window.setInterval(onChanged, 15_000);
    return () => window.clearInterval(timer);
  }, [payExpired, onChanged]);

  async function run(kind: Exclude<Busy, null>, call: () => Promise<void>): Promise<void> {
    if (busy !== null) return;
    setBusy(kind);
    setError(null);
    setNotice(null);
    try {
      await call();
    } catch (caught) {
      const apiError = caught instanceof ApiError ? caught : new ApiError(0, 'INTERNAL_ERROR', null);
      if (apiError.code === 'PROGRAM_SOLD_OUT') setSoldOutDetail(detail);
      if (apiError.code === 'PROGRAM_SEATS_AVAILABLE') setSoldOutDetail(null);
      setError({ kind, apiError });
      if (STALE_CODES.has(apiError.code)) onChanged();
    } finally {
      setBusy(null);
    }
  }

  const apply = () =>
    run('apply', async () => {
      await applyRunner.run({ programId: detail.programId }, (key) => applyProgram(detail.programId, key));
      setNotice('신청했어요. 결제 기한 안에 결제해 주세요.');
      onChanged();
    });

  const pay = () => {
    if (!mine) return;
    return run('pay', async () => {
      await payRunner.run({ applicationId: mine.applicationId }, (key) => payApplication(mine.applicationId, key));
      setNotice('결제를 마쳤어요. 신청이 확정됐어요.');
      onChanged();
    });
  };

  const cancel = async () => {
    if (!mine) return;
    await run('cancel', async () => {
      const response = await cancelApplication(mine.applicationId);
      setNotice(response.refunded ? '신청을 취소하고 환불했어요.' : '신청을 취소했어요.');
      onChanged();
    });
    setConfirmCancel(false);
  };

  const subscribe = () =>
    run('alert', async () => {
      await subscribeVacancyAlert(detail.programId);
      setAlertOn(true);
      setNotice('빈자리 알림을 신청했어요. 자리가 나면 알림이 가요.');
    });

  const unsubscribe = () =>
    run('alert', async () => {
      await unsubscribeVacancyAlert(detail.programId);
      setAlertOn(false);
      setNotice('빈자리 알림을 껐어요.');
    });

  if (session.status === 'loading') return null;

  const authenticated = session.status === 'authenticated';
  const active = pending || confirmed;
  const noSeats = detail.remainingSeats === 0 || soldOutDetail === detail;
  const canAlert = authenticated && !active && detail.status === 'OPEN' && (noSeats || alertOn);
  const refundDeadlineMs = Date.parse(detail.startAt) - REFUND_BEFORE_START_HOURS * HOUR_MS;
  const refundClosed = now > refundDeadlineMs;

  return (
    <section
      aria-label="신청"
      className="sticky bottom-(--bottom-nav-h) z-10 flex flex-col gap-3 rounded-sheet bg-surface p-4 shadow-raise"
    >
      {notice && (
        <p role="status" className="text-sm font-semibold text-accent">
          {notice}
        </p>
      )}
      {error && <ActionError title={FAIL_TITLES[error.kind]} error={error.apiError} />}

      {pending && (
        <>
          <Notice tone="warning" title="결제를 기다리고 있어요">
            {dueAt && (
              <p>
                {payExpired ? (
                  '결제 기한이 지났어요. 곧 이 자리가 다시 열려요.'
                ) : (
                  <>
                    남은 시간{' '}
                    <span className="font-mono text-base tabular-nums">
                      {countdownText(dueAt, now, detail.paymentDeadlineMinutes * 60)}
                    </span>
                  </>
                )}
              </p>
            )}
            <p className="mt-1">
              {detail.fee === 0 ? '참가비가 없는 행사예요.' : `참가비는 ${feeText(detail.fee)}이에요.`} 시연용 결제라서
              실제로 돈이 나가지 않아요.
            </p>
          </Notice>
          <Button fullWidth loading={busy === 'pay'} disabled={payExpired || busy !== null} onClick={() => void pay()}>
            결제하기
          </Button>
          <Button fullWidth variant="secondary" disabled={busy !== null} onClick={() => setConfirmCancel(true)}>
            신청 취소
          </Button>
        </>
      )}

      {confirmed && (
        <>
          <p className="text-base font-semibold text-accent">신청이 확정됐어요.</p>
          <p className="text-sm text-ink-muted">
            {refundClosed
              ? '환불 기한이 지나서 취소할 수 없어요.'
              : `행사 시작 ${REFUND_BEFORE_START_HOURS}시간 전인 ${formatKstDateTime(new Date(refundDeadlineMs).toISOString())}까지 취소하면 환불돼요.`}
          </p>
          <Button
            fullWidth
            variant="danger"
            disabled={refundClosed || busy !== null}
            disabledReason={refundClosed ? '환불 기한이 지나 취소할 수 없어요' : undefined}
            onClick={() => setConfirmCancel(true)}
          >
            신청 취소
          </Button>
        </>
      )}

      {!active && !authenticated && detail.status === 'OPEN' && (
        <Link to={withNext('/login', `/programs/${detail.programId}`)} className={`${LINK_BUTTON_CLASS} w-full`}>
          신청하기
        </Link>
      )}

      {!active && !authenticated && detail.status !== 'OPEN' && <ClosedApplyButton detail={detail} />}

      {!active && authenticated && detail.status !== 'OPEN' && <ClosedApplyButton detail={detail} />}

      {!active && authenticated && detail.status === 'OPEN' && (
        <>
          {noSeats && <p className="text-base font-semibold text-ink">남은 자리가 없어요</p>}
          {canAlert && !alertOn && noSeats && (
            <Button fullWidth loading={busy === 'alert'} disabled={busy !== null} onClick={() => void subscribe()}>
              빈자리 알림 받기
            </Button>
          )}
          <Button
            fullWidth
            variant={noSeats ? 'secondary' : 'primary'}
            loading={busy === 'apply'}
            disabled={busy !== null}
            onClick={() => void apply()}
          >
            신청하기
          </Button>
          {canAlert && alertOn && (
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm text-ink">빈자리 알림을 신청했어요</p>
              <Button
                variant="ghost"
                loading={busy === 'alert'}
                disabled={busy !== null}
                onClick={() => void unsubscribe()}
              >
                알림 끄기
              </Button>
            </div>
          )}
        </>
      )}

      {confirmCancel && (
        <Dialog title="신청을 취소할까요?" onClose={() => (busy === null ? setConfirmCancel(false) : undefined)}>
          <div className="flex flex-col gap-4">
            <p className="text-base text-ink">
              {confirmed
                ? '취소하면 결제한 금액은 환불되고 자리는 다른 사람이 신청할 수 있게 열려요.'
                : '취소하면 자리가 다시 열려요. 다시 신청하려면 남은 자리가 있어야 해요.'}
            </p>
            <div className="flex flex-col gap-2">
              <Button
                variant="danger"
                fullWidth
                loading={busy === 'cancel'}
                disabled={busy !== null}
                onClick={() => void cancel()}
              >
                신청 취소하기
              </Button>
              <Button variant="secondary" fullWidth disabled={busy !== null} onClick={() => setConfirmCancel(false)}>
                그대로 두기
              </Button>
            </div>
          </div>
        </Dialog>
      )}
    </section>
  );
}

const STALE_CODES = new Set([
  'PROGRAM_ALREADY_APPLIED',
  'PROGRAM_INVALID_STATE',
  'PROGRAM_NOT_IN_APPLY_PERIOD',
  'PROGRAM_PAYMENT_EXPIRED',
  'PROGRAM_SEATS_AVAILABLE',
]);

function ClosedApplyButton({ detail }: { detail: ProgramDetail }) {
  const reason =
    detail.status === 'UPCOMING' ? `신청은 ${formatKstDateTime(detail.applyOpenAt)}부터예요` : '신청이 마감됐어요';
  return (
    <Button fullWidth disabled disabledReason={reason}>
      신청하기
    </Button>
  );
}

function ActionError({ title, error }: { title: string; error: ApiError }) {
  return (
    <Notice tone="danger" title={title}>
      <p>{error.message}</p>
      {error.code === 'TRUST_LEVEL_INSUFFICIENT' && (
        <p className="mt-2">
          <Link
            to="/identity-verification"
            className="inline-flex min-h-11 items-center text-accent underline underline-offset-2"
          >
            본인확인하기
          </Link>
        </p>
      )}
      {error.status >= 500 && error.traceId && (
        <p className="mt-1 font-mono text-xs text-ink-muted">문의 번호 {error.traceId}</p>
      )}
    </Notice>
  );
}
