import { useEffect, useState } from 'react';
import { fetchAdminMemberReports } from '../../api/admin';
import type { AdminReportSummary, ReportStatus } from '../../api/types';
import { Link } from '../../app/router';
import { StatusText } from '../../components/StatusText';
import { Button } from '../../components/Button';
import { Checkbox } from '../../components/Checkbox';
import { EmptyState } from '../../components/EmptyState';
import { Notice } from '../../components/Notice';
import { Icon } from '../../components/icons';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime } from '../../lib/datetime';
import { REPORT_KIND_LABELS, REPORT_STATUS_META, REPORT_TYPE_LABELS } from '../report/reportLabels';
import { AdminPage } from './AdminGate';
import { AdminSegments } from './AdminSegments';
import { adminErrorMessage } from './adminErrors';

const ALL = 'ALL';

const STATUS_SEGMENTS = [
  { value: ALL, label: '전체' },
  { value: 'RECEIVED', label: '접수' },
  { value: 'IN_REVIEW', label: '검토 중' },
  { value: 'ACTIONED', label: '조치 완료' },
  { value: 'DISMISSED', label: '기각' },
];

type ListState = { items: AdminReportSummary[]; page: number; hasNext: boolean; error: string | null };

export function AdminReportsPage() {
  return (
    <AdminPage title="신고 처리" current="/admin/reports">
      <ReportList />
    </AdminPage>
  );
}

function ReportList() {
  const [status, setStatus] = useState<string>(ALL);
  const [urgentOnly, setUrgentOnly] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<ListState | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const showSkeleton = useDelayedFlag(state === null);

  const statusParam = status === ALL ? undefined : (status as ReportStatus);
  const urgentParam = urgentOnly ? true : undefined;

  // 필터를 바꾸거나 다시 불러오면 첫 페이지부터 읽는다.
  useEffect(() => {
    const controller = new AbortController();
    fetchAdminMemberReports({ status: statusParam, urgent: urgentParam, page: 0 }, controller.signal).then(
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
  }, [statusParam, urgentParam, attempt]);

  function changeFilter(change: () => void) {
    setState(null);
    change();
  }

  async function loadMore() {
    if (!state || loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await fetchAdminMemberReports({ status: statusParam, urgent: urgentParam, page: state.page + 1 });
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

  return (
    <section className="flex flex-col gap-4" aria-label="신고 목록">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between sm:gap-6">
        <AdminSegments
          label="처리 상태"
          segments={STATUS_SEGMENTS}
          value={status}
          onChange={(value) => changeFilter(() => setStatus(value))}
        />
        <Checkbox
          label="긴급 신고만"
          checked={urgentOnly}
          onChange={(event) => changeFilter(() => setUrgentOnly(event.target.checked))}
        />
      </div>
      {state?.error && (
        <div role="alert" className="flex flex-col gap-3">
          <Notice tone="danger" title="신고를 불러오지 못했어요">
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
        <EmptyState framed title="조건에 맞는 신고가 없어요" description="처리 상태나 긴급 조건을 바꿔 주세요." />
      )}
      {state !== null && state.items.length > 0 && (
        <div className="overflow-hidden rounded-sheet border border-line bg-surface">
          {/* 넓은 화면에서는 표처럼 칸 이름 줄을 둔다. 좁은 화면은 행마다 위아래로 쌓으므로 이 줄을 숨긴다. */}
          <div aria-hidden="true" className={`hidden border-b border-line bg-canvas py-2.5 text-sm font-semibold text-ink-muted md:grid ${ROW_GRID}`}>
            <span>우선</span>
            <span>사유</span>
            <span>신고한 회원 → 대상</span>
            <span>상태</span>
            <span>접수</span>
          </div>
          <ul className="divide-y divide-line">
            {state.items.map((item) => (
              <ReportRow key={item.reportId} item={item} />
            ))}
          </ul>
        </div>
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

// 칸 이름 줄과 행이 같은 칸 폭을 쓴다.
const ROW_GRID = 'px-4 md:grid-cols-[3.5rem_minmax(0,10rem)_minmax(0,1fr)_6rem_9.5rem_1.25rem] md:gap-4';

function ReportRow({ item }: { item: AdminReportSummary }) {
  const status = REPORT_STATUS_META[item.status];
  return (
    <li>
      <Link
        to={`/admin/reports/${item.reportId}`}
        className={`flex min-h-11 flex-col gap-1 py-3 hover:bg-fill md:grid md:items-center ${ROW_GRID}`}
      >
        <span className={`text-sm font-extrabold ${item.urgent ? 'text-danger' : 'text-ink-muted'}`}>
          {item.urgent ? '긴급' : '보통'}
        </span>
        <span className="flex min-w-0 flex-col">
          <span className="break-words text-base font-bold text-ink">{REPORT_TYPE_LABELS[item.type]}</span>
          <span className="text-sm text-ink-muted">{REPORT_KIND_LABELS[item.kind]}</span>
        </span>
        <span className="min-w-0 break-words text-base text-ink">
          {item.reporter.nickname} → {item.target.nickname}
        </span>
        <StatusText tone={status.tone}>{status.label}</StatusText>
        <span className="font-mono text-sm tabular-nums text-ink-muted">{formatKstDateTime(item.createdAt)}</span>
        <Icon name="chevronRight" size={18} className="hidden text-ink-muted md:block" />
      </Link>
    </li>
  );
}
