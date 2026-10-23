import { useEffect, useRef, useState } from 'react';
import { fetchSyncJobRuns, startSyncJob } from '../../api/admin';
import type { SyncJobRun, SyncJobStatus, SyncJobType } from '../../api/types';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { StatusText } from '../../components/StatusText';
import type { StatusTone } from '../../components/StatusText';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime } from '../../lib/datetime';
import { AdminPage } from './AdminGate';
import { AdminSegments } from './AdminSegments';
import { adminErrorMessage } from './adminErrors';

// 실행 기록에서 종류를 가리지 않는 칸의 값이다.
const ALL_JOBS = 'ALL';

const JOB_TYPES: readonly SyncJobType[] = ['GOCAMPING', 'FOREST', 'FOREST_STANDARD', 'PARK_BOUNDARY', 'BAKJI_REJUDGE'];

const JOB_LABELS: Record<SyncJobType, string> = {
  GOCAMPING: '고캠핑',
  FOREST: '국립 자연휴양림',
  FOREST_STANDARD: '공립·사립 휴양림',
  PARK_BOUNDARY: '공원 경계',
  BAKJI_REJUDGE: '박지 재판정',
};

// 실행 전 확인 창에 작업이 무엇을 다시 읽는지 적는다. 운영자가 원천을 착각하고 실행하지 않게 하기 위해서다.
const JOB_DESCRIPTIONS: Record<SyncJobType, string> = {
  GOCAMPING: '고캠핑 API에서 바뀐 야영장을 받아 반영해요.',
  FOREST: '서버에 둔 국립자연휴양림 파일을 다시 읽어 적재해요.',
  FOREST_STANDARD: '서버에 둔 전국휴양림표준데이터 파일을 다시 읽어 적재해요. 국립 휴양림을 먼저 적재해 두어야 해요.',
  PARK_BOUNDARY: '서버에 둔 공원 경계 파일을 다시 읽어 적재해요. 성공하면 박지 재판정이 이어서 실행돼요.',
  BAKJI_REJUDGE: '지금 적재된 공원 경계로 모든 박지의 공원 경고를 다시 판정해요.',
};

const STATUS_META: Record<SyncJobStatus, { label: string; tone: StatusTone }> = {
  RUNNING: { label: '실행 중', tone: 'info' },
  COMPLETED: { label: '완료', tone: 'accent' },
  FAILED: { label: '실패', tone: 'danger' },
};

// 서버는 고캠핑을 하루에 한 번 동기화한다. 이 시간이 지나도록 성공이 없으면 화면이 경고한다.
const GOCAMPING_STALE_HOURS = 48;
const GOCAMPING_LOOKBACK = 50;
const POLL_INTERVAL_MS = 10_000;

type CardEntry = { latest: SyncJobRun | null; lastSuccess: SyncJobRun | null };
type CardsState = Record<SyncJobType, CardEntry>;
type HistoryState = {
  items: SyncJobRun[];
  page: number;
  hasNext: boolean;
  filter: SyncJobType | null;
  error: Failure | null;
};

// 요청이 실패했을 때 화면에 보여 줄 문구와 원래 오류다. 원래 오류는 서버 오류의 문의 번호를 꺼내는 데 쓴다.
type Failure = { message: string; error: unknown };

function toFailure(error: unknown): Failure {
  return { message: adminErrorMessage(error), error };
}

async function loadCards(signal: AbortSignal): Promise<CardsState> {
  const pages = await Promise.all(
    JOB_TYPES.map((jobType) =>
      fetchSyncJobRuns({ jobType, page: 0, size: jobType === 'GOCAMPING' ? GOCAMPING_LOOKBACK : 1 }, signal),
    ),
  );
  const entries = JOB_TYPES.map((jobType, index): [SyncJobType, CardEntry] => {
    const items = pages[index]?.content ?? [];
    return [
      jobType,
      { latest: items[0] ?? null, lastSuccess: items.find((run) => run.status === 'COMPLETED') ?? null },
    ];
  });
  return Object.fromEntries(entries) as CardsState;
}

export function AdminSyncJobsPage() {
  return (
    <AdminPage title="데이터 적재" current="/admin/sync-jobs">
      <SyncJobs />
    </AdminPage>
  );
}

function SyncJobs() {
  const [filter, setFilter] = useState<SyncJobType | null>(null);
  const [tick, setTick] = useState(0);
  const [cards, setCards] = useState<CardsState | null>(null);
  const [cardsError, setCardsError] = useState<Failure | null>(null);
  const [history, setHistory] = useState<HistoryState | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const inFlight = useRef(0);
  const pollRef = useRef(false);
  const historyPageRef = useRef(0);
  const showCardSkeleton = useDelayedFlag(cards === null && cardsError === null);
  const showHistorySkeleton = useDelayedFlag(history === null);

  const anyRunning = cards !== null && JOB_TYPES.some((jobType) => cards[jobType].latest?.status === 'RUNNING');

  useEffect(() => {
    const controller = new AbortController();
    inFlight.current += 1;
    loadCards(controller.signal)
      .then(
        (next) => {
          if (controller.signal.aborted) return;
          setCards(next);
          setCardsError(null);
        },
        (caught: unknown) => {
          if (!controller.signal.aborted) setCardsError(toFailure(caught));
        },
      )
      .finally(() => {
        inFlight.current -= 1;
      });
    return () => controller.abort();
  }, [tick]);

  useEffect(() => {
    historyPageRef.current = history?.page ?? 0;
  }, [history]);

  useEffect(() => {
    // 주기적으로 다시 읽는 경우에 사용자가 더 보기로 불러 둔 목록은 건드리지 않는다. 첫 쪽만 보고 있을 때만 새로 읽는다.
    const polled = pollRef.current;
    pollRef.current = false;
    if (polled && historyPageRef.current > 0) return;
    const controller = new AbortController();
    inFlight.current += 1;
    fetchSyncJobRuns({ jobType: filter ?? undefined, page: 0 }, controller.signal)
      .then(
        (page) => {
          if (!controller.signal.aborted) {
            setHistory({ items: page.content, page: page.page, hasNext: page.hasNext, filter, error: null });
          }
        },
        (caught: unknown) => {
          if (!controller.signal.aborted) {
            setHistory((previous) => {
              // 다른 필터의 목록을 새 필터의 목록으로 오인하지 않도록 같은 필터일 때만 이어 쓴다.
              const same = previous !== null && previous.filter === filter;
              return {
                items: same ? previous.items : [],
                page: same ? previous.page : 0,
                hasNext: same ? previous.hasNext : false,
                filter,
                error: toFailure(caught),
              };
            });
          }
        },
      )
      .finally(() => {
        inFlight.current -= 1;
      });
    return () => controller.abort();
  }, [filter, tick]);

  // 실행 중인 작업이 있는 동안만 10초마다 다시 읽는다. 앞선 요청이 끝나지 않았으면 건너뛴다.
  useEffect(() => {
    if (!anyRunning) return;
    const timer = window.setInterval(() => {
      if (inFlight.current === 0) {
        pollRef.current = true;
        setTick((value) => value + 1);
      }
    }, POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [anyRunning]);

  async function loadMore() {
    if (!history || loadingMore) return;
    // 요청을 보내는 동안 필터가 바뀌거나 목록이 첫 쪽으로 돌아갈 수 있어서, 시작한 때의 필터와 쪽을 기억한다.
    const startFilter = filter;
    const startPage = history.page;
    const stillCurrent = (previous: HistoryState | null): previous is HistoryState =>
      previous !== null && previous.filter === startFilter && previous.page === startPage;
    setLoadingMore(true);
    try {
      const next = await fetchSyncJobRuns({ jobType: startFilter ?? undefined, page: startPage + 1 });
      setHistory((previous) =>
        stillCurrent(previous)
          ? {
              items: [...previous.items, ...next.content],
              page: next.page,
              hasNext: next.hasNext,
              filter: startFilter,
              error: null,
            }
          : previous,
      );
    } catch (caught) {
      setHistory((previous) => (stillCurrent(previous) ? { ...previous, error: toFailure(caught) } : previous));
    } finally {
      setLoadingMore(false);
    }
  }

  function reload() {
    setTick((value) => value + 1);
  }

  const gocamping = cards?.GOCAMPING;
  const staleSince = gocamping ? staleMessage(gocamping.lastSuccess) : null;

  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-4" aria-label="작업별 상태">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-semibold text-ink">작업별 상태</h2>
          <Button variant="secondary" onClick={reload}>
            새로 고침
          </Button>
        </div>
        {anyRunning && <p className="text-sm text-ink-muted">실행 중인 작업이 있어서 10초마다 새로 읽어요.</p>}
        {cardsError && (
          <div role="alert" className="flex flex-col gap-3">
            <ErrorNotice title="작업 상태를 불러오지 못했어요" error={cardsError.error} message={cardsError.message} />
            {cards === null && (
              <div>
                <Button variant="secondary" onClick={reload}>
                  다시 불러오기
                </Button>
              </div>
            )}
          </div>
        )}
        {cards === null && !cardsError && showCardSkeleton && <Skeleton className="h-40 w-full" />}
        {staleSince !== null && (
          <Notice tone="warning" title="고캠핑 동기화가 2일 넘게 성공하지 못했어요">
            <p>{staleSince}</p>
          </Notice>
        )}
        {cards !== null && (
          <ul className="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3">
            {JOB_TYPES.map((jobType) => (
              <JobCard key={jobType} jobType={jobType} latest={cards[jobType].latest} onStarted={reload} />
            ))}
          </ul>
        )}
      </section>

      <section className="flex flex-col gap-4" aria-label="실행 기록">
        <h2 className="text-lg font-semibold text-ink">실행 기록</h2>
        <AdminSegments
          label="작업 종류"
          segments={[
            { value: ALL_JOBS, label: '전체' },
            ...JOB_TYPES.map((jobType) => ({ value: jobType, label: JOB_LABELS[jobType] })),
          ]}
          value={filter ?? ALL_JOBS}
          onChange={(value) => {
            setHistory(null);
            setFilter(value === ALL_JOBS ? null : value);
          }}
        />
        {history?.error && (
          <div role="alert" className="flex flex-col gap-3">
            <ErrorNotice
              title="실행 기록을 불러오지 못했어요"
              error={history.error.error}
              message={history.error.message}
            />
            {history.items.length === 0 && (
              <div>
                <Button variant="secondary" onClick={reload}>
                  다시 불러오기
                </Button>
              </div>
            )}
          </div>
        )}
        {history === null && showHistorySkeleton && <Skeleton className="h-40 w-full" />}
        {history !== null && history.items.length === 0 && !history.error && (
          <EmptyState framed title="실행 기록이 없어요" description="작업을 실행하면 여기에 기록이 쌓여요." />
        )}
        {history !== null && history.items.length > 0 && (
          <ul className="flex flex-col gap-1 rounded-sheet bg-fill p-1.5">
            {history.items.map((run) => (
              <HistoryRow key={run.jobRunId} run={run} />
            ))}
          </ul>
        )}
        {history?.hasNext && (
          <div>
            <Button variant="secondary" loading={loadingMore} onClick={() => void loadMore()}>
              더 보기
            </Button>
          </div>
        )}
      </section>
    </div>
  );
}

function staleMessage(lastSuccess: SyncJobRun | null): string | null {
  if (lastSuccess === null) return `최근 ${GOCAMPING_LOOKBACK}번 실행 중에 성공한 기록이 없어요.`;
  if (lastSuccess.finishedAt === null) return null;
  const elapsed = Date.now() - new Date(lastSuccess.finishedAt).getTime();
  if (elapsed <= GOCAMPING_STALE_HOURS * 60 * 60 * 1000) return null;
  return `마지막 성공은 ${formatKstDateTime(lastSuccess.finishedAt)}이에요.`;
}

function formatCount(value: number): string {
  return value.toLocaleString('ko-KR');
}

function RunStatus({ status }: { status: SyncJobStatus }) {
  const meta = STATUS_META[status];
  return (
    <StatusText tone={meta.tone} icon={status === 'FAILED' ? 'alert' : undefined}>
      {meta.label}
    </StatusText>
  );
}

function JobCard({
  jobType,
  latest,
  onStarted,
}: {
  jobType: SyncJobType;
  latest: SyncJobRun | null;
  onStarted: () => void;
}) {
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Failure | null>(null);
  const label = JOB_LABELS[jobType];
  const running = latest?.status === 'RUNNING';

  async function run() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await startSyncJob(jobType);
      setConfirm(false);
      onStarted();
    } catch (caught) {
      setError(toFailure(caught));
      setConfirm(false);
      // 이미 실행 중이라는 응답이면 화면의 상태가 낡은 것이므로 다시 읽어서 버튼을 끈다.
      onStarted();
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="flex min-w-0 flex-col gap-3 rounded-sheet bg-fill p-5">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="min-w-0 break-words text-lg font-semibold text-ink">{label}</h3>
        {latest && <RunStatus status={latest.status} />}
      </div>
      {latest === null ? (
        <p className="text-sm text-ink-muted">아직 실행한 적이 없어요</p>
      ) : (
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
          <dt className="text-ink-muted">시작</dt>
          <dd className="font-mono tabular-nums text-ink">{formatKstDateTime(latest.startedAt)}</dd>
          <dt className="text-ink-muted">끝남</dt>
          <dd className="font-mono tabular-nums text-ink">
            {latest.finishedAt ? formatKstDateTime(latest.finishedAt) : '-'}
          </dd>
          <dt className="text-ink-muted">처리</dt>
          <dd className="text-ink-muted">
            <Count value={latest.processedCount} />
          </dd>
          <dt className="text-ink-muted">건너뜀</dt>
          <dd className="text-ink-muted">
            <Count value={latest.skippedCount} />
          </dd>
        </dl>
      )}
      {latest?.status === 'FAILED' && latest.errorMessage && (
        <p className="break-words rounded-row bg-surface px-4 py-2.5 text-sm text-danger">{latest.errorMessage}</p>
      )}
      {jobType === 'PARK_BOUNDARY' && <p className="text-sm text-ink-muted">성공하면 박지 재판정이 이어서 실행돼요</p>}
      {error && (
        <div role="alert">
          <ErrorNotice title="작업을 실행하지 못했어요" error={error.error} message={error.message} />
        </div>
      )}
      <div>
        <Button
          variant="secondary"
          loading={busy}
          disabled={running || busy}
          disabledReason={running ? '이미 실행 중이에요' : undefined}
          onClick={() => setConfirm(true)}
        >
          실행
        </Button>
      </div>
      {confirm && (
        <Dialog title={`${label} 작업을 실행할까요?`} onClose={() => !busy && setConfirm(false)}>
          <div className="flex flex-col gap-4">
            <p className="text-base text-ink">
              {JOB_DESCRIPTIONS[jobType]} 작업은 서버에서 따로 실행되고, 결과는 이 화면에서 확인해요.
            </p>
            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="secondary" disabled={busy} onClick={() => setConfirm(false)}>
                취소
              </Button>
              <Button loading={busy} onClick={() => void run()}>
                실행
              </Button>
            </div>
          </div>
        </Dialog>
      )}
    </li>
  );
}

// 실행 한 번을 목록판의 한 행으로 적는다. 작업과 상태를 앞에 두고, 시각과 건수를 한 줄로 잇는다.
function HistoryRow({ run }: { run: SyncJobRun }) {
  return (
    <li className="flex min-w-0 flex-col gap-1 rounded-row bg-surface px-4 py-3">
      <p className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="text-base font-bold text-ink">{JOB_LABELS[run.jobType]}</span>
        <RunStatus status={run.status} />
      </p>
      <p className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-ink-muted">
        <span className="font-mono tabular-nums">
          {formatKstDateTime(run.startedAt)}
          {run.finishedAt && ` ~ ${formatKstDateTime(run.finishedAt)}`}
        </span>
        <span>
          처리 <Count value={run.processedCount} />
        </span>
        <span>
          건너뜀 <Count value={run.skippedCount} />
        </span>
      </p>
      {run.errorMessage && (
        <p className="min-w-0 break-words rounded-row bg-danger-soft px-4 py-2.5 text-sm text-danger">
          {run.errorMessage}
        </p>
      )}
    </li>
  );
}

// 건수는 굵은 숫자에 작은 단위를 붙인다.
function Count({ value }: { value: number }) {
  return (
    <span className="whitespace-nowrap">
      <b className="font-mono font-extrabold tabular-nums text-ink">{formatCount(value)}</b>
      <span className="text-xs font-semibold">건</span>
    </span>
  );
}
