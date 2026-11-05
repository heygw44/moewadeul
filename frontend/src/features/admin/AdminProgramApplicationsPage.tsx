import { useState } from 'react';
import { fetchProgramApplicants } from '../../api/admin';
import type { AdminProgramApplicant, Page, ProgramApplicationStatus } from '../../api/types';
import { BackLink } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Skeleton } from '../../components/Skeleton';
import { MetaLine, StatusText } from '../../components/StatusText';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { usePagedList } from '../../components/usePagedList';
import { formatKstDateTime } from '../../lib/datetime';
import {
  APPLICATION_FILTERS,
  APPLICATION_STATUS_META,
  CANCEL_REASON_LABELS,
  PROGRAM_STATUS_META,
} from '../program/programLabels';
import { AdminPage } from './AdminGate';
import { AdminSegments } from './AdminSegments';
import { LIST_PATH } from './adminProgramForm';
import { useProgramDetail } from './useProgramDetail';

type ApplicationFilter = ProgramApplicationStatus | 'ALL';

const itemKey = (item: AdminProgramApplicant) => item.applicationId;

const EMPTY_COPY: Record<ApplicationFilter, { title: string; description: string }> = {
  ALL: { title: '아직 신청한 회원이 없어요', description: '신청이 들어오면 신청한 순서대로 여기에 모여요.' },
  PENDING_PAYMENT: {
    title: '결제를 기다리는 신청이 없어요',
    description: '신청한 뒤 아직 결제하지 않은 신청이 여기에 모여요.',
  },
  CONFIRMED: { title: '확정된 신청이 없어요', description: '결제를 마쳐서 확정된 신청이 여기에 모여요.' },
  CANCELED: {
    title: '취소된 신청이 없어요',
    description: '직접 취소했거나 행사가 취소되면서 함께 취소된 신청이 여기에 모여요.',
  },
  EXPIRED: { title: '만료된 신청이 없어요', description: '결제 기한이 지나 만료된 신청이 여기에 모여요.' },
};

export function AdminProgramApplicationsPage({ programId }: { programId: number }) {
  return (
    <AdminPage title="행사 신청자" current={LIST_PATH}>
      <Applicants programId={programId} />
    </AdminPage>
  );
}

function Applicants({ programId }: { programId: number }) {
  const [filter, setFilter] = useState<ApplicationFilter>('ALL');
  const { current, loadingMore, loadMore, retry } = usePagedList(
    true,
    filter,
    (status: ApplicationFilter, page: number, signal?: AbortSignal): Promise<Page<AdminProgramApplicant>> =>
      fetchProgramApplicants(programId, { status: status === 'ALL' ? undefined : status, page }, signal),
    itemKey,
  );
  const showSkeleton = useDelayedFlag(current === null);

  return (
    <section className="flex flex-col gap-4" aria-label="행사 신청자">
      <ProgramHeading programId={programId} />
      <AdminSegments label="신청 상태" segments={APPLICATION_FILTERS} value={filter} onChange={setFilter} />
      {current?.error && (
        <div role="alert" className="flex flex-col gap-3">
          <ErrorNotice
            title="신청자를 불러오지 못했어요"
            error={current.error}
            traceId={current.traceId ?? undefined}
          />
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
        <EmptyState framed title={EMPTY_COPY[filter].title} description={EMPTY_COPY[filter].description} />
      )}
      {current !== null && current.items.length > 0 && (
        <div className="rounded-sheet bg-fill p-1.5">
          <ul className="flex flex-col gap-1" aria-label="신청자 목록">
            {current.items.map((item) => (
              <ApplicantRow key={item.applicationId} item={item} />
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

function ProgramHeading({ programId }: { programId: number }) {
  const { load } = useProgramDetail(programId);
  const showSkeleton = useDelayedFlag(load.status === 'loading');

  return (
    <div className="flex flex-col gap-1">
      <div>
        <BackLink to={LIST_PATH}>행사 목록으로</BackLink>
      </div>
      {load.status === 'loading' && showSkeleton && <Skeleton className="h-7 w-1/2" />}
      {load.status === 'error' && <h2 className="text-xl font-bold text-ink">행사 {programId}번</h2>}
      {load.status === 'ready' && (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <h2 className="min-w-0 break-words text-xl font-bold text-ink">{load.program.title}</h2>
          <StatusText tone={PROGRAM_STATUS_META[load.program.status].tone}>
            {PROGRAM_STATUS_META[load.program.status].label}
          </StatusText>
        </div>
      )}
    </div>
  );
}

function ApplicantRow({ item }: { item: AdminProgramApplicant }) {
  const status = APPLICATION_STATUS_META[item.status];
  return (
    <li className="flex flex-col gap-1.5 rounded-row bg-surface px-4 py-3">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="min-w-0 break-words text-base font-bold text-ink">{item.member.nickname}</span>
        <StatusText tone={status.tone}>{status.label}</StatusText>
      </div>
      <MetaLine
        items={[
          `신청 ${formatKstDateTime(item.createdAt)}`,
          item.paymentDueAt && `결제 기한 ${formatKstDateTime(item.paymentDueAt)}`,
          item.confirmedAt && `확정 ${formatKstDateTime(item.confirmedAt)}`,
          item.canceledAt && `취소 ${formatKstDateTime(item.canceledAt)}`,
        ]}
      />
      {item.cancelReason && <p className="text-sm text-ink">취소 사유 {CANCEL_REASON_LABELS[item.cancelReason]}</p>}
      {item.reviewRequestedAt && (
        <p>
          <StatusText tone="warning" icon="alert">
            이용 정지 회원의 신청이라 확인이 필요해요
          </StatusText>
        </p>
      )}
    </li>
  );
}
