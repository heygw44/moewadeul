import type { BasecampSearchItem } from '../../api/types';
import { Link } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Skeleton } from '../../components/Skeleton';
import { MetaLine, StatusText } from '../../components/StatusText';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatLocalDate } from '../../lib/datetime';
import { useSession } from '../member/session';
import {
  BASECAMP_STATUS_META,
  headcountText,
  joinConditionSummary,
  nightsBetween,
  OFF_PLATFORM_PAYMENT_REMINDER,
  unmetReasonText,
} from './basecampLabels';
import type { BasecampsInView } from './useBasecampsInView';

type BasecampListPanelProps = {
  result: BasecampsInView;
  filtered: boolean;
  spotFilterId: number | null;
  onClearSpotFilter: () => void;
  waitingForMap: boolean;
};

export function BasecampListPanel({
  result,
  filtered,
  spotFilterId,
  onClearSpotFilter,
  waitingForMap,
}: BasecampListPanelProps) {
  const showSkeleton = useDelayedFlag(result.status === 'loading' || waitingForMap);
  const visible =
    spotFilterId === null ? result.items : result.items.filter((item) => item.spot.spotId === spotFilterId);
  const filteredSpotName = spotFilterId === null ? null : (visible[0]?.spot.name ?? null);

  return (
    <div className="flex flex-col">
      {spotFilterId !== null && (
        <div className="mx-2 mb-1 flex items-center justify-between gap-2 rounded-row bg-fill py-1 pr-1 pl-4 lg:mx-3">
          <p className="min-w-0 truncate text-sm text-ink">{filteredSpotName ?? '고른 장소'} 베이스캠프만 보여요</p>
          <Button variant="ghost" onClick={onClearSpotFilter} className="shrink-0 px-3">
            모든 장소 보기
          </Button>
        </div>
      )}

      {showSkeleton && result.items.length === 0 ? (
        <div aria-busy="true" className="flex flex-col">
          {[0, 1, 2].map((index) => (
            <div key={index} className="flex min-h-16 flex-col gap-2 px-4 py-3 lg:px-5">
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="h-3 w-1/2" />
            </div>
          ))}
        </div>
      ) : (
        <>
          {result.status === 'error' && result.error && (
            <div className="flex flex-col gap-3 px-4 pt-4">
              <ErrorNotice
                title="베이스캠프를 불러오지 못했어요"
                error={result.error}
                traceId={result.traceId ?? undefined}
              />
              <div>
                <Button variant="secondary" onClick={result.retry}>
                  다시 불러오기
                </Button>
              </div>
            </div>
          )}

          {result.status === 'ready' && visible.length === 0 && (
            <EmptyState
              title={
                filtered && spotFilterId === null
                  ? '조건에 맞는 베이스캠프가 없어요'
                  : '이 지역에는 모집 중인 베이스캠프가 없어요'
              }
              description={
                spotFilterId !== null
                  ? undefined
                  : filtered
                    ? '조건을 줄이거나 지도를 다른 곳으로 옮겨 주세요.'
                    : '지도를 다른 곳으로 옮겨 주세요. 장소를 고르면 베이스캠프를 직접 열 수도 있어요.'
              }
            />
          )}

          {visible.length > 0 && (
            <ul aria-label="이 지역의 베이스캠프" className="mx-2 mb-4 flex flex-col gap-0.5 lg:mx-3">
              {visible.map((item) => (
                <BasecampRow key={item.basecampId} item={item} />
              ))}
            </ul>
          )}

          {result.hasNext && (
            <div className="flex flex-col gap-2 px-4 py-4">
              {result.moreError && (
                <div role="alert" className="flex flex-col gap-1">
                  <p className="text-sm text-danger">{result.moreError}</p>
                  {result.traceId && <p className="font-mono text-xs text-ink-muted">문의 번호 {result.traceId}</p>}
                </div>
              )}
              <Button variant="secondary" fullWidth loading={result.loadingMore} onClick={result.loadMore}>
                더 보기
              </Button>
            </div>
          )}
        </>
      )}

      <p className="px-4 pt-2 pb-4 text-sm text-ink-muted lg:px-5">{OFF_PLATFORM_PAYMENT_REMINDER}</p>
    </div>
  );
}

function BasecampRow({ item }: { item: BasecampSearchItem }) {
  const session = useSession();
  const status = BASECAMP_STATUS_META[item.status];
  const trustLevel = session.me?.trustLevel ?? null;
  const unmet = item.canApply === false ? (item.unmetReasons ?? []) : [];

  const [, month, day] = item.startDate.split('-');
  const weekday = formatLocalDate(item.startDate).match(/\((.)\)/)?.[1] ?? '';

  return (
    <li>
      <Link
        to={`/basecamps/${item.basecampId}`}
        className="grid min-h-16 grid-cols-[3.25rem_minmax(0,1fr)_auto] items-start gap-3 rounded-row px-3 py-3.5 hover:bg-fill focus-visible:-outline-offset-2"
      >
        <span className="flex flex-col items-center rounded-[0.875rem] bg-fill-strong py-1.5 text-center">
          <span className="font-mono text-xl leading-none font-extrabold tabular-nums text-ink">{Number(day)}</span>
          <span className="mt-1 text-[0.6875rem] text-ink-muted">
            {Number(month)}월 {weekday}
          </span>
        </span>
        <span className="flex min-w-0 flex-col gap-0.5">
          <span className="min-w-0 break-words text-base font-bold tracking-[-0.01em] text-ink">{item.title}</span>
          <MetaLine
            className="text-[0.8125rem]"
            items={[
              <StatusText tone={status.tone}>{status.label}</StatusText>,
              `${nightsBetween(item.startDate, item.endDate)}박`,
            ]}
          />
          <span className="truncate text-[0.8125rem] text-ink">{item.spot.name}</span>
          <span className="text-[0.8125rem] text-ink-muted">합류 조건: {joinConditionSummary(item.joinCondition)}</span>
          {unmet.length > 0 && (
            <span className="text-[0.8125rem] font-semibold text-warning">
              신청할 수 없어요. {unmet.map((reason) => unmetReasonText(reason, trustLevel).reason).join(' ')}
            </span>
          )}
        </span>
        <span className="pt-0.5 font-mono text-base font-extrabold tabular-nums text-ink">
          {headcountText(item.headcount, item.capacity)}
          <span className="ml-0.5 text-xs font-semibold text-ink-muted">명</span>
        </span>
      </Link>
    </li>
  );
}
