import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import {
  createCompanionReview,
  fetchPendingCompanionReviews,
  fetchReceivedCompanionReviews,
} from '../../api/companionReviews';
import type {
  PendingCompanionReview,
  PendingCompanionTarget,
  ReceivedCompanionReview,
  RevealedReceivedCompanionReview,
} from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { StatusText } from '../../components/StatusText';
import { Button } from '../../components/Button';
import { Checkbox } from '../../components/Checkbox';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Notice } from '../../components/Notice';
import { PageCard, PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { UnderlineTabs } from '../../components/UnderlineTabs';
import { TextArea } from '../../components/TextArea';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { daysUntilKst, formatKstDateTime } from '../../lib/datetime';
import { companionTagLabel, NEGATIVE_COMPANION_TAGS, POSITIVE_COMPANION_TAGS } from '../member/memberLabels';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { MemberReportDialog } from '../report/MemberReportDialog';
import { buttonClassName } from '../../components/buttonClass';

const COMMENT_MAX = 300;

type Tab = 'pending' | 'received';

const TAB_OPTIONS: ReadonlyArray<{ value: Tab; label: string }> = [
  { value: 'pending', label: '쓸 후기' },
  { value: 'received', label: '받은 후기' },
];

const REJOIN_OPTIONS: ReadonlyArray<ChoiceOption<'YES' | 'NO'>> = [
  { value: 'YES', label: '예' },
  { value: 'NO', label: '아니요' },
];

const LINK_BUTTON_CLASS = buttonClassName('secondary');

function tabFromSearch(search: string): Tab {
  return new URLSearchParams(search).get('tab') === 'received' ? 'received' : 'pending';
}

export function CompanionReviewsPage() {
  const session = useSession();
  const { pathname, search } = useLocation();
  const tab = tabFromSearch(search);
  const showSkeleton = useDelayedFlag(session.status === 'loading');

  useEffect(() => {
    document.title = '동행 후기 · 뫼와들';
  }, []);

  useEffect(() => {
    if (session.status === 'anonymous') navigate(withNext('/login', pathname + search), { replace: true });
  }, [session.status, pathname, search]);

  if (session.status !== 'authenticated' || !session.me) {
    return <PageLayout title="동행 후기">{showSkeleton ? <Skeleton className="h-40 w-full" /> : null}</PageLayout>;
  }

  return (
    <PageLayout
      title="동행 후기"

      tabs={
        <UnderlineTabs
          label="동행 후기 종류"
          options={TAB_OPTIONS}
          value={tab}
          onChange={(next) => navigate(next === 'received' ? `${pathname}?tab=received` : pathname, { replace: true })}
        />
      }
    >
      <div className="flex flex-col gap-4">
        {tab === 'pending' ? <PendingTab trustLevel={session.me.trustLevel} /> : <ReceivedTab />}
      </div>
    </PageLayout>
  );
}

type PendingResult =
  { attempt: number; items: PendingCompanionReview[] } | { attempt: number; error: { cause: unknown } };

function PendingTab({ trustLevel }: { trustLevel: number }) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<PendingResult | null>(null);
  const [writing, setWriting] = useState<{ review: PendingCompanionReview; target: PendingCompanionTarget } | null>(
    null,
  );
  const [done, setDone] = useState<string | null>(null);
  const eligible = trustLevel >= 1;

  useEffect(() => {
    if (!eligible) return;
    const controller = new AbortController();
    fetchPendingCompanionReviews(controller.signal).then(
      (items) => {
        if (!controller.signal.aborted) setResult({ attempt, items });
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) setResult({ attempt, error: { cause: caught } });
      },
    );
    return () => controller.abort();
  }, [eligible, attempt]);

  const current = result?.attempt === attempt ? result : null;
  const showSkeleton = useDelayedFlag(eligible && current === null);

  if (!eligible) {
    return (
      <div className="flex flex-col items-start gap-1">
        <p className="text-base text-ink">본인확인을 마치면 동행 후기를 쓸 수 있어요.</p>
        <Link
          to="/identity-verification"
          className="inline-flex min-h-11 items-center font-semibold text-accent underline underline-offset-2"
        >
          본인확인하기
        </Link>
      </div>
    );
  }
  if (current === null) return showSkeleton ? <Skeleton className="h-40 w-full" /> : null;
  if ('error' in current) {
    return (
      <div className="flex flex-col gap-3">
        <ErrorNotice title="쓸 후기를 불러오지 못했어요" error={current.error.cause} />
        <div>
          <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
            다시 불러오기
          </Button>
        </div>
      </div>
    );
  }

  return (
    <>
      {done && (
        <p role="status" className="text-sm font-semibold text-accent-deep">
          {done}
        </p>
      )}
      {current.items.length === 0 ? (
        <EmptyState
          framed
          title="쓸 후기가 없어요"
          description="함께 다녀온 베이스캠프가 완료되면 14일 안에 동행 후기를 쓸 수 있어요."
        />
      ) : (
        current.items.map((review) => (
          <PageCard key={review.basecampId} title={review.basecampTitle}>
            <p className="font-mono text-sm tabular-nums text-ink-muted">
              {formatKstDateTime(review.completedAt)} 완료 · {deadlineText(review.deadline)}
            </p>
            <ul className="flex flex-col">
              {review.targets.map((target) => (
                <li key={target.memberId} className="flex items-center justify-between gap-2 py-1">
                  <Link
                    to={`/members/${target.memberId}`}
                    className="inline-flex min-h-11 items-center text-base font-semibold text-accent underline underline-offset-2"
                  >
                    {target.nickname}
                  </Link>
                  <Button variant="secondary" onClick={() => setWriting({ review, target })}>
                    후기 쓰기
                  </Button>
                </li>
              ))}
            </ul>
          </PageCard>
        ))
      )}
      {writing && (
        <WriteDialog
          review={writing.review}
          target={writing.target}
          onClose={() => setWriting(null)}
          onSubmitted={() => {
            setDone(`${writing.target.nickname}님에게 동행 후기를 남겼어요.`);
            setWriting(null);
            setAttempt((value) => value + 1);
          }}
        />
      )}
    </>
  );
}

function deadlineText(deadline: string): string {
  const days = daysUntilKst(deadline);
  if (days < 0) return '기한이 지났어요';
  return days === 0 ? '오늘 마감' : `D-${days}`;
}

function WriteDialog({
  review,
  target,
  onClose,
  onSubmitted,
}: {
  review: PendingCompanionReview;
  target: PendingCompanionTarget;
  onClose: () => void;
  onSubmitted: () => void;
}) {
  const [rejoin, setRejoin] = useState<'YES' | 'NO' | null>(null);
  const [tags, setTags] = useState<string[]>([]);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<{ cause: unknown } | null>(null);
  const [commentError, setCommentError] = useState<string | undefined>();

  function toggleTag(tag: string, checked: boolean) {
    setTags((previous) => (checked ? [...previous, tag] : previous.filter((item) => item !== tag)));
  }

  async function submit() {
    if (rejoin === null || submitting) return;
    setSubmitting(true);
    setError(null);
    setCommentError(undefined);
    try {
      await createCompanionReview(review.basecampId, {
        revieweeId: target.memberId,
        rejoinWanted: rejoin === 'YES',
        tags,
        comment: comment.trim() || undefined,
      });
      onSubmitted();
    } catch (caught) {
      const own = caught instanceof ApiError ? caught.fieldErrors.find((item) => item.field === 'comment') : undefined;
      if (own) setCommentError(own.reason);
      else setError({ cause: caught });
      setSubmitting(false);
    }
  }

  return (
    <Dialog title={`${target.nickname}님에게 남길 동행 후기`} onClose={() => !submitting && onClose()}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-ink-muted">{review.basecampTitle}</p>
        {error && (
          <div role="alert">
            <ErrorNotice title="후기를 남기지 못했어요" error={error.cause} />
          </div>
        )}
        <ChoiceGroup
          legend="다시 동행하고 싶나요?"
          name="rejoinWanted"
          options={REJOIN_OPTIONS}
          value={rejoin}
          onChange={setRejoin}
          disabled={submitting}
        />
        <fieldset className="flex min-w-0 flex-col gap-1">
          <legend className="mb-1 text-sm font-medium text-ink">어울리는 태그 (선택)</legend>
          {[...POSITIVE_COMPANION_TAGS, ...NEGATIVE_COMPANION_TAGS].map((tag) => (
            <Checkbox
              key={tag}
              label={companionTagLabel(tag)}
              checked={tags.includes(tag)}
              disabled={submitting}
              onChange={(event) => toggleTag(tag, event.target.checked)}
            />
          ))}
        </fieldset>
        <TextArea
          label="한마디 (선택)"
          name="comment"
          maxLength={COMMENT_MAX}
          value={comment}
          disabled={submitting}
          error={commentError}
          onChange={(event) => setComment(event.target.value)}
        />
        <Notice tone="info" title="남긴 뒤에는 수정하거나 삭제할 수 없어요" />
        <div className="flex flex-wrap items-start justify-end gap-2">
          <Button variant="secondary" disabled={submitting} onClick={onClose}>
            닫기
          </Button>
          <Button
            loading={submitting}
            disabled={rejoin === null}
            disabledReason={rejoin === null ? '다시 동행하고 싶은지 골라 주세요.' : undefined}
            onClick={() => void submit()}
          >
            후기 남기기
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

type ReceivedState = {
  items: ReceivedCompanionReview[];
  page: number;
  hasNext: boolean;
  error: { cause: unknown } | null;
};

function ReceivedTab() {
  const [state, setState] = useState<ReceivedState | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [reportTarget, setReportTarget] = useState<RevealedReceivedCompanionReview | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchReceivedCompanionReviews(0, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted)
          setState({ items: page.content, page: page.page, hasNext: page.hasNext, error: null });
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) setState({ items: [], page: 0, hasNext: false, error: { cause: caught } });
      },
    );
    return () => controller.abort();
  }, [attempt]);

  const showSkeleton = useDelayedFlag(state === null);

  async function loadMore() {
    if (!state || loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await fetchReceivedCompanionReviews(state.page + 1);
      setState((previous) =>
        previous
          ? { items: [...previous.items, ...next.content], page: next.page, hasNext: next.hasNext, error: null }
          : previous,
      );
    } catch (caught) {
      setState((previous) => (previous ? { ...previous, error: { cause: caught } } : previous));
    } finally {
      setLoadingMore(false);
    }
  }

  if (state === null) return showSkeleton ? <Skeleton className="h-40 w-full" /> : null;

  return (
    <>
      {state.error && (
        <div className="flex flex-col gap-3">
          <ErrorNotice title="받은 후기를 불러오지 못했어요" error={state.error.cause} />
          {state.items.length === 0 && (
            <div>
              <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
                다시 불러오기
              </Button>
            </div>
          )}
        </div>
      )}
      {state.items.length === 0 && !state.error ? (
        <EmptyState framed title="받은 후기가 없어요" description="함께 다녀온 멤버가 후기를 남기면 여기에 보여요." />
      ) : (
        state.items.map((item, index) =>
          item.revealed ? (
            <PageCard key={item.reviewId} title={item.basecampTitle}>
              <div className="flex flex-wrap items-center gap-2">
                <Link
                  to={`/members/${item.reviewer.memberId}`}
                  className="inline-flex min-h-11 items-center text-base font-semibold text-accent underline underline-offset-2"
                >
                  {item.reviewer.nickname}
                </Link>
                <StatusText tone={item.rejoinWanted ? 'accent' : 'neutral'}>
                  {item.rejoinWanted ? '다시 동행하고 싶어요' : '다시 동행하고 싶지는 않아요'}
                </StatusText>
              </div>
              {item.tags.length > 0 && (
                <p className="text-sm text-ink-muted">{item.tags.map(companionTagLabel).join(' · ')}</p>
              )}
              {item.comment && <p className="whitespace-pre-line break-words text-base text-ink">{item.comment}</p>}
              <p className="font-mono text-sm tabular-nums text-ink-muted">{formatKstDateTime(item.createdAt)}</p>
              <div>
                <Button variant="ghost" onClick={() => setReportTarget(item)}>
                  후기 신고
                </Button>
              </div>
            </PageCard>
          ) : (
            <section
              key={`sealed-${item.basecampId}-${index}`}
              className="flex flex-col gap-3 rounded-sheet bg-fill p-4 lg:p-5"
            >
              <p className="text-base text-ink">내 후기를 남기면 상대가 쓴 후기를 볼 수 있어요.</p>
              <Link to={`/basecamps/${item.basecampId}`} className={`${LINK_BUTTON_CLASS} self-start`}>
                베이스캠프 보기
              </Link>
            </section>
          ),
        )
      )}
      {state.hasNext && (
        <div>
          <Button variant="secondary" loading={loadingMore} onClick={() => void loadMore()}>
            더 보기
          </Button>
        </div>
      )}
      {reportTarget && (
        <MemberReportDialog
          kind="REVIEW"
          targetMemberId={reportTarget.reviewer.memberId}
          targetNickname={reportTarget.reviewer.nickname}
          basecampId={reportTarget.basecampId}
          companionReviewId={reportTarget.reviewId}
          onClose={() => setReportTarget(null)}
        />
      )}
    </>
  );
}
