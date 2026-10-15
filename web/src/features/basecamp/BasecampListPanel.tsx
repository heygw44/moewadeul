import type { BasecampSearchItem } from '../../api/types';
import { Link } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { MetaLine, StatusText } from '../../components/StatusText';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { localDateBlock, nightsBetween } from '../../lib/datetime';
import { useSession } from '../member/session';
import { SeatDots } from './SeatDots';
import { BASECAMP_STATUS_META, headcountText, joinConditionSummary, unmetReasonText } from './basecampLabels';
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
        <div className="mx-4 mb-1 flex items-center justify-between gap-2 rounded-control bg-fill py-1 pr-1 pl-4 lg:mx-5">
          <p className="min-w-0 truncate text-sm text-ink">{filteredSpotName ?? '고른 장소'} 베이스캠프만 보여요</p>
          <Button variant="ghost" onClick={onClearSpotFilter} className="shrink-0 px-3">
            모든 장소 보기
          </Button>
        </div>
      )}

      {showSkeleton && result.items.length === 0 ? (
        <div aria-busy="true" className="flex flex-col gap-1 px-4 lg:px-5">
          {[0, 1, 2].map((index) => (
            <div key={index} className="flex flex-col gap-2 rounded-control bg-surface px-4 py-4">
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
            <ul aria-label="이 지역의 베이스캠프" className="flex flex-col gap-1 px-4 pb-6 lg:px-5">
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

// 왼쪽에 출발 날짜 칸, 오른쪽에 제목과 장소·박 수, 자리 점과 합류 조건을 둔다. 내 조건으로 신청할 수 없으면 이유를 경고 색으로 한 줄 더한다.
function BasecampRow({ item }: { item: BasecampSearchItem }) {
  const session = useSession();
  const status = BASECAMP_STATUS_META[item.status];
  const trustLevel = session.me?.trustLevel ?? null;
  const unmet = item.canApply === false ? (item.unmetReasons ?? []) : [];
  const block = localDateBlock(item.startDate);
  const nights = nightsBetween(item.startDate, item.endDate);

  return (
    <li>
      <Link
        to={`/basecamps/${item.basecampId}`}
        className="grid grid-cols-[52px_1fr] gap-3.5 rounded-control bg-surface p-4 transition-colors hover:bg-fill focus-visible:-outline-offset-2"
      >
        <span className="flex flex-col items-center pt-0.5 text-center" aria-hidden="true">
          <b className="text-[26px] leading-none font-extrabold tracking-[-0.03em] text-ink tabular-nums">{block?.day}</b>
          <span className="mt-1 text-[13px] text-ink-muted">
            {block?.month} {block?.weekday}
          </span>
        </span>
        <span className="flex min-w-0 flex-col">
          <span className="min-w-0 break-words text-[17px] leading-snug font-bold text-ink">{item.title}</span>
          <MetaLine
            className="mt-1"
            items={[
              <StatusText tone={status.tone}>{status.label}</StatusText>,
              <span className="truncate">{item.spot.name}</span>,
              nights !== null && nights > 0 && <span className="tabular-nums">{nights}박</span>,
            ]}
          />
          <span className="mt-2 flex flex-wrap items-center gap-x-2.5 gap-y-1 text-sm text-ink">
            <SeatDots headcount={item.headcount} capacity={item.capacity} />
            <span className="tabular-nums">{headcountText(item.headcount, item.capacity)}명</span>
            <span className="text-ink-muted">{joinConditionSummary(item.joinCondition)}</span>
          </span>
          {unmet.length > 0 && (
            <span className="mt-2 text-sm font-semibold text-warning">
              신청할 수 없어요. {unmet.map((reason) => unmetReasonText(reason, trustLevel).reason).join(' ')}
            </span>
          )}
        </span>
      </Link>
    </li>
  );
}
