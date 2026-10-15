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
// 카드 높이가 저마다 달라서 열을 엇갈려 쌓는다. 폰은 한 열, 넓어지면 두세 열이다.
const FEED_GRID_CLASS = 'columns-1 gap-4 sm:columns-2 lg:columns-3';

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
            {Array.from({ length: 6 }, (_, index) => (
              <Skeleton key={index} className={`mb-4 w-full break-inside-avoid rounded-sheet ${index % 2 === 0 ? 'h-72' : 'h-48'}`} />
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
            <li key={item.memoryId} className="mb-4 break-inside-avoid">
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

// 카드 하나는 기록으로 가는 링크 하나다. 링크 안에 버튼이나 다른 링크를 두지 않는다.
// 사진이 있으면 첫 사진을 위에 두고, 사진이 없거나 못 불러오면 숲색 글 카드로 바꾼다. 위치는 대략 좌표만 받으므로 적지 않는다.
function MemoryFeedCell({ item }: { item: MemoryFeedItem }) {
  // 조회 URL이 만료되는 등 사진을 못 불러오면 글 카드 모양으로 바꾼다.
  const [imageFailed, setImageFailed] = useState(false);
  const photoCount = item.images.length;
  const cover = imageFailed ? undefined : item.images[0];
  const textOnly = cover === undefined;
  const title = item.spot?.name ?? `${formatLocalDate(item.visitedDate)} 기록`;
  const muted = textOnly ? 'text-brand-ink' : 'text-ink-muted';

  return (
    <Link
      to={`/memories/${item.memoryId}`}
      className={`group block overflow-hidden rounded-sheet transition-colors ${textOnly ? 'bg-brand text-surface hover:bg-brand-deep' : 'bg-surface text-ink hover:bg-fill'}`}
    >
      {cover && (
        <span className="relative block bg-fill">
          <img
            src={cover.url}
            alt=""
            loading="lazy"
            onError={() => setImageFailed(true)}
            className="block max-h-[420px] w-full object-cover"
          />
          {photoCount > 1 && (
            <span className="absolute top-2 right-2 inline-flex items-center gap-1 rounded-control bg-surface px-1.5 py-0.5 text-xs font-semibold text-ink tabular-nums">
              <Icon name="photos" size={14} />
              <span className="sr-only">사진</span>
              {photoCount}
              <span className="sr-only">장</span>
            </span>
          )}
        </span>
      )}
      <span className="flex flex-col gap-1.5 px-[18px] pt-4 pb-[18px]">
        <span className={`flex items-center gap-2 text-sm ${muted}`}>
          <span
            aria-hidden="true"
            className={`grid size-6 shrink-0 place-items-center rounded-full text-xs font-bold ${textOnly ? 'bg-brand-deep text-surface' : 'bg-fill-strong text-ink'}`}
          >
            {item.author.nickname.slice(0, 1)}
          </span>
          <span className="min-w-0 truncate">
            {item.author.nickname} · {formatLocalDate(item.visitedDate)}
          </span>
        </span>
        <span className="text-lg leading-snug font-bold">{title}</span>
        {item.excerpt && <span className={`line-clamp-4 text-[15px] ${muted}`}>{item.excerpt}</span>}
        <span className={`flex gap-3 text-sm tabular-nums ${muted}`}>
          {item.nights > 0 && <span>{item.nights}박</span>}
          <span>좋아요 {item.likeCount}</span>
        </span>
      </span>
    </Link>
  );
}
