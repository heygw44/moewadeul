import { useEffect } from 'react';
import { fetchMemoryFeed } from '../../api/memories';
import type { MemoryFeedItem } from '../../api/types';
import { Link } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { HubLayout } from '../../components/HubLayout';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatLocalDate } from '../../lib/datetime';
import { SECONDARY_LINK_CLASS } from '../community/communityLabels';
import { useSession } from '../member/session';
import { usePagedList } from '../program/usePagedList';
import { LocationLabel } from '../spot/LocationLabel';
import { nightsLabel } from './memoryLabels';
import { MemoryLikeButton } from './MemoryLikeButton';
import { MemoryPhotoCarousel } from './MemoryPhotoCarousel';

const FEED_PAGE_SIZE = 10;

// 모두에게 공개된 기록을 최신순으로 이어서 보여 준다. 좋아요 상태가 로그인에 따라 달라서 세션을 확인한 뒤에 받는다.
export function MemoryFeedPage() {
  const session = useSession();
  const sessionReady = session.status !== 'loading';
  const feed = usePagedList<MemoryFeedItem, string>(sessionReady, session.status, (_filter, page, signal) =>
    fetchMemoryFeed(page, FEED_PAGE_SIZE, signal),
  );
  const { current } = feed;
  const showSkeleton = useDelayedFlag(current === null);

  useEffect(() => {
    document.title = '발자국 · 뫼와들';
  }, []);

  let body;
  if (current === null) {
    body = (
      <div aria-busy="true">
        {showSkeleton && (
          <div className="flex flex-col gap-4">
            <Skeleton className="aspect-square w-full" />
            <Skeleton className="h-16 w-full" />
          </div>
        )}
      </div>
    );
  } else if (current.error !== null && current.items.length === 0) {
    body = (
      <div role="alert" className="flex flex-col gap-3">
        <Notice tone="danger" title="발자국을 불러오지 못했어요">
          {current.error}
        </Notice>
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
        title="공개된 발자국이 아직 없어요"
        description="내가 다녀온 곳을 기록하고 공개하면 여기에 보여요."
        action={
          <Link to="/me/memories" className={SECONDARY_LINK_CLASS}>
            내 발자국 지도
          </Link>
        }
      />
    );
  } else {
    body = (
      <>
        <ul className="flex flex-col gap-4">
          {current.items.map((item) => (
            <li key={item.memoryId}>
              <MemoryFeedCard item={item} />
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
      title="발자국"
      width="narrow"
      actions={
        <Link to="/me/memories" className={SECONDARY_LINK_CLASS}>
          내 발자국 지도
        </Link>
      }
    >
      {body}
    </HubLayout>
  );
}

// 링크 안에 버튼이나 다른 링크를 넣지 않으려고, 닉네임·장소 줄, 사진, 본문 링크, 좋아요 줄을 서로 형제로 둔다.
function MemoryFeedCard({ item }: { item: MemoryFeedItem }) {
  return (
    <article className="flex flex-col gap-3 rounded-control border border-contour bg-card p-4">
      <div className="flex flex-col gap-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
          <Link
            to={`/members/${item.author.memberId}/memories`}
            className="inline-flex min-h-11 items-center font-semibold text-ink underline-offset-2 hover:underline"
          >
            {item.author.nickname}
          </Link>
          {item.spot && (
            <>
              <span aria-hidden="true" className="text-ink-muted">
                ·
              </span>
              <Link
                to={`/spots/${item.spot.spotId}`}
                className="inline-flex min-h-11 items-center gap-1 text-forest underline underline-offset-2"
              >
                <Icon name="map" size={14} />
                {item.spot.name}
              </Link>
            </>
          )}
        </div>
        <div className="flex flex-col">
          <p className="text-xs text-ink-muted">대략 위치</p>
          <LocationLabel position={{ lat: item.approxLat, lng: item.approxLng }} />
        </div>
      </div>
      <MemoryPhotoCarousel images={item.images} owner={item.author.nickname} />
      <Link to={`/memories/${item.memoryId}`} className="flex flex-col gap-1 hover:underline">
        <span className="text-sm text-ink-muted">
          <time dateTime={item.visitedDate}>{formatLocalDate(item.visitedDate)}</time>
          <span aria-hidden="true"> · </span>
          {nightsLabel(item.nights)}
        </span>
        {item.excerpt && <span className="line-clamp-3 whitespace-pre-line break-words text-base text-ink">{item.excerpt}</span>}
        {!item.excerpt && <span className="text-sm font-semibold text-forest">기록 보기</span>}
      </Link>
      <div>
        <MemoryLikeButton memoryId={item.memoryId} likedByMe={item.likedByMe === true} likeCount={item.likeCount} />
      </div>
    </article>
  );
}
