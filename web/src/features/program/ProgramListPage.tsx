import { useEffect, useState } from 'react';
import { listPrograms } from '../../api/programs';
import type { ProgramListStatus } from '../../api/programs';
import type { ProgramSummary } from '../../api/types';
import { Link } from '../../app/router';
import { Button } from '../../components/Button';
import { buttonClassName } from '../../components/buttonClass';
import { HubLayout } from '../../components/HubLayout';
import { EmptyState } from '../../components/EmptyState';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { StatusText } from '../../components/StatusText';
import { UnderlineTabs } from '../../components/UnderlineTabs';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstTime, kstDateBlock } from '../../lib/datetime';
import { useSession } from '../member/session';
import { feeText, PROGRAM_FILTERS, PROGRAM_STATUS_META, seatsText } from './programLabels';
import { ProgramAside } from './ProgramAside';
import { usePagedList } from './usePagedList';

type ProgramFilter = ProgramListStatus | 'ALL';

const SECONDARY_LINK_CLASS = buttonClassName('secondary');
const KST_MONTH = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit' });

function fetchPrograms(filter: ProgramFilter, page: number, signal?: AbortSignal) {
  return listPrograms(filter === 'ALL' ? undefined : filter, page, signal);
}

export function ProgramListPage() {
  const [filter, setFilter] = useState<ProgramFilter>('ALL');
  const { current, loadingMore, loadMore, retry } = usePagedList(true, filter, fetchPrograms);
  const showSkeleton = useDelayedFlag(current === null);
  const session = useSession();
  const signedIn = session.status === 'authenticated' && session.me !== null;

  useEffect(() => {
    document.title = '공식 행사 · 뫼와들';
  }, []);

  return (
    <HubLayout
      announcement
      title="공식 행사"
      description="지자체와 브랜드가 여는 백패킹 행사에 선착순으로 신청할 수 있어요."
      actions={
        signedIn ? (
          <Link to="/me/program-applications" className={SECONDARY_LINK_CLASS}>
            내 행사 신청
          </Link>
        ) : null
      }
      tabs={<UnderlineTabs label="신청 상태" options={PROGRAM_FILTERS} value={filter} onChange={setFilter} />}
      aside={<ProgramAside />}
    >
      {current?.error && (
        <div className="flex flex-col gap-3">
          <Notice tone="danger" title="행사를 불러오지 못했어요">
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
      <div aria-busy={current === null}>
        {current === null ? (
          showSkeleton ? (
            <ul className="flex flex-col gap-1">
              {Array.from({ length: 3 }, (_, index) => (
                <li key={index} className="flex flex-col gap-2 rounded-control bg-surface px-5 py-5">
                  <Skeleton className="h-5 w-1/2" />
                  <Skeleton className="h-4 w-2/3" />
                </li>
              ))}
            </ul>
          ) : null
        ) : current.items.length === 0 ? (
          current.error ? null : <ProgramEmpty filtered={filter !== 'ALL'} onReset={() => setFilter('ALL')} />
        ) : (
          <>
            {groupByMonth(current.items).map((group) => (
              <section
                key={group.key}
                aria-labelledby={`programs-${group.key}`}
                className="grid gap-3 py-5 first:pt-0 lg:grid-cols-[120px_1fr] lg:gap-8"
              >
                <h2
                  id={`programs-${group.key}`}
                  className="text-[26px] leading-none font-extrabold tracking-[-0.03em] text-ink lg:text-[32px]"
                >
                  {group.label}
                  <span className="mt-2 block text-sm font-normal tracking-normal text-ink-muted">
                    행사 {group.items.length}개
                  </span>
                </h2>
                <ul className="flex flex-col gap-1">
                  {group.items.map((item, index) => (
                    <ProgramRow key={item.programId} item={item} shaded={index % 2 === 1} />
                  ))}
                </ul>
              </section>
            ))}
            {current.hasNext && (
              <div className="pt-2">
                <Button variant="secondary" fullWidth loading={loadingMore} onClick={() => void loadMore()}>
                  더 보기
                </Button>
              </div>
            )}
          </>
        )}
      </div>
    </HubLayout>
  );
}

function ProgramEmpty({ filtered, onReset }: { filtered: boolean; onReset: () => void }) {
  return (
    <EmptyState
      title={filtered ? '해당하는 행사가 없어요' : '지금 볼 수 있는 행사가 없어요'}
      description={filtered ? undefined : '행사가 열리면 이 목록에 올라와요.'}
      action={
        filtered ? (
          <Button variant="secondary" onClick={onReset}>
            전체 보기
          </Button>
        ) : undefined
      }
    />
  );
}


// 받은 행사를 시작일의 한국 달로 묶는다. 서버가 시작일 빠른 순으로 주므로 순서는 그대로 둔다.
function groupByMonth(items: ProgramSummary[]): { key: string; label: string; items: ProgramSummary[] }[] {
  const groups: { key: string; label: string; items: ProgramSummary[] }[] = [];
  for (const item of items) {
    const key = KST_MONTH.format(new Date(item.startAt));
    const last = groups[groups.length - 1];
    if (last && last.key === key) last.items.push(item);
    else groups.push({ key, label: kstDateBlock(item.startAt).month, items: [item] });
  }
  return groups;
}

// 행사 하나를 띠 한 줄로 보여 준다. 왼쪽부터 시작 날짜·요일·시각, 제목과 장소, 상태, 남은 자리와 참가비다.
// 이웃한 띠는 면 색을 번갈아 칠해 선 없이 나눈다.
function ProgramRow({ item, shaded }: { item: ProgramSummary; shaded: boolean }) {
  const status = PROGRAM_STATUS_META[item.status];
  const date = kstDateBlock(item.startAt);
  const monthNumber = date.month.replace('월', '').padStart(2, '0');
  return (
    <li>
      <Link
        to={`/programs/${item.programId}`}
        className={`grid grid-cols-[1fr_auto] items-center gap-x-3 gap-y-1 rounded-control px-4 py-3.5 transition-colors hover:bg-fill-strong lg:min-h-22 lg:grid-cols-[88px_1fr_auto_96px] lg:gap-x-5 lg:px-5 ${shaded ? 'bg-fill' : 'bg-surface'}`}
      >
        <span className="col-span-2 flex items-baseline gap-2 lg:col-span-1 lg:flex-col lg:gap-0">
          <b className="text-lg font-extrabold tracking-[-0.03em] text-ink tabular-nums lg:text-2xl">
            {monthNumber}.{date.day.padStart(2, '0')}
          </b>
          <span className="text-[13px] text-ink-muted tabular-nums">
            {date.weekday}요일 {formatKstTime(item.startAt)}
          </span>
        </span>
        <span className="flex min-w-0 flex-col gap-0.5">
          <b className="break-words text-lg leading-snug font-bold text-ink">{item.title}</b>
          <span className="text-sm text-ink-muted">
            {item.locationText}
            {item.overnight ? ' · 숙박' : ' · 당일'}
          </span>
        </span>
        <span className="col-start-1 row-start-3 lg:col-start-auto lg:row-start-auto">
          <StatusText tone={status.tone}>{status.label}</StatusText>
        </span>
        <span className="col-start-2 row-span-2 row-start-2 flex flex-col text-right tabular-nums lg:col-start-auto lg:row-span-1 lg:row-start-auto">
          <b className="text-[17px] text-ink">
            <span className="sr-only">남은 자리 </span>
            {seatsText(item.remainingSeats, item.capacity)}
          </b>
          <span className="text-sm text-ink-muted">{feeText(item.fee)}</span>
        </span>
      </Link>
    </li>
  );
}
