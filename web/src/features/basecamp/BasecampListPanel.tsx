import type { BasecampSearchItem } from '../../api/types';
import { Link } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { Notice } from '../../components/Notice';
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
  unmetReasonText,
} from './basecampLabels';
import type { BasecampsInView } from './useBasecampsInView';

type BasecampListPanelProps = {
  result: BasecampsInView;
  // 조건 대화상자에서 고른 조건이 하나라도 켜져 있는지다. 결과가 없을 때 안내 문구를 고른다.
  filtered: boolean;
  // 지도에서 고른 장소다. 있으면 그 장소의 베이스캠프만 보여 준다.
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
        <div className="flex items-center justify-between gap-2 border-b border-line bg-canvas px-4 py-1 lg:px-5">
          <p className="min-w-0 truncate text-sm text-ink">{filteredSpotName ?? '고른 장소'} 베이스캠프만 보여요</p>
          <Button variant="ghost" onClick={onClearSpotFilter} className="shrink-0 px-3">
            모든 장소 보기
          </Button>
        </div>
      )}

      {showSkeleton && result.items.length === 0 ? (
        <div aria-busy="true" className="flex flex-col">
          {[0, 1, 2].map((index) => (
            <div key={index} className="flex min-h-16 flex-col gap-2 border-b border-line px-4 py-3 lg:px-5">
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="h-3 w-1/2" />
            </div>
          ))}
        </div>
      ) : (
        <>
          {result.status === 'error' && result.error && (
            <div className="flex flex-col gap-3 px-4 pt-4">
              <Notice tone="danger" title="베이스캠프를 불러오지 못했어요">
                {result.error}
              </Notice>
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
            <ul
              aria-label="이 지역의 베이스캠프"
              className="mx-3 my-3 divide-y divide-line overflow-hidden rounded-sheet border border-line bg-surface lg:mx-5"
            >
              {visible.map((item) => (
                <BasecampRow key={item.basecampId} item={item} />
              ))}
            </ul>
          )}

          {result.hasNext && (
            <div className="flex flex-col gap-2 px-4 py-4">
              {result.moreError && (
                <p role="alert" className="text-sm text-danger">
                  {result.moreError}
                </p>
              )}
              <Button variant="secondary" fullWidth loading={result.loadingMore} onClick={result.loadMore}>
                더 보기
              </Button>
            </div>
          )}
        </>
      )}
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

  // 왼쪽에 출발 날짜 칸, 가운데 제목과 상태·장소·박 수, 오른쪽에 자리 수를 둔다.
  // 날짜를 칸으로 세워서 목록을 위아래로 훑으면 출발일 순서가 먼저 읽힌다.
  return (
    <li>
      <Link
        to={`/basecamps/${item.basecampId}`}
        className="grid min-h-16 grid-cols-[3.25rem_minmax(0,1fr)_auto] items-start gap-3 px-4 py-3 hover:bg-fill focus-visible:-outline-offset-2"
      >
        <span className="flex flex-col items-center rounded-row bg-fill py-1.5 text-center">
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
