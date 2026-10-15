import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { toUserMessage } from '../../api/errors';
import { fetchSpotDetail } from '../../api/spots';
import type { SpotDetail } from '../../api/types';
import { Link } from '../../app/router';
import { buttonClassName } from '../../components/buttonClass';
import { Icon } from '../../components/icons';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstTime, formatLocalDate } from '../../lib/datetime';
import { SPOT_TYPE_META, sourceLabel } from './spotLabels';

type SpotPreviewBoardProps = {
  spotId: number;
  onClose: () => void;
};

type PreviewResult = { spotId: number; detail: SpotDetail } | { spotId: number; error: unknown };

// 데스크톱 지도에서 장소를 고르면 목록을 둔 채 지도 오른쪽 위에 띄우는 근거 판이다.
// 탐방로 안내판처럼 숲색 머리에 이름을 적고, 판단에 필요한 근거(공원 경계·운영·날씨·예상 인원)를 행마다 출처와 함께 적는다.
// 장소 상세 응답을 그대로 쓰고, 더 자세한 내용은 '자세히 보기'로 왼쪽 패널의 상세에서 본다.
export function SpotPreviewBoard({ spotId, onClose }: SpotPreviewBoardProps) {
  const [result, setResult] = useState<PreviewResult | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchSpotDetail(spotId, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setResult({ spotId, detail });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ spotId, error });
      },
    );
    return () => controller.abort();
  }, [spotId]);

  const current = result?.spotId === spotId ? result : null;
  const showSkeleton = useDelayedFlag(current === null);
  const detail = current && 'detail' in current ? current.detail : null;

  return (
    <section
      aria-label="고른 장소의 근거"
      className="pointer-events-auto w-[22.5rem] overflow-hidden rounded-sheet bg-surface shadow-raise [&_.board-head_:focus-visible]:outline-white"
    >
      <div className="board-head flex items-start gap-2 bg-brand py-4 pr-2 pl-5 text-white">
        <div className="min-w-0 flex-1">
          {detail ? (
            <>
              <h2 className="text-2xl leading-tight font-extrabold tracking-[-0.025em] break-words">{detail.name}</h2>
              <p className="mt-1 text-[0.8125rem] text-brand-ink">
                {[SPOT_TYPE_META[detail.type].label, detail.address, ratingText(detail)].filter(Boolean).join(' · ')}
              </p>
            </>
          ) : (
            <h2 className="text-2xl font-extrabold tracking-[-0.025em]">장소 근거</h2>
          )}
        </div>
        <button
          type="button"
          aria-label="근거 판 닫기"
          onClick={onClose}
          className="flex size-11 shrink-0 items-center justify-center rounded-control text-white hover:bg-brand-raised"
        >
          <Icon name="close" size={22} />
        </button>
      </div>

      {current === null ? (
        showSkeleton && (
          <div aria-busy="true" className="flex flex-col gap-3 p-5">
            <Skeleton className="h-5 w-2/3" />
            <Skeleton className="h-5 w-1/2" />
            <Skeleton className="h-5 w-3/4" />
          </div>
        )
      ) : detail === null ? (
        <p role="alert" className="p-5 text-sm text-danger">
          {'error' in current ? toUserMessage(current.error) : null}
        </p>
      ) : (
        <>
          <dl>
            <ParkRow detail={detail} />
            <OperationRow detail={detail} />
            <WeatherRow detail={detail} />
            <PeopleRow detail={detail} />
          </dl>
          <div className="border-t border-line px-5 pt-4 pb-5">
            <Link to={`/spots/${detail.spotId}`} className={`${buttonClassName('primary')} w-full`}>
              {detail.recruitingBasecamps.length > 0
                ? `모집 중인 베이스캠프 ${detail.recruitingBasecamps.length}개와 자세히 보기`
                : '자세히 보기'}
            </Link>
          </div>
        </>
      )}
    </section>
  );
}

function ratingText(detail: SpotDetail): string | null {
  const { average, count } = detail.rating;
  if (average === null) return null;
  return `평점 ${average.toFixed(1)} (후기 ${count})`;
}

// 판의 한 행이다. 왼쪽 라벨, 오른쪽 굵은 값, 값 아래에 출처·기준일을 단다.
function Row({ label, value, source, tone }: { label: string; value: ReactNode; source?: string | null; tone?: 'warning' }) {
  return (
    <div className="grid grid-cols-[5.5rem_minmax(0,1fr)] gap-3 border-t border-line px-5 py-3 text-sm first:border-t-0">
      <dt className="text-ink-muted">{label}</dt>
      <dd className="min-w-0">
        <span className={`block font-bold ${tone === 'warning' ? 'text-warning' : 'text-ink'}`}>{value}</span>
        {source && <span className="mt-0.5 block text-xs text-ink-muted">{source}</span>}
      </dd>
    </div>
  );
}

function ParkRow({ detail }: { detail: SpotDetail }) {
  const warning = detail.parkWarning;
  if (!warning.warned) return <Row label="공원 경계" value="겹치는 공원 경계가 없어요" />;
  const source = [warning.areaName, warning.source, warning.sourceDate && `${warning.sourceDate} 기준`]
    .filter(Boolean)
    .join(' · ');
  return <Row label="공원 경계" tone="warning" value="공원 경계 안일 수 있어요" source={source || null} />;
}

function OperationRow({ detail }: { detail: SpotDetail }) {
  const publicDetail = detail.publicDetail;
  if (publicDetail) {
    const source = [sourceLabel(publicDetail.source), publicDetail.sourceDate && `${publicDetail.sourceDate} 기준`]
      .filter(Boolean)
      .join(' · ');
    let value = '운영 중';
    if (publicDetail.closedNow) {
      value = publicDetail.closedUntil ? `${formatLocalDate(publicDetail.closedUntil)}까지 휴장` : '지금은 휴장 기간이에요';
    } else if (publicDetail.operatingStatus === null) {
      value = '운영 정보 없음';
    }
    return <Row label="운영" value={value} source={source} />;
  }
  if (detail.bakji) {
    return (
      <Row
        label="박지"
        value={`회원 제보 · 확인 ${detail.bakji.confirmationCount}회`}
        source={`제보한 회원 ${detail.bakji.reporter.nickname}`}
      />
    );
  }
  return null;
}

function WeatherRow({ detail }: { detail: SpotDetail }) {
  const weather = detail.weather;
  if (weather === null) return <Row label="날씨" value="날씨 정보를 불러오지 못했어요" />;
  const first = weather.shortTerm[0];
  const parts: string[] = [];
  if (first) {
    parts.push(formatKstTime(first.at));
    if (first.temperature !== null) parts.push(`${first.temperature}°C`);
    if (first.precipitationProbability !== null) parts.push(`강수확률 ${first.precipitationProbability}%`);
  }
  if (weather.sun) parts.push(`일몰 ${weather.sun.sunset}`);
  const source = weather.sun ? `${weather.source} · 한국천문연구원` : weather.source;
  return <Row label="날씨" value={<span className="font-mono tabular-nums">{parts.join(' · ') || '예보 없음'}</span>} source={source} />;
}

function PeopleRow({ detail }: { detail: SpotDetail }) {
  const [next] = detail.expectedPeople;
  if (!next) return <Row label="예상 인원" value="아직 예상 인원이 없어요" />;
  return (
    <Row
      label="예상 인원"
      value={<span className="tabular-nums">{`${formatLocalDate(next.date)} 밤 ${next.count}명`}</span>}
      source="확정 멤버·행사 참가자·혼자 가는 일정을 합친 인원"
    />
  );
}
