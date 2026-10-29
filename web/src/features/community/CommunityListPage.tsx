import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { listPosts } from '../../api/community';
import { fetchSpotDetail } from '../../api/spots';
import type { CommunityPostSummary, CommunitySearchType } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { AsideSection, HubLayout, ListPanel } from '../../components/HubLayout';
import { Icon } from '../../components/icons';
import { ErrorNotice } from '../../components/ErrorNotice';
import { SelectField } from '../../components/SelectField';
import { Skeleton } from '../../components/Skeleton';
import { TextField } from '../../components/TextField';
import { UnderlineTabs } from '../../components/UnderlineTabs';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatRelativeKst } from '../../lib/datetime';
import { useSession } from '../member/session';
import { usePagedList } from '../program/usePagedList';
import { LINK_BUTTON_CLASS, parseSpotId, UNVERIFIED_WRITE_REASON } from './communityLabels';

type ListTab = 'all' | 'popular';
type ListQuery = {
  spotId: number | null;
  tab: ListTab;
  searchType: CommunitySearchType;
  keyword: string | null;
};

const KEYWORD_MIN = 2;
const KEYWORD_MAX = 50;

const SEARCH_TYPES: ReadonlyArray<{ value: CommunitySearchType; label: string }> = [
  { value: 'TITLE_CONTENT', label: '제목+내용' },
  { value: 'TITLE', label: '제목' },
  { value: 'CONTENT', label: '내용' },
  { value: 'AUTHOR', label: '글쓴이' },
];

function parseSearchType(raw: string | null): CommunitySearchType {
  return SEARCH_TYPES.find((option) => option.value === raw)?.value ?? 'TITLE_CONTENT';
}

function parseKeyword(raw: string | null): string | null {
  const keyword = raw?.trim() ?? '';
  return keyword.length >= KEYWORD_MIN && keyword.length <= KEYWORD_MAX ? keyword : null;
}

const TABS: ReadonlyArray<{ value: ListTab; label: string }> = [
  { value: 'all', label: '전체 글' },
  { value: 'popular', label: '인기글' },
];

function parseListQuery(search: string): ListQuery {
  const params = new URLSearchParams(search);
  return {
    spotId: parseSpotId(params.get('spotId')),
    tab: params.get('tab') === 'popular' ? 'popular' : 'all',
    searchType: parseSearchType(params.get('searchType')),
    keyword: parseKeyword(params.get('keyword')),
  };
}

function listPath({ spotId, tab, searchType, keyword }: ListQuery): string {
  const params = new URLSearchParams();
  if (tab === 'popular') params.set('tab', 'popular');
  if (spotId !== null) params.set('spotId', String(spotId));
  if (keyword !== null) {
    params.set('searchType', searchType);
    params.set('keyword', keyword);
  }
  const query = params.toString();
  return query ? `/community?${query}` : '/community';
}

export function CommunityListPage() {
  const { search } = useLocation();
  const query = parseListQuery(search);
  const filter = {
    spotId: query.spotId ?? undefined,
    popular: query.tab === 'popular',
    searchType: query.searchType,
    keyword: query.keyword ?? undefined,
  };
  const posts = usePagedList(true, filter, listPosts);
  const { current } = posts;
  const showSkeleton = useDelayedFlag(current === null);
  const firstPageFailed = current !== null && current.error !== null && current.items.length === 0;

  useEffect(() => {
    document.title = '커뮤니티 · 뫼와들';
  }, []);

  const goTo = (next: Partial<ListQuery>) => navigate(listPath({ ...query, ...next }));

  return (
    <HubLayout
      title="커뮤니티"
      actions={<WriteAction spotId={query.spotId} />}
      tabs={<UnderlineTabs label="글 모아 보기" options={TABS} value={query.tab} onChange={(tab) => goTo({ tab })} />}
      aside={<CommunityAside />}
    >
      {query.spotId !== null && (
        <SpotFilterLine
          key={query.spotId}
          spotId={query.spotId}
          onRemove={() => navigate(listPath({ ...query, spotId: null }), { replace: true })}
        />
      )}
      {firstPageFailed && (
        <div role="alert" className="flex flex-col gap-3">
          <ErrorNotice title="글을 불러오지 못했어요" error={current.error} traceId={current.traceId ?? undefined} />
          <div>
            <Button variant="secondary" onClick={posts.retry}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}
      <SearchBar
        key={`${query.searchType}|${query.keyword ?? ''}`}
        searchType={query.searchType}
        keyword={query.keyword}
        onSearch={(searchType, keyword) => goTo({ searchType, keyword })}
      />
      <div aria-busy={current === null} className="flex flex-col gap-4">
        <ListPanel>
          {current === null ? (
            showSkeleton ? (
              Array.from({ length: 5 }, (_, index) => (
                <li key={index} className="flex flex-col gap-2 px-4 py-5">
                  <Skeleton className="h-5 w-2/3" />
                  <Skeleton className="h-4 w-full" />
                  <Skeleton className="h-4 w-1/3" />
                </li>
              ))
            ) : null
          ) : firstPageFailed ? null : current.items.length === 0 ? (
            <li>
              {query.keyword !== null ? (
                <SearchEmpty onClear={() => goTo({ keyword: null })} />
              ) : query.tab === 'popular' ? (
                <PopularEmpty />
              ) : (
                <CommunityEmpty />
              )}
            </li>
          ) : (
            current.items.map((item) => <PostRow key={item.postId} item={item} />)
          )}
        </ListPanel>
        {!firstPageFailed && current?.error && (
          <p role="alert" className="text-sm text-danger">
            {current.error}
          </p>
        )}
        {current?.hasNext && (
          <div>
            <Button variant="secondary" loading={posts.loadingMore} onClick={() => void posts.loadMore()}>
              더 보기
            </Button>
          </div>
        )}
      </div>
    </HubLayout>
  );
}

type SearchBarProps = {
  searchType: CommunitySearchType;
  keyword: string | null;
  onSearch: (searchType: CommunitySearchType, keyword: string | null) => void;
};

function SearchBar({ searchType, keyword, onSearch }: SearchBarProps) {
  const [type, setType] = useState<CommunitySearchType>(searchType);
  const [text, setText] = useState(keyword ?? '');
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = text.trim();
    if (trimmed !== '' && trimmed.length < KEYWORD_MIN) {
      setError('검색어를 두 글자 이상 적어 주세요.');
      return;
    }
    setError(null);
    onSearch(type, trimmed === '' ? null : trimmed);
  }

  return (
    <form role="search" aria-label="글 검색" onSubmit={handleSubmit} className="flex flex-col gap-1">
      <div className="flex items-start gap-2 [&_input]:rounded-control [&_input]:bg-fill [&_input]:pl-5 [&_input:not([aria-invalid])]:border-transparent [&_select]:rounded-control [&_select]:border-transparent [&_select]:bg-fill [&_select]:pl-5">
        <SelectField
          label="검색 대상"
          hideLabel
          options={SEARCH_TYPES}
          value={type}
          onChange={(event) => setType(parseSearchType(event.target.value))}
          className="w-32 shrink-0"
        />
        <TextField
          label="검색어"
          hideLabel
          type="search"
          maxLength={KEYWORD_MAX}
          value={text}
          invalid={error !== null}
          aria-describedby={error ? 'community-search-error' : undefined}
          onChange={(event) => {
            setText(event.target.value);
            setError(null);
          }}
          placeholder="두 글자 이상"
          className="min-w-0 flex-1"
        />
        <Button type="submit" variant="secondary">
          검색
        </Button>
      </div>
      {error && (
        <p id="community-search-error" className="flex items-start gap-1 text-sm text-danger">
          <Icon name="alert" size={16} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </p>
      )}
      {!error && keyword !== null && <p className="text-sm text-ink-muted">최근 글 1만 개 안에서 찾았어요.</p>}
    </form>
  );
}

function SearchEmpty({ onClear }: { onClear: () => void }) {
  return (
    <div className="flex flex-col items-start gap-3 p-6">
      <p className="text-lg font-semibold text-ink">검색 결과가 없어요</p>
      <p className="text-sm text-ink-muted">검색어를 바꾸거나 검색 대상을 넓혀 주세요.</p>
      <Button variant="secondary" onClick={onClear}>
        검색어 지우기
      </Button>
    </div>
  );
}

function PopularEmpty() {
  return (
    <div className="flex flex-col items-start gap-3 p-6">
      <p className="text-lg font-semibold text-ink">아직 인기글이 없어요</p>
      <p className="text-sm text-ink-muted">좋아요를 5개 이상 받은 글이 인기글이 돼요.</p>
    </div>
  );
}

function CommunityEmpty() {
  return (
    <div className="flex flex-col items-start gap-3 p-6">
      <p className="text-lg font-semibold text-ink">아직 올라온 글이 없어요</p>
      <p className="text-sm text-ink-muted">다녀온 박지, 써 본 장비, 준비하며 궁금한 것을 나눌 수 있어요.</p>
    </div>
  );
}

function CommunityAside() {
  return (
    <>
      <AsideSection title="커뮤니티 안내">
        <ul className="list-disc pl-5">
          <li>
            동행은{' '}
            <Link to="/basecamps" className="font-semibold text-accent underline underline-offset-2">
              베이스캠프
            </Link>
            에서 구해요.
          </li>
          <li>금전을 요구하는 글은 신고해 주세요.</li>
          <li>공원 내에서 야영을 권하는 글은 신고 대상이에요.</li>
          <li>신고가 5건 쌓이면 글이 잠시 내려가고 관리자가 검토해요.</li>
        </ul>
      </AsideSection>
    </>
  );
}

function WriteAction({ spotId }: { spotId: number | null }) {
  const session = useSession();
  const target = spotId === null ? '/community/new' : `/community/new?spotId=${spotId}`;

  if (session.status === 'loading' || session.status === 'anonymous' || session.me === null) return null;
  if (session.me.status === 'UNVERIFIED') {
    return (
      <Button variant="secondary" disabled disabledReason={UNVERIFIED_WRITE_REASON}>
        글쓰기
      </Button>
    );
  }
  return (
    <Link to={target} className={LINK_BUTTON_CLASS}>
      글쓰기
    </Link>
  );
}

function SpotFilterLine({ spotId, onRemove }: { spotId: number; onRemove: () => void }) {
  const [name, setName] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchSpotDetail(spotId, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setName(detail.name);
      },
      () => undefined,
    );
    return () => controller.abort();
  }, [spotId]);

  const label = name === null ? '이 장소 글' : `${name} 글`;
  return (
    <div className="flex flex-wrap items-center justify-between gap-x-4 rounded-row bg-fill py-1 pr-2 pl-4">
      <p className="text-base font-semibold text-ink">{label}</p>
      <button
        type="button"
        onClick={onRemove}
        className="inline-flex min-h-11 items-center text-base font-semibold text-accent underline underline-offset-2"
      >
        전체 글 보기
      </button>
    </div>
  );
}

function PostRow({ item }: { item: CommunityPostSummary }) {
  const [thumbnailFailed, setThumbnailFailed] = useState(false);
  const thumbnail = item.thumbnailUrl && !thumbnailFailed ? item.thumbnailUrl : null;
  return (
    <li>
      <Link
        to={`/community/${item.postId}`}
        className="flex min-h-11 items-start gap-4 rounded-row px-4 py-4 hover:bg-fill sm:gap-6 sm:py-5"
      >
        <span className="flex min-w-0 flex-1 flex-col gap-1.5">
          <span className="flex min-w-0 items-center gap-2">
            <span className="line-clamp-2 text-[1.0625rem] leading-snug font-bold tracking-[-0.01em] break-words text-ink sm:text-lg">
              {item.title}
            </span>
            {thumbnail === null && item.imageCount > 0 && (
              <Icon name="image" size={16} title="사진 있음" className="shrink-0 text-ink-muted" />
            )}
          </span>
          {item.excerpt && (
            <span className="line-clamp-2 text-[0.9375rem] leading-relaxed break-words text-ink-muted">
              {item.excerpt}
            </span>
          )}
          <span className="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-[0.8125rem] text-ink-muted">
            <span className="min-w-0 truncate">{item.author.nickname}</span>
            <time dateTime={item.createdAt} className="tabular-nums">
              {formatRelativeKst(item.createdAt)}
            </time>
            <span className="tabular-nums">좋아요 {item.likeCount}</span>
            <span className="tabular-nums">댓글 {item.commentCount}</span>
            <span className="tabular-nums">조회 {item.viewCount}</span>
          </span>
        </span>
        {thumbnail !== null && (
          <img
            src={thumbnail}
            alt=""
            loading="lazy"
            onError={() => setThumbnailFailed(true)}
            className="size-20 shrink-0 rounded-row bg-fill object-cover sm:size-28"
          />
        )}
      </Link>
    </li>
  );
}
