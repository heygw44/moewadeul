import { ApiError } from '../../api/client';
import { fetchMemberCompanionReviews } from '../../api/companionReviews';
import type { Page, PublicCompanionReview } from '../../api/types';
import { UnverifiedNote } from '../../app/session/GatedButton';
import { useSession } from '../../app/session/session';
import { Button } from '../../components/Button';
import { ErrorNotice } from '../../components/ErrorNotice';
import { PageCard } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { usePagedList } from '../../components/usePagedList';
import { formatKstDateTime } from '../../lib/datetime';
import { companionTagLabel } from './memberLabels';

const REVIEW_TITLE = '받은 후기';

const itemKey = () => null;

const NO_REVIEWS: Page<PublicCompanionReview> = { content: [], page: 0, size: 0, hasNext: false };

async function fetchReviewPage(memberId: number, page: number, signal?: AbortSignal) {
  try {
    return await fetchMemberCompanionReviews(memberId, page, signal);
  } catch (caught) {
    if (caught instanceof ApiError && caught.code === 'NOT_FOUND') return NO_REVIEWS;
    throw caught;
  }
}

export function ReceivedReviewsCard({ memberId }: { memberId: number }) {
  const session = useSession();
  if (session.status === 'loading') {
    return (
      <PageCard title={REVIEW_TITLE}>
        <Skeleton className="h-16 w-full" />
      </PageCard>
    );
  }
  if (session.me?.status === 'UNVERIFIED') {
    return (
      <PageCard title={REVIEW_TITLE}>
        <UnverifiedNote action="이 회원이 받은 후기를 읽을 수 있어요." />
      </PageCard>
    );
  }
  return <ReviewList memberId={memberId} />;
}

function ReviewList({ memberId }: { memberId: number }) {
  const { current: state, loadingMore, loadMore, retry } = usePagedList(true, memberId, fetchReviewPage, itemKey);
  const showSkeleton = useDelayedFlag(state === null);

  let body;
  if (state === null) {
    body = showSkeleton ? <Skeleton className="h-16 w-full" /> : null;
  } else {
    body = (
      <>
        {state.error && (
          <div className="flex flex-col gap-3">
            <ErrorNotice
              title="받은 후기를 불러오지 못했어요"
              error={state.error}
              traceId={state.traceId ?? undefined}
            />
            {state.items.length === 0 && (
              <div>
                <Button variant="secondary" onClick={retry}>
                  다시 불러오기
                </Button>
              </div>
            )}
          </div>
        )}
        {state.items.length === 0 && !state.error && (
          <p className="text-base text-ink-muted">
            아직 받은 후기가 없어요. 함께 다녀온 멤버가 후기를 남기면 여기에 보여요.
          </p>
        )}
        {state.items.length > 0 && (
          <ul className="flex flex-col gap-5">
            {state.items.map((review, index) => (
              <li key={index} className="flex flex-col gap-1">
                {review.tags.length > 0 && (
                  <p className="text-sm text-ink-muted">{review.tags.map(companionTagLabel).join(' · ')}</p>
                )}
                {review.comment && (
                  <p className="whitespace-pre-line break-words text-base text-ink">{review.comment}</p>
                )}
                {review.tags.length === 0 && !review.comment && (
                  <p className="text-sm text-ink-muted">태그나 한마디 없이 남긴 후기예요.</p>
                )}
                <p className="font-mono text-sm tabular-nums text-ink-muted">{formatKstDateTime(review.createdAt)}</p>
              </li>
            ))}
          </ul>
        )}
        {state.hasNext && (
          <div>
            <Button variant="secondary" loading={loadingMore} onClick={() => void loadMore()}>
              더 보기
            </Button>
          </div>
        )}
      </>
    );
  }

  return <PageCard title={REVIEW_TITLE}>{body}</PageCard>;
}
