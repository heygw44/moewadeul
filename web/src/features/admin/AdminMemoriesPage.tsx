import { useEffect, useState } from 'react';
import { fetchAdminMemories, hideMemory, restoreMemory } from '../../api/admin';
import type { AdminMemoryStatus, AdminMemorySummary, CommunityReportReason } from '../../api/types';
import { StatusText } from '../../components/StatusText';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime, formatLocalDate } from '../../lib/datetime';
import { REPORT_REASON_OPTIONS } from '../community/communityLabels';
import { ApproxLocationLabel } from '../memory/ApproxLocationLabel';
import { nightsLabel } from '../memory/memoryLabels';
import { AdminPage } from './AdminGate';
import { adminErrorMessage } from './adminErrors';

const TABS: ReadonlyArray<{ status: AdminMemoryStatus; label: string }> = [
  { status: 'PENDING_REVIEW', label: '검토 대기' },
  { status: 'HIDDEN', label: '숨김' },
];

const REASON_LABELS = Object.fromEntries(REPORT_REASON_OPTIONS.map((option) => [option.value, option.label])) as Record<
  CommunityReportReason,
  string
>;

// 관리자 화면에는 사진을 몇 장만 작게 보여 준다. 나머지는 장 수로 알린다.
const MAX_THUMBNAILS = 4;

type ListState = { items: AdminMemorySummary[]; page: number; hasNext: boolean; error: string | null };

export function AdminMemoriesPage() {
  return (
    <AdminPage title="발자국 검토" current="/admin/memories">
      <MemoryReview />
    </AdminPage>
  );
}

// 관리자도 비공개 발자국은 보지 못해서, 서버가 공개 발자국만 준다. 위치도 대략 위치만 온다.
function MemoryReview() {
  const [tab, setTab] = useState<AdminMemoryStatus>('PENDING_REVIEW');
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<ListState | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const showSkeleton = useDelayedFlag(state === null);

  useEffect(() => {
    const controller = new AbortController();
    fetchAdminMemories({ status: tab, page: 0 }, controller.signal).then(
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
      const next = await fetchAdminMemories({ status: tab, page: state.page + 1 });
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

  function removeItem(memoryId: number) {
    setState((previous) =>
      previous ? { ...previous, items: previous.items.filter((item) => item.memoryId !== memoryId) } : previous,
    );
  }

  return (
    <section className="flex flex-col gap-4" aria-label="발자국 검토 목록">
      <div role="group" aria-label="목록 종류" className="flex flex-wrap gap-2">
        {TABS.map((item) => (
          <button
            key={item.status}
            type="button"
            aria-pressed={item.status === tab}
            onClick={() => {
              if (item.status === tab) return;
              setState(null);
              setTab(item.status);
            }}
            className={`inline-flex min-h-11 items-center rounded-control border px-4 font-semibold ${
              item.status === tab
                ? 'border-forest bg-forest-soft text-forest-deep'
                : 'border-ink-subtle bg-card text-ink hover:bg-paper-deep'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>
      {state?.error && (
        <div role="alert" className="flex flex-col gap-3">
          <Notice tone="danger" title="발자국을 불러오지 못했어요">
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
          title={tab === 'PENDING_REVIEW' ? '검토를 기다리는 발자국이 없어요' : '숨긴 발자국이 없어요'}
          description="신고가 쌓이거나 발자국을 숨기면 여기에 보여요."
        />
      )}
      {state !== null && state.items.length > 0 && (
        <ul className="flex flex-col gap-3">
          {state.items.map((item) => (
            <MemoryCard key={item.memoryId} item={item} tab={tab} onDone={() => removeItem(item.memoryId)} />
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

function MemoryCard({ item, tab, onDone }: { item: AdminMemorySummary; tab: AdminMemoryStatus; onDone: () => void }) {
  const [confirmHide, setConfirmHide] = useState(false);
  const [busy, setBusy] = useState<Action | null>(null);
  const [error, setError] = useState<string | null>(null);
  const thumbnails = item.images.slice(0, MAX_THUMBNAILS);

  async function run(action: Action) {
    if (busy) return;
    setBusy(action);
    setError(null);
    try {
      if (action === 'hide') await hideMemory(item.memoryId);
      else await restoreMemory(item.memoryId);
      onDone();
    } catch (caught) {
      setError(adminErrorMessage(caught));
      setConfirmHide(false);
      setBusy(null);
    }
  }

  return (
    <li className="flex flex-col gap-3 rounded-control border border-contour bg-card p-4">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="min-w-0 break-words text-lg font-semibold text-ink">{item.author.nickname}의 발자국</h3>
        <StatusText tone="neutral">{item.status === 'HIDDEN' ? '숨김' : '검토 대기'}</StatusText>
      </div>
      {thumbnails.length > 0 && (
        <ul className="grid grid-cols-4 gap-1" aria-label={`사진 ${item.images.length}장`}>
          {thumbnails.map((image, index) => (
            <li key={image.imageId} className="aspect-square overflow-hidden rounded-control bg-paper-deep">
              <img src={image.url} alt={`사진 ${index + 1}`} loading="lazy" className="h-full w-full object-cover" />
            </li>
          ))}
        </ul>
      )}
      {item.content && <p className="whitespace-pre-line break-words text-base text-ink">{item.content}</p>}
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
        <dt className="text-ink-muted">대략 위치</dt>
        <dd className="text-ink">
          <ApproxLocationLabel position={{ lat: item.approxLat, lng: item.approxLng }} />
        </dd>
        <dt className="text-ink-muted">다녀온 날</dt>
        <dd className="text-ink">
          <span className="font-mono tabular-nums">{formatLocalDate(item.visitedDate)}</span> · {nightsLabel(item.nights)}
        </dd>
        {item.images.length > thumbnails.length && (
          <>
            <dt className="text-ink-muted">사진</dt>
            <dd className="font-mono tabular-nums text-ink">{item.images.length}장</dd>
          </>
        )}
        <dt className="text-ink-muted">검토 전 신고</dt>
        <dd className="font-mono tabular-nums text-ink">
          {item.reportCount}건
          <span className="font-sans">
            {' ('}
            {REPORT_REASON_OPTIONS.filter((option) => (item.reasonCounts[option.value] ?? 0) > 0)
              .map((option) => `${option.label} ${item.reasonCounts[option.value]}`)
              .join(', ') || '없음'}
            {')'}
          </span>
        </dd>
        <dt className="text-ink-muted">상태 변경</dt>
        <dd className="font-mono tabular-nums text-ink">{formatKstDateTime(item.statusChangedAt)}</dd>
      </dl>
      {item.recentReports.length > 0 && (
        <div className="flex flex-col gap-2">
          <p className="text-sm font-medium text-ink">최근 신고</p>
          <ul className="flex flex-col gap-2">
            {item.recentReports.map((report, index) => (
              <li key={index} className="rounded-control bg-paper-deep px-3 py-2 text-sm">
                <p className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold text-ink">{REASON_LABELS[report.reason] ?? report.reason}</span>
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
          <Notice tone="danger" title="처리하지 못했어요">
            <p>{error}</p>
          </Notice>
        </div>
      )}
      <div className="flex flex-wrap items-center gap-2">
        {tab === 'PENDING_REVIEW' && (
          <Button variant="danger" disabled={busy !== null} onClick={() => setConfirmHide(true)}>
            숨김
          </Button>
        )}
        <Button variant="secondary" loading={busy === 'restore'} disabled={busy === 'hide'} onClick={() => void run('restore')}>
          복구
        </Button>
      </div>
      {confirmHide && (
        <Dialog title="발자국을 숨길까요?" onClose={() => busy === null && setConfirmHide(false)}>
          <div className="flex flex-col gap-4">
            <p className="text-base text-ink">
              {item.author.nickname}의 발자국을 작성자 말고는 볼 수 없게 숨겨요. 숨긴 발자국은 숨김 탭에서 다시 복구할 수 있어요.
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
