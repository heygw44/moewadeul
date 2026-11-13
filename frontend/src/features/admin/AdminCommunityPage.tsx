import { Fragment, useState } from 'react';
import type { ReactNode } from 'react';
import {
  fetchAdminCommunityComments,
  fetchAdminCommunityPosts,
  hideCommunityComment,
  hideCommunityPost,
  restoreCommunityComment,
  restoreCommunityPost,
} from '../../api/admin';
import type {
  AdminCommunityComment,
  AdminCommunityPost,
  AdminCommunityStatus,
  Page,
  PageRequest,
} from '../../api/types';
import { navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { usePagedList } from '../../components/usePagedList';
import { AdminPage } from './AdminGate';
import { AdminSegments } from './AdminSegments';
import { CommentCard, PostCard } from './AdminCommunityCards';
import type { ActionResult, CardProps } from './AdminCommunityCards';
import { EMPTY_COPY, STATUS_TABS, isStaleItemError } from './adminCommunityLabels';
import type { AdminCommunityItem, ReviewAction } from './adminCommunityLabels';
import type { CommunityNoun } from '../community/communityLabels';

type Kind = 'post' | 'comment';

const LIST_PATH = '/admin/community';

const KINDS: ReadonlyArray<{ value: Kind; label: CommunityNoun }> = [
  { value: 'post', label: '글' },
  { value: 'comment', label: '댓글' },
];

interface KindConfig<T extends AdminCommunityItem> {
  noun: CommunityNoun;
  itemId: (item: T) => number;
  fetchPage: (status: AdminCommunityStatus, request: PageRequest, signal?: AbortSignal) => Promise<Page<T>>;
  hide: (id: number) => Promise<unknown>;
  restore: (id: number) => Promise<unknown>;
  renderCard: (props: CardProps<T>) => ReactNode;
}

const POSTS: KindConfig<AdminCommunityPost> = {
  noun: '글',
  itemId: (item) => item.postId,
  fetchPage: (status, { page }, signal) => fetchAdminCommunityPosts({ status, page }, signal),
  hide: hideCommunityPost,
  restore: restoreCommunityPost,
  renderCard: (props) => <PostCard {...props} />,
};

const COMMENTS: KindConfig<AdminCommunityComment> = {
  noun: '댓글',
  itemId: (item) => item.commentId,
  fetchPage: (status, { page }, signal) => fetchAdminCommunityComments({ status, page }, signal),
  hide: hideCommunityComment,
  restore: restoreCommunityComment,
  renderCard: (props) => <CommentCard {...props} />,
};

export function AdminCommunityPage() {
  return (
    <AdminPage title="커뮤니티 검토" current={LIST_PATH}>
      <CommunityReview />
    </AdminPage>
  );
}

function parseReviewView(search: string): { kind: Kind; tab: AdminCommunityStatus } {
  const params = new URLSearchParams(search);
  return {
    kind: params.get('kind') === 'comment' ? 'comment' : 'post',
    tab: params.get('tab') === 'hidden' ? 'HIDDEN' : 'PENDING_REVIEW',
  };
}

function reviewViewPath(kind: Kind, tab: AdminCommunityStatus): string {
  return `${LIST_PATH}?kind=${kind}${tab === 'HIDDEN' ? '&tab=hidden' : ''}`;
}

function CommunityReview() {
  const { search } = useLocation();
  const { kind, tab } = parseReviewView(search);

  return (
    <section className="flex flex-col gap-4" aria-label="커뮤니티 검토 목록">
      <div className="flex flex-wrap gap-3">
        <AdminSegments
          label="검토 대상"
          segments={KINDS}
          value={kind}
          onChange={(next) => navigate(reviewViewPath(next, tab), { replace: true })}
        />
        <AdminSegments
          label="목록 종류"
          segments={STATUS_TABS}
          value={tab}
          onChange={(next) => navigate(reviewViewPath(kind, next), { replace: true })}
        />
      </div>
      {kind === 'post' ? (
        <ReviewList key={`post:${tab}`} config={POSTS} tab={tab} />
      ) : (
        <ReviewList key={`comment:${tab}`} config={COMMENTS} tab={tab} />
      )}
    </section>
  );
}

function ReviewList<T extends AdminCommunityItem>({
  config,
  tab,
}: {
  config: KindConfig<T>;
  tab: AdminCommunityStatus;
}) {
  const { current, loadingMore, loadMore, retry, reload, refreshLoaded, updateItems } = usePagedList(
    true,
    tab,
    config.fetchPage,
    config.itemId,
  );
  const [failure, setFailure] = useState<{ id: number; error: unknown } | null>(null);
  const showSkeleton = useDelayedFlag(current === null);
  const orphanFailure =
    failure !== null && current !== null && !current.items.some((item) => config.itemId(item) === failure.id)
      ? failure
      : null;

  async function perform(item: T, action: ReviewAction): Promise<ActionResult> {
    const id = config.itemId(item);
    setFailure(null);
    try {
      await (action === 'hide' ? config.hide(id) : config.restore(id));
    } catch (caught) {
      setFailure({ id, error: caught });
      if (isStaleItemError(caught)) reload();
      return 'failed';
    }
    updateItems((items) => items.filter((candidate) => config.itemId(candidate) !== id));
    reload();
    return 'done';
  }

  return (
    <>
      {current?.error && (
        <div role="alert" className="flex flex-col gap-3">
          <ErrorNotice
            title={`${config.noun}을 불러오지 못했어요`}
            error={current.error}
            traceId={current.traceId ?? undefined}
          />
          {current.failedOn === 'first' && (
            <div>
              <Button variant="secondary" onClick={retry}>
                다시 불러오기
              </Button>
            </div>
          )}
        </div>
      )}
      {orphanFailure && (
        <div role="alert">
          <ErrorNotice title="상태를 바꾸지 못했어요" error={orphanFailure.error} />
        </div>
      )}
      {current === null && showSkeleton && <Skeleton className="h-40 w-full" />}
      {current !== null && current.items.length === 0 && !current.error && (
        <EmptyState framed {...EMPTY_COPY[config.noun][tab]} />
      )}
      {current !== null && current.items.length > 0 && (
        <ul className="flex flex-col gap-3" aria-label={`${config.noun} 목록`}>
          {current.items.map((item) => {
            const id = config.itemId(item);
            return (
              <Fragment key={id}>
                {config.renderCard({
                  item,
                  tab,
                  failure: failure?.id === id ? failure.error : null,
                  onAction: (action) => perform(item, action),
                  onReload: refreshLoaded,
                })}
              </Fragment>
            );
          })}
        </ul>
      )}
      {current?.hasNext && (
        <div>
          <Button variant="secondary" loading={loadingMore} onClick={() => void loadMore()}>
            더 보기
          </Button>
        </div>
      )}
    </>
  );
}
