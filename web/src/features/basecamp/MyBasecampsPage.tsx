import { useEffect, useState } from 'react';
import { buttonClassName } from '../../components/buttonClass';
import { fetchMyBasecamps } from '../../api/basecamps';
import type { MyBasecampItem, MyBasecampRelation } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { BackLink } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { ListPanel } from '../../components/HubLayout';
import { ErrorNotice } from '../../components/ErrorNotice';
import { PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { MetaLine, StatusText } from '../../components/StatusText';
import { UnderlineTabs } from '../../components/UnderlineTabs';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatLocalDate } from '../../lib/datetime';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { BASECAMP_STATUS_META, headcountText } from './basecampLabels';

type RelationFilter = MyBasecampRelation | 'ALL';

const FILTERS: ReadonlyArray<{ value: RelationFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'LEADER', label: '캠프 리더' },
  { value: 'MEMBER', label: '멤버' },
  { value: 'APPLICANT', label: '승인 대기' },
];

const RELATION_LABELS: Record<MyBasecampRelation, string> = {
  LEADER: '캠프 리더',
  MEMBER: '멤버',
  APPLICANT: '승인 대기',
};

type ListState = {
  filter: RelationFilter;
  items: MyBasecampItem[];
  page: number;
  hasNext: boolean;
  error: { cause: unknown } | null;
};

export function MyBasecampsPage() {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [filter, setFilter] = useState<RelationFilter>('ALL');
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<ListState | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const authenticated = session.status === 'authenticated';

  useEffect(() => {
    document.title = '내 베이스캠프 · 뫼와들';
  }, []);

  // 내 베이스캠프는 로그인한 세션으로만 볼 수 있다. 로그인한 뒤 이 화면으로 돌아오게 한다.
  useEffect(() => {
    if (session.status === 'anonymous') navigate(withNext('/login', pathname + search), { replace: true });
  }, [session.status, pathname, search]);

  useEffect(() => {
    if (!authenticated) return;
    const controller = new AbortController();
    fetchMyBasecamps({ relation: filter === 'ALL' ? undefined : filter, page: 0 }, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) {
          setState({ filter, items: page.content, page: page.page, hasNext: page.hasNext, error: null });
        }
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) {
          setState({ filter, items: [], page: 0, hasNext: false, error: { cause: caught } });
        }
      },
    );
    return () => controller.abort();
  }, [authenticated, filter, attempt]);

  const current = state?.filter === filter ? state : null;
  const showSkeleton = useDelayedFlag(session.status === 'loading' || (authenticated && current === null));

  async function loadMore() {
    if (!current || loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await fetchMyBasecamps({ relation: filter === 'ALL' ? undefined : filter, page: current.page + 1 });
      setState((previous) =>
        previous?.filter === filter
          ? {
              ...previous,
              items: [...previous.items, ...next.content],
              page: next.page,
              hasNext: next.hasNext,
              error: null,
            }
          : previous,
      );
    } catch (caught) {
      setState((previous) => (previous?.filter === filter ? { ...previous, error: { cause: caught } } : previous));
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <PageLayout
      title="내 베이스캠프"
      breadcrumb={<BackLink to="/me">내 정보</BackLink>}
      tabs={<UnderlineTabs label="내 역할" options={FILTERS} value={filter} onChange={setFilter} />}
    >
      <div aria-busy={authenticated && current === null} className="flex flex-col gap-3">
        {!authenticated || current === null ? (
          showSkeleton ? (
            <Skeleton className="h-24 w-full" />
          ) : null
        ) : (
          <>
            {current.error && (
              <div className="flex flex-col gap-3">
                <ErrorNotice title="내 베이스캠프를 불러오지 못했어요" error={current.error.cause} />
                {current.items.length === 0 && (
                  <div>
                    <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
                      다시 불러오기
                    </Button>
                  </div>
                )}
              </div>
            )}
            {current.items.length === 0 && !current.error ? (
              <EmptyState
                framed
                title={filter === 'ALL' ? '열었거나 합류한 베이스캠프가 없어요' : '해당하는 베이스캠프가 없어요'}
                description={
                  filter === 'ALL'
                    ? '같은 날 같은 곳으로 가는 베이스캠프를 찾아 합류 신청을 보낼 수 있어요.'
                    : undefined
                }
                action={
                  filter === 'ALL' && (
                    <Link to="/basecamps" className={buttonClassName('secondary')}>
                      베이스캠프 찾기
                    </Link>
                  )
                }
              />
            ) : (
              <ListPanel>
                {current.items.map((item) => (
                  <BasecampCard
                    key={`${item.basecampId}-${item.myRelation}`}
                    item={item}
                    showRelation={filter === 'ALL'}
                  />
                ))}
              </ListPanel>
            )}
            {current.hasNext && (
              <div>
                <Button variant="secondary" loading={loadingMore} onClick={() => void loadMore()}>
                  더 보기
                </Button>
              </div>
            )}
          </>
        )}
      </div>
    </PageLayout>
  );
}

// 관계(캠프 리더·멤버·신청 중)로 거른 탭에서는 모든 행의 관계가 탭 이름과 같으므로 '전체' 탭에서만 적는다.
function BasecampCard({ item, showRelation }: { item: MyBasecampItem; showRelation: boolean }) {
  const status = BASECAMP_STATUS_META[item.status];
  return (
    <li>
      <Link to={`/basecamps/${item.basecampId}`} className="flex flex-col gap-1 p-4 hover:bg-fill">
        <span className="text-lg font-semibold text-ink">{item.title}</span>
        <MetaLine
          items={[
            <StatusText tone={status.tone}>{status.label}</StatusText>,
            showRelation && RELATION_LABELS[item.myRelation],
            item.spot.name,
          ]}
        />
        <span className="font-mono text-sm tabular-nums text-ink-muted">
          {formatLocalDate(item.startDate)} ~ {formatLocalDate(item.endDate)} ·{' '}
          {headcountText(item.headcount, item.capacity)}명
        </span>
      </Link>
    </li>
  );
}
