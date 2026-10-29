import { useState } from 'react';
import { fetchMemoryFeed } from '../../api/memories';
import type { MemoryFeedItem } from '../../api/types';
import { Link } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { HubLayout } from '../../components/HubLayout';
import { Icon } from '../../components/icons';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatLocalDate } from '../../lib/datetime';
import { SECONDARY_LINK_CLASS } from '../../components/buttonClass';
import { useViewerEpoch } from '../member/useViewerEpoch';
import { usePagedList } from '../../components/usePagedList';
import { useDocumentTitle } from '../../components/useDocumentTitle';

const FEED_PAGE_SIZE = 24;
const FEED_GRID_CLASS = 'grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5';

export function MemoryFeedPage() {
  const viewerEpoch = useViewerEpoch();
  const feed = usePagedList<MemoryFeedItem, number>(true, viewerEpoch, (_filter, page, signal) =>
    fetchMemoryFeed(page, FEED_PAGE_SIZE, signal),
  );
  const { current } = feed;
  const showSkeleton = useDelayedFlag(current === null);

  useDocumentTitle('피드 · 뫼와들');

  let body;
  if (current === null) {
    body = (
      <div aria-busy="true">
        {showSkeleton && (
          <div className={FEED_GRID_CLASS}>
            {Array.from({ length: 15 }, (_, index) => (
              <Skeleton key={index} className="aspect-square w-full" />
            ))}
          </div>
        )}
      </div>
    );
  } else if (current.error !== null && current.items.length === 0) {
    body = (
      <div role="alert" className="flex flex-col gap-3">
        <ErrorNotice title="기록을 불러오지 못했어요" error={current.error} traceId={current.traceId ?? undefined} />
        <div>
          <Button variant="secondary" onClick={feed.retry}>
            다시 불러오기
          </Button>
        </div>
      </div>
    );
  } else if (current.items.length === 0) {
    body = (
      <EmptyState
        framed
        title="공개된 기록이 아직 없어요"
        description="다녀온 곳을 기록으로 남기고 공개하면 여기에 보여요."
        action={
          <Link to="/me/memories" className={SECONDARY_LINK_CLASS}>
            내 기록 지도
          </Link>
        }
      />
    );
  } else {
    body = (
      <>
        <ul className={FEED_GRID_CLASS}>
          {current.items.map((item) => (
            <li key={item.memoryId}>
              <MemoryFeedCell item={item} />
            </li>
          ))}
        </ul>
        {current.error && (
          <p role="alert" className="text-sm text-danger">
            {current.error}
          </p>
        )}
        {current.hasNext && (
          <div>
            <Button variant="secondary" loading={feed.loadingMore} onClick={() => void feed.loadMore()}>
              더 보기
            </Button>
          </div>
        )}
      </>
    );
  }

  return (
    <HubLayout
      title="피드"
      actions={
        <Link to="/me/memories" className={SECONDARY_LINK_CLASS}>
          내 기록 지도
        </Link>
      }
    >
      {body}
    </HubLayout>
  );
}

function MemoryFeedCell({ item }: { item: MemoryFeedItem }) {
  const [imageFailed, setImageFailed] = useState(false);
  const photoCount = item.imageCount;
  const coverUrl = imageFailed ? undefined : item.thumbnailUrl;
  const label = [
    `${item.author.nickname}님의 기록`,
    formatLocalDate(item.visitedDate),
    photoCount > 0 ? `사진 ${photoCount}장` : null,
    `좋아요 ${item.likeCount}개`,
  ]
    .filter(Boolean)
    .join(', ');

  return (
    <Link
      to={`/memories/${item.memoryId}`}
      aria-label={label}
      className="group relative block aspect-square overflow-hidden rounded-sheet bg-fill focus-visible:outline-offset-[-2px]"
    >
      {coverUrl ? (
        <img
          src={coverUrl}
          alt=""
          loading="lazy"
          decoding="async"
          onError={() => setImageFailed(true)}
          className="h-full w-full object-cover"
        />
      ) : (
        <div className="flex h-full w-full flex-col gap-1 rounded-sheet bg-fill p-3 pr-9">
          {item.excerpt && <p className="line-clamp-4 break-keep text-sm text-ink">{item.excerpt}</p>}
          <p className="mt-auto text-xs text-ink-muted">{formatLocalDate(item.visitedDate)}</p>
        </div>
      )}
      {photoCount > 1 && (
        <span aria-hidden="true" className="absolute right-1 top-1 rounded-control bg-ink/40 p-0.5 text-white">
          <Icon name="photos" size={16} />
        </span>
      )}
      <div
        aria-hidden="true"
        className="absolute inset-0 flex flex-col items-center justify-center gap-1 bg-ink/60 px-2 text-white opacity-0 transition-opacity duration-150 group-hover:opacity-100 group-focus-visible:opacity-100 motion-reduce:transition-none"
      >
        <span className="inline-flex items-center gap-1 font-mono text-sm tabular-nums">
          <Icon name="heart" size={16} />
          {item.likeCount}
        </span>
        <span className="max-w-full truncate text-xs">{item.author.nickname}</span>
      </div>
    </Link>
  );
}
