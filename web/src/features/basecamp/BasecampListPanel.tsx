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
        <div className="flex items-center justify-between gap-2 border-b border-line bg-canvas px-4 py-1">
          <p className="min-w-0 truncate text-sm text-ink">{filteredSpotName ?? '고른 장소'}의 베이스캠프만 보고 있어요</p>
          <Button variant="ghost" onClick={onClearSpotFilter} className="shrink-0 px-3">
            장소 선택 풀기
          </Button>
        </div>
      )}

      {showSkeleton && result.items.length === 0 ? (
        <div aria-busy="true" className="flex flex-col">
          {[0, 1, 2].map((index) => (
            <div key={index} className="flex min-h-14 flex-col gap-2 border-b border-line px-4 py-3">
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
                    ? '조건을 줄이거나 지도를 옮기면 다시 찾아요.'
                    : '지도를 옮기면 다시 찾아요. 장소 상세에서 베이스캠프를 직접 열 수도 있어요.'
              }
            />
          )}

          {visible.length > 0 && (
            <ul aria-label="이 지역 베이스캠프">
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

  return (
    <li>
      <Link
        to={`/basecamps/${item.basecampId}`}
        className="flex min-h-14 flex-col gap-1 border-b border-line px-4 py-3 hover:bg-fill focus-visible:-outline-offset-2"
      >
        <span className="min-w-0 break-words text-base font-semibold text-ink">{item.title}</span>
        <MetaLine
          items={[<StatusText tone={status.tone}>{status.label}</StatusText>, <span className="truncate">{item.spot.name}</span>]}
        />
        <span className="flex items-center justify-between gap-2 text-sm text-ink">
          <span className="tabular-nums">
            {formatLocalDate(item.startDate)} ~ {formatLocalDate(item.endDate)}
          </span>
          <span className="shrink-0 font-mono tabular-nums">{headcountText(item.headcount, item.capacity)}명</span>
        </span>
        <span className="text-sm text-ink-muted">합류 조건: {joinConditionSummary(item.joinCondition)}</span>
        {unmet.length > 0 && (
          <span className="text-sm text-warning">
            신청할 수 없어요: {unmet.map((reason) => unmetReasonText(reason, trustLevel).reason).join(' ')}
          </span>
        )}
      </Link>
    </li>
  );
}
