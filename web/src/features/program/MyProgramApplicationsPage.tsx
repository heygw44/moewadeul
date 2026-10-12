import { useEffect, useState } from 'react';
import { listMyProgramApplications } from '../../api/programs';
import type { MyProgramApplicationItem, ProgramApplicationStatus } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { BackLink } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { ListPanel } from '../../components/HubLayout';
import { Notice } from '../../components/Notice';
import { PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { MetaLine, StatusText } from '../../components/StatusText';
import { UnderlineTabs } from '../../components/UnderlineTabs';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime } from '../../lib/datetime';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { APPLICATION_FILTERS, APPLICATION_STATUS_META, CANCEL_REASON_LABELS, countdownText } from './programLabels';
import { useNow } from './useNow';
import { usePagedList } from './usePagedList';

type ApplicationFilter = ProgramApplicationStatus | 'ALL';

function fetchApplications(filter: ApplicationFilter, page: number, signal?: AbortSignal) {
  return listMyProgramApplications(filter === 'ALL' ? undefined : filter, page, signal);
}

export function MyProgramApplicationsPage() {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [filter, setFilter] = useState<ApplicationFilter>('ALL');
  const authenticated = session.status === 'authenticated';
  const { current, loadingMore, loadMore, retry } = usePagedList(authenticated, filter, fetchApplications);
  const showSkeleton = useDelayedFlag(session.status === 'loading' || (authenticated && current === null));
  const hasPending = current?.items.some((item) => item.status === 'PENDING_PAYMENT') ?? false;
  const now = useNow(hasPending);

  useEffect(() => {
    document.title = '내 행사 신청 · 뫼와들';
  }, []);

  // 내 신청은 로그인한 세션으로만 볼 수 있다. 로그인한 뒤 이 화면으로 돌아오게 한다.
  useEffect(() => {
    if (session.status === 'anonymous') navigate(withNext('/login', pathname + search), { replace: true });
  }, [session.status, pathname, search]);

  return (
    <PageLayout
      title="내 행사 신청"
      breadcrumb={<BackLink to="/me">내 정보</BackLink>}
      tabs={<UnderlineTabs label="신청 상태" options={APPLICATION_FILTERS} value={filter} onChange={setFilter} />}
    >
      <div aria-busy={authenticated && current === null} className="flex flex-col gap-3">
        {!authenticated || current === null ? (
          showSkeleton ? <Skeleton className="h-24 w-full" /> : null
        ) : (
          <>
            {current.error && (
              <div className="flex flex-col gap-3">
                <Notice tone="danger" title="내 신청을 불러오지 못했어요">
                  <p>{current.error}</p>
                </Notice>
                {current.items.length === 0 && (
                  <div>
                    <Button variant="secondary" onClick={retry}>
                      다시 불러오기
                    </Button>
                  </div>
                )}
              </div>
            )}
            {current.items.length === 0 && !current.error ? (
              <EmptyState
                framed
                title={filter === 'ALL' ? '신청한 행사가 없어요' : '이 상태의 신청이 없어요'}
                action={
                  filter === 'ALL' && (
                    <Link
                      to="/programs"
                      className="inline-flex min-h-11 items-center rounded-control border border-ink-subtle bg-card px-4 font-semibold text-ink hover:bg-paper-deep"
                    >
                      공식 행사 보기
                    </Link>
                  )
                }
              />
            ) : (
              <ListPanel>
                {current.items.map((item) => (
                  <ApplicationCard key={item.applicationId} item={item} now={now} />
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

// 신청 시각부터 결제 기한까지가 그 신청의 결제 기한 길이다. 행사마다 기한 길이가 다를 수 있어서 응답의 두 시각으로 구한다.
function paymentWindowSeconds(createdAt: string, paymentDueAt: string): number {
  return Math.floor((Date.parse(paymentDueAt) - Date.parse(createdAt)) / 1000);
}

function ApplicationCard({ item, now }: { item: MyProgramApplicationItem; now: number }) {
  const status = APPLICATION_STATUS_META[item.status];
  const pending = item.status === 'PENDING_PAYMENT' && item.paymentDueAt !== null;
  return (
    <li>
      <Link
        to={`/programs/${item.program.programId}`}
        className="flex min-h-11 flex-col gap-2 p-4 hover:bg-paper-deep"
      >
        <span className="text-lg font-semibold text-ink">{item.program.title}</span>
        <MetaLine
          items={[
            <StatusText tone={status.tone}>{status.label}</StatusText>,
            item.program.status === 'CANCELED' && '행사 취소',
            pending && item.paymentDueAt && (
              <span className="text-warning">
                남은 시간{' '}
                <span className="font-mono tabular-nums">
                  {countdownText(item.paymentDueAt, now, paymentWindowSeconds(item.createdAt, item.paymentDueAt))}
                </span>
              </span>
            ),
          ]}
        />
        <span className="font-mono text-sm tabular-nums text-ink-muted">{formatKstDateTime(item.program.startAt)}</span>
        {item.status === 'CANCELED' && item.cancelReason && (
          <span className="text-sm text-ink-muted">취소 사유: {CANCEL_REASON_LABELS[item.cancelReason]}</span>
        )}
        {item.status === 'EXPIRED' && <span className="text-sm text-ink-muted">결제 기한이 지나 만료됐어요</span>}
      </Link>
    </li>
  );
}
