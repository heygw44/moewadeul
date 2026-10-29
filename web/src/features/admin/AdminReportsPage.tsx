import { useState } from 'react';
import { fetchAdminMemberReports } from '../../api/admin';
import type { AdminReportSummary, Page, ReportStatus } from '../../api/types';
import { Link } from '../../app/router';
import { StatusText } from '../../components/StatusText';
import { Button } from '../../components/Button';
import { Checkbox } from '../../components/Checkbox';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Icon } from '../../components/icons';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime } from '../../lib/datetime';
import { REPORT_KIND_LABELS, REPORT_STATUS_META, REPORT_TYPE_LABELS } from '../report/reportLabels';
import { usePagedList } from '../../components/usePagedList';
import { AdminPage } from './AdminGate';
import { AdminSegments } from './AdminSegments';

const ALL = 'ALL';

const STATUS_SEGMENTS = [
  { value: ALL, label: '전체' },
  { value: 'RECEIVED', label: '접수' },
  { value: 'IN_REVIEW', label: '검토 중' },
  { value: 'ACTIONED', label: '조치 완료' },
  { value: 'DISMISSED', label: '기각' },
];

type ReportFilter = { status: ReportStatus | undefined; urgent: true | undefined };

function fetchReports(filter: ReportFilter, page: number, signal?: AbortSignal): Promise<Page<AdminReportSummary>> {
  return fetchAdminMemberReports({ ...filter, page }, signal);
}

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

  const filter: ReportFilter = {
    status: status === ALL ? undefined : (status as ReportStatus),
    urgent: urgentOnly ? true : undefined,
  };
  const { current, loadingMore, loadMore, retry } = usePagedList(true, filter, fetchReports);
  const showSkeleton = useDelayedFlag(current === null);

  return (
    <section className="flex flex-col gap-4" aria-label="신고 목록">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between sm:gap-6">
        <AdminSegments label="처리 상태" segments={STATUS_SEGMENTS} value={status} onChange={setStatus} />
        <Checkbox label="긴급 신고만" checked={urgentOnly} onChange={(event) => setUrgentOnly(event.target.checked)} />
      </div>
      {current?.error && (
        <div role="alert" className="flex flex-col gap-3">
          <ErrorNotice title="신고를 불러오지 못했어요" error={current.error} traceId={current.traceId ?? undefined} />
          {current.items.length === 0 && (
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
        <EmptyState framed title="조건에 맞는 신고가 없어요" description="처리 상태나 긴급 조건을 바꿔 주세요." />
      )}
      {current !== null && current.items.length > 0 && (
        <div className="rounded-sheet bg-fill p-1.5">
          <div aria-hidden="true" className={`hidden py-2.5 text-sm font-semibold text-ink-muted md:grid ${ROW_GRID}`}>
            <span>우선</span>
            <span>사유</span>
            <span>신고한 회원 → 대상</span>
            <span>상태</span>
            <span>접수</span>
          </div>
          <ul className="flex flex-col gap-1">
            {current.items.map((item) => (
              <ReportRow key={item.reportId} item={item} />
            ))}
          </ul>
        </div>
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

const ROW_GRID = 'px-4 md:grid-cols-[3.5rem_minmax(0,10rem)_minmax(0,1fr)_6rem_9.5rem_1.25rem] md:gap-4';

function ReportRow({ item }: { item: AdminReportSummary }) {
  const status = REPORT_STATUS_META[item.status];
  return (
    <li>
      <Link
        to={`/admin/reports/${item.reportId}`}
        className={`flex min-h-11 flex-col gap-1 rounded-row bg-surface py-3 hover:bg-fill-strong md:grid md:items-center ${ROW_GRID}`}
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
