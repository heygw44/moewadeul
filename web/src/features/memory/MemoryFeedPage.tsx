import { useEffect, useState } from 'react';
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

const FEED_PAGE_SIZE = 24;
// 커뮤니티·행사와 같은 넓은 열을 써서, 넓은 화면에서는 칸이 지나치게 커지지 않게 열 수를 늘린다.
const FEED_GRID_CLASS = 'grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5';

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
    document.title = '피드 · 뫼와들';
  }, []);

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
        <Notice tone="danger" title="기록을 불러오지 못했어요">
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

// 칸 하나는 기록으로 가는 링크 하나다. 링크 안에 버튼이나 다른 링크를 두지 않는다.
// 덮개는 마우스를 올리거나 키보드 포커스가 올 때만 보이고, 같은 내용을 링크의 aria-label이 읽어 준다.
function MemoryFeedCell({ item }: { item: MemoryFeedItem }) {
  // 조회 URL이 만료되는 등 사진을 못 불러오면 사진 없는 칸 모양으로 바꾼다.
  const [imageFailed, setImageFailed] = useState(false);
  const photoCount = item.images.length;
  const cover = imageFailed ? undefined : item.images[0];
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
      {cover ? (
        <img
          src={cover.url}
          alt=""
          loading="lazy"
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
