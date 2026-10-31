import { useState } from 'react';
import { fetchAdminMemories, hideMemory, restoreMemory } from '../../api/admin';
import type { AdminMemoryStatus, AdminMemorySummary, CommunityReportReason, Page } from '../../api/types';
import { StatusText } from '../../components/StatusText';
import { Button } from '../../components/Button';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime, formatLocalDate } from '../../lib/datetime';
import { usePagedList } from '../../components/usePagedList';
import { REPORT_REASON_OPTIONS } from '../community/communityLabels';
import { ApproxLocationLabel } from '../memory/ApproxLocationLabel';
import { nightsLabel } from '../memory/memoryLabels';
import { AdminPage } from './AdminGate';
import { AdminSegments } from './AdminSegments';

const itemKey = (item: AdminMemorySummary) => item.memoryId;

const TABS: ReadonlyArray<{ status: AdminMemoryStatus; label: string }> = [
  { status: 'PENDING_REVIEW', label: '검토 대기' },
  { status: 'HIDDEN', label: '숨김' },
];

const REASON_LABELS = Object.fromEntries(REPORT_REASON_OPTIONS.map((option) => [option.value, option.label])) as Record<
  CommunityReportReason,
  string
>;

const MAX_THUMBNAILS = 4;

function fetchMemories(
  status: AdminMemoryStatus,
  page: number,
  signal?: AbortSignal,
): Promise<Page<AdminMemorySummary>> {
  return fetchAdminMemories({ status, page }, signal);
}

export function AdminMemoriesPage() {
  return (
    <AdminPage title="기록 검토" current="/admin/memories">
      <MemoryReview />
    </AdminPage>
  );
}

function MemoryReview() {
  const [tab, setTab] = useState<AdminMemoryStatus>('PENDING_REVIEW');
  const { current, loadingMore, loadMore, retry, reload } = usePagedList(true, tab, fetchMemories, itemKey);
  const showSkeleton = useDelayedFlag(current === null);

  return (
    <section className="flex flex-col gap-4" aria-label="기록 검토 목록">
      <AdminSegments
        label="목록 종류"
        segments={TABS.map((item) => ({ value: item.status, label: item.label }))}
        value={tab}
        onChange={setTab}
      />
      {current?.error && (
        <div role="alert" className="flex flex-col gap-3">
          <ErrorNotice title="기록을 불러오지 못했어요" error={current.error} traceId={current.traceId ?? undefined} />
          {current.failedOn === 'first' && (
            <div>
              <Button variant="secondary" onClick={retry}>
                다시 불러오기
              </Button>
            </div>
          )}
        </div>
      )}
      {current === null && showSkeleton && <Skeleton className="h-40 w-full" />}
      {current !== null && current.items.length === 0 && !current.error && (
        <EmptyState
          framed
          title={tab === 'PENDING_REVIEW' ? '검토를 기다리는 기록이 없어요' : '숨긴 기록이 없어요'}
          description={tab === 'PENDING_REVIEW' ? '신고가 쌓인 기록이 여기에 모여요.' : '숨긴 기록이 여기에 모여요.'}
        />
      )}
      {current !== null && current.items.length > 0 && (
        <ul className="flex flex-col gap-3">
          {current.items.map((item) => (
            <MemoryCard key={item.memoryId} item={item} tab={tab} onDone={reload} />
          ))}
        </ul>
      )}
      {current?.hasNext && (
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
  const [error, setError] = useState<unknown>(null);
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
      setError(caught);
      setConfirmHide(false);
      setBusy(null);
    }
  }

  return (
    <li className="flex flex-col gap-3 rounded-sheet bg-fill p-5">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="min-w-0 break-words text-lg font-semibold text-ink">{item.author.nickname}님의 기록</h3>
        <StatusText tone="neutral">{item.status === 'HIDDEN' ? '숨김' : '검토 대기'}</StatusText>
      </div>
      {thumbnails.length > 0 && (
        <ul className="grid grid-cols-4 gap-1" aria-label={`사진 ${item.images.length}장`}>
          {thumbnails.map((image, index) => (
            <li key={image.imageId} className="aspect-square overflow-hidden rounded-row bg-fill-strong">
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
          <span className="font-mono tabular-nums">{formatLocalDate(item.visitedDate)}</span> ·{' '}
          {nightsLabel(item.nights)}
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
          <ul className="flex flex-col gap-1">
            {item.recentReports.map((report, index) => (
              <li key={index} className="rounded-row bg-surface px-4 py-2.5 text-sm">
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
      {error !== null && (
        <div role="alert">
          <ErrorNotice title="상태를 바꾸지 못했어요" error={error} />
        </div>
      )}
      <div className="flex flex-wrap items-center gap-2">
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
        <Dialog title="기록을 숨길까요?" onClose={() => busy === null && setConfirmHide(false)}>
          <div className="flex flex-col gap-4">
            <p className="text-base text-ink">
              {item.author.nickname}님의 기록을 작성자만 볼 수 있게 숨겨요. 숨긴 기록은 숨김 탭에서 복구할 수 있어요.
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
