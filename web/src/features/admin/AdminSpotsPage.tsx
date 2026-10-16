import { useEffect, useState } from 'react';
import { fetchAdminSpots, hideSpot, restoreSpot } from '../../api/admin';
import type { AdminSpotStatus, AdminSpotSummary, BakjiReportReason } from '../../api/types';
import { Link } from '../../app/router';
import { StatusText } from '../../components/StatusText';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime } from '../../lib/datetime';
import { REPORT_REASON_LABELS, SPOT_TYPE_META } from '../spot/spotLabels';
import { AdminPage } from './AdminGate';
import { AdminSegments } from './AdminSegments';
import { adminErrorMessage } from './adminErrors';
import { buttonClassName } from '../../components/buttonClass';

const TABS: ReadonlyArray<{ status: AdminSpotStatus; label: string }> = [
  { status: 'PENDING_REVIEW', label: '검토 대기' },
  { status: 'HIDDEN', label: '숨김' },
];

const REASONS: BakjiReportReason[] = ['ILLEGAL_AREA', 'CLOSED', 'FALSE_INFO'];

type ListState = { items: AdminSpotSummary[]; page: number; hasNext: boolean; error: string | null };

export function AdminSpotsPage() {
  return (
    <AdminPage title="박지 검토" current="/admin/spots">
      <SpotReview />
    </AdminPage>
  );
}

function SpotReview() {
  const [tab, setTab] = useState<AdminSpotStatus>('PENDING_REVIEW');
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<ListState | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const showSkeleton = useDelayedFlag(state === null);

  useEffect(() => {
    const controller = new AbortController();
    fetchAdminSpots({ status: tab, page: 0 }, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) {
          setState({ items: page.content, page: page.page, hasNext: page.hasNext, error: null });
        }
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) {
          setState({ items: [], page: 0, hasNext: false, error: adminErrorMessage(caught) });
        }
      },
    );
    return () => controller.abort();
  }, [tab, attempt]);

  async function loadMore() {
    if (!state || loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await fetchAdminSpots({ status: tab, page: state.page + 1 });
      setState((previous) =>
        previous
          ? { items: [...previous.items, ...next.content], page: next.page, hasNext: next.hasNext, error: null }
          : previous,
      );
    } catch (caught) {
      setState((previous) => (previous ? { ...previous, error: adminErrorMessage(caught) } : previous));
    } finally {
      setLoadingMore(false);
    }
  }

  function removeItem(spotId: number) {
    setState((previous) =>
      previous ? { ...previous, items: previous.items.filter((item) => item.spotId !== spotId) } : previous,
    );
  }

  return (
    <section className="flex flex-col gap-4" aria-label="박지 검토 목록">
      <AdminSegments
        label="목록 종류"
        segments={TABS.map((item) => ({ value: item.status, label: item.label }))}
        value={tab}
        onChange={(status) => {
          setState(null);
          setTab(status);
        }}
      />
      {state?.error && (
        <div role="alert" className="flex flex-col gap-3">
          <Notice tone="danger" title="박지를 불러오지 못했어요">
            <p>{state.error}</p>
          </Notice>
          {state.items.length === 0 && (
            <div>
              <Button
                variant="secondary"
                onClick={() => {
                  setState(null);
                  setAttempt((value) => value + 1);
                }}
              >
                다시 불러오기
              </Button>
            </div>
          )}
        </div>
      )}
      {state === null && showSkeleton && <Skeleton className="h-40 w-full" />}
      {state !== null && state.items.length === 0 && !state.error && (
        <EmptyState
          framed
          title={tab === 'PENDING_REVIEW' ? '검토를 기다리는 박지가 없어요' : '숨긴 박지가 없어요'}
          description={tab === 'PENDING_REVIEW' ? '신고가 쌓인 박지가 여기에 모여요.' : '숨긴 박지가 여기에 모여요.'}
        />
      )}
      {state !== null && state.items.length > 0 && (
        <ul className="flex flex-col gap-3">
          {state.items.map((item) => (
            <SpotCard key={item.spotId} item={item} tab={tab} onDone={() => removeItem(item.spotId)} />
          ))}
        </ul>
      )}
      {state?.hasNext && (
        <div>
          <Button variant="secondary" loading={loadingMore} onClick={() => void loadMore()}>
            더 보기
          </Button>
        </div>
      )}
    </section>
  );
}

type Action = 'hide' | 'restore';

function SpotCard({ item, tab, onDone }: { item: AdminSpotSummary; tab: AdminSpotStatus; onDone: () => void }) {
  const [confirmHide, setConfirmHide] = useState(false);
  const [busy, setBusy] = useState<Action | null>(null);
  const [error, setError] = useState<string | null>(null);
  const meta = SPOT_TYPE_META[item.type];

  async function run(action: Action) {
    if (busy) return;
    setBusy(action);
    setError(null);
    try {
      if (action === 'hide') await hideSpot(item.spotId);
      else await restoreSpot(item.spotId);
      onDone();
    } catch (caught) {
      setError(adminErrorMessage(caught));
      setConfirmHide(false);
      setBusy(null);
    }
  }

  return (
    <li className="flex flex-col gap-3 rounded-sheet border border-line bg-surface p-4">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="min-w-0 break-words text-lg font-semibold text-ink">{item.name}</h3>
        <StatusText tone={meta.tone}>{meta.label}</StatusText>
        <StatusText tone="neutral">{item.status === 'HIDDEN' ? '숨김' : '검토 대기'}</StatusText>
        {item.parkWarning && (
          <StatusText tone="warning" icon="alert">
            공원 경계 경고
          </StatusText>
        )}
      </div>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
        <dt className="text-ink-muted">제보자</dt>
        <dd className="text-ink">{item.reporter ? item.reporter.nickname : '공공데이터'}</dd>
        <dt className="text-ink-muted">검토 전 신고</dt>
        <dd className="font-mono tabular-nums text-ink">
          {item.reportCount}건
          <span className="font-sans">
            {' ('}
            {REASONS.map((reason) => `${REPORT_REASON_LABELS[reason]} ${item.reasonCounts[reason] ?? 0}`).join(', ')}
            {')'}
          </span>
        </dd>
        <dt className="text-ink-muted">상태 변경</dt>
        <dd className="font-mono tabular-nums text-ink">{formatKstDateTime(item.statusChangedAt)}</dd>
      </dl>
      {item.recentReports.length > 0 && (
        <div className="flex flex-col gap-2">
          <p className="text-sm font-medium text-ink">최근 신고</p>
          <ul className="flex flex-col divide-y divide-line border-y border-line">
            {item.recentReports.map((report, index) => (
              <li key={index} className="py-2 text-sm">
                <p className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold text-ink">{REPORT_REASON_LABELS[report.reason] ?? report.reason}</span>
                  <span className="font-mono tabular-nums text-ink-muted">{formatKstDateTime(report.createdAt)}</span>
                </p>
                {report.content && <p className="mt-1 whitespace-pre-line break-words text-ink">{report.content}</p>}
              </li>
            ))}
          </ul>
        </div>
      )}
      {error && (
        <div role="alert">
          <Notice tone="danger" title="상태를 바꾸지 못했어요">
            <p>{error}</p>
          </Notice>
        </div>
      )}
      <div className="flex flex-wrap items-center gap-2">
        <Link to={`/spots/${item.spotId}`} className={buttonClassName('secondary')}>
          장소 보기
        </Link>
        {tab === 'PENDING_REVIEW' && (
          <Button variant="danger" disabled={busy !== null} onClick={() => setConfirmHide(true)}>
            숨기기
          </Button>
        )}
        <Button
          variant="secondary"
          loading={busy === 'restore'}
          disabled={busy === 'hide'}
          onClick={() => void run('restore')}
        >
          복구
        </Button>
      </div>
      {confirmHide && (
        <Dialog title="박지를 숨길까요?" onClose={() => busy === null && setConfirmHide(false)}>
          <div className="flex flex-col gap-4">
            <p className="text-base text-ink">
              &apos;{item.name}&apos; 박지를 지도와 검색에서 숨겨요. 숨긴 박지는 숨김 탭에서 복구할 수 있어요.
            </p>
            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="secondary" disabled={busy !== null} onClick={() => setConfirmHide(false)}>
                취소
              </Button>
              <Button variant="danger" loading={busy === 'hide'} onClick={() => void run('hide')}>
                숨기기
              </Button>
            </div>
          </div>
        </Dialog>
      )}
    </li>
  );
}
