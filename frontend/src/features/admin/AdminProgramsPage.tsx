import { useEffect, useState } from 'react';
import { cancelProgram } from '../../api/admin';
import { listPrograms } from '../../api/programs';
import type { ProgramListStatus } from '../../api/programs';
import type { Page, ProgramSummary } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { buttonClassName } from '../../components/buttonClass';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { StatusText } from '../../components/StatusText';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { usePagedList } from '../../components/usePagedList';
import { formatKstDateTime } from '../../lib/datetime';
import { feeText, PROGRAM_FILTERS, PROGRAM_STATUS_META, seatsText } from '../program/programLabels';
import { AdminPage } from './AdminGate';
import { AdminSegments } from './AdminSegments';
import { LIST_PATH, SAVED_NOTICES, savedKindFrom } from './adminProgramForm';

type ProgramFilter = ProgramListStatus | 'ALL';

const itemKey = (item: ProgramSummary) => item.programId;

const EMPTY_COPY: Record<ProgramFilter, { title: string; description: string }> = {
  ALL: { title: '등록한 행사가 없어요', description: '행사 등록을 눌러 첫 행사를 만들어 주세요.' },
  UPCOMING: { title: '신청 예정인 행사가 없어요', description: '신청이 열리기를 기다리는 행사가 여기에 모여요.' },
  OPEN: { title: '신청을 받는 행사가 없어요', description: '신청을 받는 중인 행사가 여기에 모여요.' },
  CLOSED: { title: '마감한 행사가 없어요', description: '신청 기간이 끝난 행사가 여기에 모여요.' },
};

function fetchPrograms(filter: ProgramFilter, page: number, signal?: AbortSignal): Promise<Page<ProgramSummary>> {
  return listPrograms(filter === 'ALL' ? undefined : filter, page, signal);
}

function savedNotice(search: string): string | null {
  const kind = savedKindFrom(search);
  return kind === null ? null : SAVED_NOTICES[kind];
}

function canceledNotice(canceledApplicationCount: number): string {
  return canceledApplicationCount === 0
    ? '행사를 취소했어요. 취소할 신청은 없었어요.'
    : `행사를 취소했어요. 신청 ${canceledApplicationCount}건도 함께 취소했어요.`;
}

export function AdminProgramsPage() {
  return (
    <AdminPage title="행사 관리" current={LIST_PATH}>
      <ProgramManagement />
    </AdminPage>
  );
}

function ProgramManagement() {
  const { search } = useLocation();
  const [filter, setFilter] = useState<ProgramFilter>('ALL');
  const [notice, setNotice] = useState<string | null>(() => savedNotice(search));
  const { current, loadingMore, loadMore, retry, reload, updateItems } = usePagedList(
    true,
    filter,
    fetchPrograms,
    itemKey,
  );
  const showSkeleton = useDelayedFlag(current === null);

  useEffect(() => {
    if (savedKindFrom(search) !== null) navigate(LIST_PATH, { replace: true });
  }, [search]);

  function handleCanceled(programId: number, canceledApplicationCount: number) {
    setNotice(canceledNotice(canceledApplicationCount));
    updateItems((items) => items.filter((item) => item.programId !== programId));
    reload();
  }

  return (
    <section className="flex flex-col gap-4" aria-label="행사 관리 목록">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <p className="min-w-0 flex-1 text-sm text-ink-muted">취소한 행사는 이 목록에 나오지 않아요.</p>
        <Link to="/admin/programs/new" className={buttonClassName('primary')}>
          행사 등록
        </Link>
      </div>
      {notice !== null && (
        <div role="status">
          <Notice tone="info" title={notice} />
        </div>
      )}
      <AdminSegments label="신청 상태" segments={PROGRAM_FILTERS} value={filter} onChange={setFilter} />
      {current?.error && (
        <div role="alert" className="flex flex-col gap-3">
          <ErrorNotice title="행사를 불러오지 못했어요" error={current.error} traceId={current.traceId ?? undefined} />
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
        <ul className="flex flex-col gap-3" aria-label="행사 목록">
          {current.items.map((item) => (
            <ProgramCard key={item.programId} item={item} onCanceled={handleCanceled} onStale={reload} />
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

type ProgramCardProps = {
  item: ProgramSummary;
  onCanceled: (programId: number, canceledApplicationCount: number) => void;
  onStale: () => void;
};

function ProgramCard({ item, onCanceled, onStale }: ProgramCardProps) {
  const [confirming, setConfirming] = useState(false);
  const status = PROGRAM_STATUS_META[item.status];

  return (
    <li className="flex flex-col gap-3 rounded-sheet bg-fill p-5">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="min-w-0 break-words text-lg font-semibold text-ink">{item.title}</h3>
        <StatusText tone={status.tone}>{status.label}</StatusText>
        {item.overnight && <StatusText tone="neutral">숙박</StatusText>}
      </div>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
        <dt className="text-ink-muted">신청 기간</dt>
        <dd className="min-w-0 break-words font-mono tabular-nums text-ink">
          {formatKstDateTime(item.applyOpenAt)} ~ {formatKstDateTime(item.applyCloseAt)}
        </dd>
        <dt className="text-ink-muted">행사 일정</dt>
        <dd className="min-w-0 break-words font-mono tabular-nums text-ink">
          {formatKstDateTime(item.startAt)} ~ {formatKstDateTime(item.endAt)}
        </dd>
        <dt className="text-ink-muted">남은 자리</dt>
        <dd className="font-mono tabular-nums text-ink">{seatsText(item.remainingSeats, item.capacity)}</dd>
        <dt className="text-ink-muted">참가비</dt>
        <dd className="font-mono tabular-nums text-ink">{feeText(item.fee)}</dd>
      </dl>
      <div className="flex flex-wrap items-center gap-2">
        <Link
          to={`/admin/programs/${item.programId}/applications`}
          aria-label={`${item.title} 신청자 보기`}
          className={buttonClassName('secondary')}
        >
          신청자 보기
        </Link>
        <Link
          to={`/admin/programs/${item.programId}/edit`}
          aria-label={`${item.title} 수정`}
          className={buttonClassName('secondary')}
        >
          수정
        </Link>
        <Button variant="danger" aria-label={`${item.title} 행사 취소`} onClick={() => setConfirming(true)}>
          행사 취소
        </Button>
      </div>
      {confirming && (
        <CancelDialog
          program={item}
          onClose={(failed) => {
            setConfirming(false);
            if (failed) onStale();
          }}
          onCanceled={(count) => {
            setConfirming(false);
            onCanceled(item.programId, count);
          }}
        />
      )}
    </li>
  );
}

type CancelDialogProps = {
  program: ProgramSummary;
  onClose: (failed: boolean) => void;
  onCanceled: (canceledApplicationCount: number) => void;
};

function CancelDialog({ program, onClose, onCanceled }: CancelDialogProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const failed = error !== null;

  async function confirm() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const result = await cancelProgram(program.programId);
      onCanceled(result.canceledApplicationCount);
    } catch (caught) {
      setError(caught);
      setBusy(false);
    }
  }

  return (
    <Dialog title="행사를 취소할까요?" onClose={() => !busy && onClose(failed)}>
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-2 text-base text-ink">
          <p className="break-words">&apos;{program.title}&apos; 행사를 취소해요.</p>
          <p>이 행사에 들어온 신청은 모두 취소돼요. 결제한 신청은 환불되고, 신청한 회원에게 알림이 가요.</p>
          <p className="font-semibold">취소한 행사는 되돌릴 수 없어요.</p>
        </div>
        {failed && (
          <div role="alert">
            <ErrorNotice title="행사를 취소하지 못했어요" error={error} />
          </div>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" disabled={busy} onClick={() => onClose(failed)}>
            {failed ? '닫기' : '아니요, 그대로 둘게요'}
          </Button>
          <Button variant="danger" loading={busy} onClick={() => void confirm()}>
            행사 취소하기
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
