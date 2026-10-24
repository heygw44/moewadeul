import { useEffect, useEffectEvent, useState } from 'react';
import type { ReactNode } from 'react';
import { ApiError } from '../../api/client';
import { fetchSpotDetail } from '../../api/spots';
import type {
  BakjiDetail,
  ExpectedPeople,
  MidTermForecast,
  PublicDetail,
  SpotDetail,
  SpotRating,
} from '../../api/types';
import { Link } from '../../app/router';
import { Button } from '../../components/Button';
import { buttonClassName } from '../../components/buttonClass';
import { EmptyState } from '../../components/EmptyState';
import { Evidence } from '../../components/Evidence';
import { Icon } from '../../components/icons';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstMonthDay, formatKstTime, formatLocalDate } from '../../lib/datetime';
import { newMemoryPath } from '../memory/memoryLabels';
import { UnverifiedNote } from '../member/GatedButton';
import { SpotReviews } from '../review/SpotReviews';
import { SoloTripSection } from '../trip/SoloTripSection';
import { BakjiFeedback } from './BakjiFeedback';
import { BakjiOwnerActions } from './BakjiOwnerActions';
import { ParkWarningNotice } from './ParkWarningNotice';
import { FACILITY_LABELS, SIGNAL_LEVEL_LABELS, SPOT_TYPE_META, sourceLabel } from './spotLabels';
import { useSpotWeather } from './useSpotWeather';
import type { SpotWeatherState } from './useSpotWeather';

type SpotDetailPanelProps = {
  spotId: number;
  onBack: () => void;
  // 장소 정보를 불러오면 알린다. 지도 화면은 이 장소로 지도를 옮기고 핀을 그린다.
  onLoaded?: (detail: SpotDetail) => void;
  // 제보한 회원이 박지를 지우면 알린다. 지도 화면은 지운 박지가 사라지도록 마커를 다시 불러온다.
  onBakjiDeleted?: () => void;
};

// 어느 요청(장소, 시도 횟수)의 결과인지 함께 기억해서, 장소를 바꾸면 이전 결과를 곧바로 버린다.
type DetailResult =
  { spotId: number; attempt: number; detail: SpotDetail } | { spotId: number; attempt: number; error: unknown };

// 섹션은 선 없이 간격과 굵은 제목으로 나눈다.
const SECTION_CLASS = 'mt-8';
const SECTION_TITLE_CLASS = 'text-[1.1875rem] font-extrabold tracking-[-0.02em] text-ink';

// 단기 예보는 앞에서부터 이만큼만 보여 준다.
const SHORT_TERM_LIMIT = 4;

// 중기 예보는 앞에서부터 이만큼만 보여 준다.
const MID_TERM_LIMIT = 7;

// 기상청이 주지 않은 값은 글자 대신 줄표로 보여 준다.
function formatValue(value: number | null, unit: string): string {
  return value === null ? '-' : `${value}${unit}`;
}

export function SpotDetailPanel({ spotId, onBack, onLoaded, onBakjiDeleted }: SpotDetailPanelProps) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<DetailResult | null>(null);
  // 날씨는 상세와 같은 때에 따로 요청한다. 상세가 먼저 오면 근거부터 그리고, 날씨 칸만 날씨를 기다린다.
  const weather = useSpotWeather(spotId);

  useEffect(() => {
    const controller = new AbortController();
    fetchSpotDetail(spotId, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setResult({ spotId, attempt, detail });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ spotId, attempt, error });
      },
    );
    return () => controller.abort();
  }, [spotId, attempt]);

  const current = result && result.spotId === spotId && result.attempt === attempt ? result : null;
  const showSkeleton = useDelayedFlag(current === null);
  const loadedDetail = current && 'detail' in current ? current.detail : null;
  const loadedName = loadedDetail?.name ?? null;

  const reportLoaded = useEffectEvent((detail: SpotDetail) => onLoaded?.(detail));
  useEffect(() => {
    if (loadedDetail !== null) reportLoaded(loadedDetail);
  }, [loadedDetail]);

  useEffect(() => {
    if (loadedName === null) return;
    document.title = `${loadedName} · 뫼와들`;
    return () => {
      document.title = '뫼와들';
    };
  }, [loadedName]);

  let body: ReactNode;
  if (current === null) {
    body = showSkeleton ? <DetailSkeleton /> : null;
  } else if ('detail' in current) {
    body = <DetailContent detail={current.detail} weather={weather} onBakjiDeleted={onBakjiDeleted} />;
  } else if (current.error instanceof ApiError && current.error.code === 'NOT_FOUND') {
    body = <EmptyState title="장소를 찾을 수 없어요" description="삭제됐거나 숨겨진 장소예요." />;
  } else {
    body = (
      <div className="flex flex-col gap-3">
        <ErrorNotice title="장소 정보를 불러오지 못했어요" error={current.error} />
        <div>
          <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
            다시 불러오기
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col">
      <div className="sticky top-0 z-10 flex items-center bg-surface px-2 lg:px-4">
        <button
          type="button"
          onClick={onBack}
          className="inline-flex min-h-11 items-center gap-1 rounded-control pr-3 pl-1 text-sm font-semibold text-ink hover:bg-fill"
        >
          <Icon name="chevronLeft" size={22} />
          목록
        </button>
      </div>
      <div aria-busy={current === null} className="px-4 pt-2 pb-8 lg:px-6">
        {body}
      </div>
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="flex flex-col gap-3">
      <Skeleton className="h-7 w-2/3" />
      <Skeleton className="h-5 w-1/3" />
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-16 w-full" />
    </div>
  );
}

function DetailContent({
  detail,
  weather,
  onBakjiDeleted,
}: {
  detail: SpotDetail;
  weather: SpotWeatherState;
  onBakjiDeleted?: () => void;
}) {
  const meta = SPOT_TYPE_META[detail.type];
  const publicDetail = detail.publicDetail;
  const bakji = detail.bakji;
  const warning = detail.parkWarning;
  // 확인 버튼을 누르면 서버가 준 새 횟수로 바꿔 보여 준다. 상세를 다시 불러오지 않는다.
  const [confirmationCount, setConfirmationCount] = useState(bakji?.confirmationCount ?? 0);
  // 개인 일정을 등록하면 예상 인원만 새로 받아 바꾼다. 상세 전체를 다시 그리면 쓰던 화면 위치를 잃기 때문이다.
  const [expectedPeople, setExpectedPeople] = useState(detail.expectedPeople);

  function refreshExpectedPeople() {
    fetchSpotDetail(detail.spotId).then(
      (fresh) => setExpectedPeople(fresh.expectedPeople),
      // 다시 받지 못해도 등록은 끝났으므로, 예상 인원은 다음에 상세를 열 때 바뀐다.
      () => undefined,
    );
  }

  return (
    <article>
      {/* 휴장과 공원 경계 경고는 바로 아래 안내 상자가 기간·근거와 함께 알리므로, 이름 아래에는 장소 유형과 주소만 둔다. */}
      <header className="flex flex-col gap-1.5">
        <h2 className="text-[1.75rem] leading-tight font-extrabold tracking-[-0.03em] text-ink lg:text-[1.875rem]">
          {detail.name}
        </h2>
        <p className="flex flex-wrap items-center gap-x-1.5 text-sm text-ink-muted">
          <Icon name={meta.icon} size={16} className="shrink-0" />
          {[meta.label, detail.address].filter(Boolean).join(' · ')}
        </p>
      </header>

      <div className="mt-3">
        <UnverifiedNote action="박지를 확인하고 혼자 가는 일정과 후기를 남길 수 있어요." />
      </div>

      {(publicDetail?.closedNow || warning.warned) && (
        <div className="mt-4 flex flex-col gap-3">
          {publicDetail?.closedNow && <ClosedNotice publicDetail={publicDetail} />}
          <ParkWarningNotice warning={warning} />
        </div>
      )}

      <SummaryTiles rating={detail.rating} expectedPeople={expectedPeople} />

      {bakji && (
        <BakjiSection bakji={bakji} confirmationCount={confirmationCount}>
          <BakjiOwnerActions spotId={detail.spotId} reporterId={bakji.reporter.memberId} onDeleted={onBakjiDeleted} />
          <BakjiFeedback spotId={detail.spotId} onConfirmed={setConfirmationCount} />
        </BakjiSection>
      )}
      {publicDetail && <PublicSection publicDetail={publicDetail} />}

      <ExpectedPeopleSection expectedPeople={expectedPeople} />
      <SoloTripSection spotId={detail.spotId} onRegistered={refreshExpectedPeople} />

      <section className={SECTION_CLASS} aria-labelledby="spot-basecamp-title">
        <h3 id="spot-basecamp-title" className={SECTION_TITLE_CLASS}>
          베이스캠프
        </h3>
        <div className="mt-2">
          {warning.warned ? (
            <Button
              variant="secondary"
              disabled
              disabledReason="공원 경계 경고가 있는 박지에서는 베이스캠프를 열 수 없어요."
            >
              이 장소에서 베이스캠프 열기
            </Button>
          ) : (
            <Link to={`/basecamps/new?spotId=${detail.spotId}`} className={buttonClassName('secondary')}>
              이 장소에서 베이스캠프 열기
            </Link>
          )}
        </div>
      </section>

      <section className={SECTION_CLASS} aria-labelledby="spot-community-title">
        <h3 id="spot-community-title" className={SECTION_TITLE_CLASS}>
          커뮤니티
        </h3>
        <div className="mt-2 flex flex-wrap gap-2">
          <Link to={`/community?spotId=${detail.spotId}`} className={buttonClassName('secondary')}>
            이 장소 글 보기
          </Link>
          <Link to={`/community/new?spotId=${detail.spotId}`} className={buttonClassName('secondary')}>
            이 장소로 글쓰기
          </Link>
        </div>
      </section>

      <section className={SECTION_CLASS} aria-labelledby="spot-memory-title">
        <h3 id="spot-memory-title" className={SECTION_TITLE_CLASS}>
          다녀온 기록
        </h3>
        <p className="mt-1 text-sm text-ink-muted">다녀온 날의 사진과 글을 남겨 두세요. 처음에는 나만 봐요.</p>
        <div className="mt-2">
          <Link
            to={newMemoryPath({ lat: detail.lat, lng: detail.lng }, detail.spotId)}
            className={buttonClassName('secondary')}
          >
            기록 남기기
          </Link>
        </div>
      </section>

      <SpotReviews key={detail.spotId} spotId={detail.spotId} initialRating={detail.rating} />

      <WeatherSection weather={weather} />
    </article>
  );
}

// 평점·후기 수·가장 가까운 밤의 예상 인원을 세이지 칸 셋으로 먼저 보여 준다. 자세한 값은 아래 섹션에 있다.
function SummaryTiles({ rating, expectedPeople }: { rating: SpotRating; expectedPeople: ExpectedPeople[] }) {
  const next = expectedPeople[0];
  const tiles = [
    { label: '평점', value: rating.average === null ? '-' : rating.average.toFixed(1) },
    { label: '후기', value: `${rating.count}개` },
    { label: next ? `${formatLocalDate(next.date)} 밤` : '예상 인원', value: next ? `${next.count}명` : '-' },
  ];
  return (
    <dl className="mt-5 grid grid-cols-3 gap-2">
      {tiles.map((tile) => (
        <div key={tile.label} className="flex min-w-0 flex-col gap-1 rounded-sheet bg-fill px-4 py-3.5">
          <dt className="truncate text-[0.8125rem] text-ink-muted">{tile.label}</dt>
          <dd className="text-xl font-extrabold tracking-[-0.02em] tabular-nums text-ink">{tile.value}</dd>
        </div>
      ))}
    </dl>
  );
}

function ClosedNotice({ publicDetail }: { publicDetail: PublicDetail }) {
  const { closedFrom, closedUntil } = publicDetail;
  let period: string | null = null;
  if (closedFrom !== null && closedUntil !== null) {
    period = `${formatLocalDate(closedFrom)} ~ ${formatLocalDate(closedUntil)}`;
  } else if (closedFrom !== null) {
    period = `${formatLocalDate(closedFrom)}부터`;
  } else if (closedUntil !== null) {
    period = `${formatLocalDate(closedUntil)}까지`;
  }

  // 원천 데이터가 늦게 바뀔 수 있어서 서버는 휴장 장소를 숨기지 않는다. 그래서 확인을 권하는 문장을 함께 둔다.
  return (
    <Notice tone="info" title="지금은 휴장 기간이에요">
      <div className="flex flex-col gap-2">
        {period && <p className="tabular-nums">{period}</p>}
        <p>운영 정보는 공공데이터 기준이라 실제와 다를 수 있어요. 방문 전에 야영장에 확인해 주세요.</p>
      </div>
    </Notice>
  );
}

function BakjiSection({
  bakji,
  confirmationCount,
  children,
}: {
  bakji: BakjiDetail;
  confirmationCount: number;
  children: ReactNode;
}) {
  return (
    <section className={SECTION_CLASS} aria-labelledby="spot-bakji-title">
      <h3 id="spot-bakji-title" className={SECTION_TITLE_CLASS}>
        박지 정보
      </h3>
      {bakji.notice && <p className="mt-2 text-sm text-ink-muted">{bakji.notice}</p>}
      {bakji.description && <p className="mt-2 whitespace-pre-line text-base text-ink">{bakji.description}</p>}
      <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 rounded-sheet bg-fill p-4 text-sm">
        <dt className="text-ink-muted">식수</dt>
        <dd className="text-ink">{bakji.hasWater ? '있음' : '없음'}</dd>
        <dt className="text-ink-muted">화장실</dt>
        <dd className="text-ink">{bakji.hasToilet ? '있음' : '없음'}</dd>
        {bakji.signalLevel !== null && (
          <>
            <dt className="text-ink-muted">통신</dt>
            <dd className="text-ink">{SIGNAL_LEVEL_LABELS[bakji.signalLevel]}</dd>
          </>
        )}
        <dt className="text-ink-muted">확인 횟수</dt>
        <dd className="tabular-nums text-ink">{confirmationCount}회</dd>
        <dt className="text-ink-muted">제보한 회원</dt>
        <dd className="text-ink">{bakji.reporter.nickname}</dd>
      </dl>
      {children}
    </section>
  );
}

// 링크로 열 수 있는 https 주소만 링크로 만든다. 원천 값에 scheme이 없거나 http 같은 다른 scheme이면 글자로만 보여 준다.
function parseHomepage(homepage: string): URL | null {
  try {
    const url = new URL(homepage);
    return url.protocol === 'https:' ? url : null;
  } catch {
    return null;
  }
}

function PublicSection({ publicDetail }: { publicDetail: PublicDetail }) {
  const { category, facilities, phone, homepage, source, sourceDate, checkedAt } = publicDetail;
  const facilityRows = facilities
    ? FACILITY_LABELS.flatMap(([key, label]) => {
        const value = facilities[key];
        return value === null ? [] : [{ key, label, value }];
      })
    : [];
  const homepageUrl = homepage ? parseHomepage(homepage) : null;

  return (
    <section className={SECTION_CLASS} aria-labelledby="spot-public-title">
      <h3 id="spot-public-title" className={SECTION_TITLE_CLASS}>
        시설 정보
      </h3>
      {category !== null && <p className="mt-2 text-base text-ink">{category}</p>}
      {facilityRows.length > 0 && (
        <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-3">
          {facilityRows.map((row) => (
            <div key={row.key} className="flex min-w-0 flex-col">
              <dt className="text-sm text-ink-muted">{row.label}</dt>
              <dd className="text-sm break-words text-ink">{row.value}</dd>
            </div>
          ))}
        </dl>
      )}
      {(phone || homepage) && (
        <div className="mt-3 flex flex-col">
          {phone && (
            <a
              href={`tel:${phone.replace(/[^\d+]/g, '')}`}
              className="inline-flex min-h-11 items-center self-start text-accent underline underline-offset-2"
            >
              전화 {phone}
            </a>
          )}
          {homepage &&
            (homepageUrl ? (
              <a
                href={homepageUrl.href}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex min-h-11 items-center self-start text-accent underline underline-offset-2"
              >
                홈페이지 {homepageUrl.hostname}
              </a>
            ) : (
              <p className="py-2 text-sm text-ink">홈페이지 {homepage}</p>
            ))}
        </div>
      )}
      <div className="mt-3">
        <Evidence
          items={[
            { label: '출처', value: sourceLabel(source) },
            ...(sourceDate ? [{ label: '정보 기준일', value: sourceDate }] : []),
            ...(checkedAt ? [{ label: '마지막 확인', value: formatKstMonthDay(checkedAt) }] : []),
          ]}
        />
      </div>
    </section>
  );
}

// 서버는 오늘 이후이면서 인원이 있는 날짜만 날짜 순으로 준다. 그래서 화면은 받은 날짜를 자르지 않고 모두 보여 준다.
function ExpectedPeopleSection({ expectedPeople }: { expectedPeople: ExpectedPeople[] }) {
  return (
    <section className={SECTION_CLASS} aria-labelledby="spot-expected-people-title">
      <h3 id="spot-expected-people-title" className={SECTION_TITLE_CLASS}>
        예상 인원
      </h3>
      {expectedPeople.length === 0 ? (
        <p className="mt-2 text-sm text-ink-muted">아직 예상 인원이 없어요</p>
      ) : (
        <div className="mt-2 flex flex-col gap-2">
          <p className="text-sm text-ink-muted">
            확정된 베이스캠프 멤버, 행사 참가자, 혼자 가는 일정을 합쳐 밤마다 센 인원이에요.
          </p>
          <Evidence
            items={expectedPeople.map((day) => ({ label: formatLocalDate(day.date), value: `${day.count}명` }))}
          />
        </div>
      )}
    </section>
  );
}

function WeatherSection({ weather: state }: { weather: SpotWeatherState }) {
  const showSkeleton = useDelayedFlag(state.status === 'loading');
  const weather = state.status === 'loaded' ? state.weather : null;
  return (
    <section className={SECTION_CLASS} aria-labelledby="spot-weather-title" aria-busy={state.status === 'loading'}>
      <h3 id="spot-weather-title" className={SECTION_TITLE_CLASS}>
        날씨
      </h3>
      {state.status === 'loading' ? (
        showSkeleton && (
          <div className="mt-2 flex flex-col gap-2">
            <Skeleton className="h-5 w-1/3" />
            <Skeleton className="h-24 w-full" />
          </div>
        )
      ) : weather === null ? (
        <p className="mt-2 text-sm text-ink-muted">날씨 정보를 불러오지 못했어요</p>
      ) : (
        <div className="mt-2 flex flex-col gap-3">
          <p className="text-sm text-ink-muted">출처 {weather.source}</p>
          {weather.shortTerm.length > 0 && (
            <div className="rounded-sheet bg-fill px-4 py-2">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="text-ink-muted">
                    <th scope="col" className="py-1 font-normal">
                      시각
                    </th>
                    <th scope="col" className="py-1 text-right font-normal">
                      기온
                    </th>
                    <th scope="col" className="py-1 text-right font-normal">
                      강수확률
                    </th>
                    <th scope="col" className="py-1 text-right font-normal">
                      바람
                    </th>
                  </tr>
                </thead>
                <tbody className="font-mono tabular-nums text-ink">
                  {weather.shortTerm.slice(0, SHORT_TERM_LIMIT).map((forecast) => (
                    <tr key={forecast.at}>
                      <td className="py-2">{formatKstTime(forecast.at)}</td>
                      <td className="py-2 text-right">{formatValue(forecast.temperature, '°C')}</td>
                      <td className="py-2 text-right">{formatValue(forecast.precipitationProbability, '%')}</td>
                      <td className="py-2 text-right">{formatValue(forecast.windSpeed, 'm/s')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {weather.midTerm.length > 0 && <MidTermTable forecasts={weather.midTerm.slice(0, MID_TERM_LIMIT)} />}
          {weather.sun ? (
            <Evidence
              items={[
                { label: '일출', value: weather.sun.sunrise },
                { label: '일몰', value: weather.sun.sunset },
                { label: '어두워지는 시각', value: weather.sun.civilTwilightEnd },
              ]}
            />
          ) : (
            <p className="text-sm text-ink-muted">일출·일몰 정보를 불러오지 못했어요</p>
          )}
        </div>
      )}
    </section>
  );
}

function MidTermTable({ forecasts }: { forecasts: MidTermForecast[] }) {
  return (
    <div className="rounded-sheet bg-fill px-4 py-2">
      <table className="w-full text-left text-sm">
        <caption className="pb-1 text-left text-ink-muted">중기 예보</caption>
        <thead>
          <tr className="text-ink-muted">
            <th scope="col" className="py-1 font-normal">
              날짜
            </th>
            <th scope="col" className="py-1 text-right font-normal">
              최저/최고
            </th>
            <th scope="col" className="py-1 text-right font-normal">
              오전
            </th>
            <th scope="col" className="py-1 text-right font-normal">
              오후
            </th>
          </tr>
        </thead>
        <tbody className="tabular-nums text-ink">
          {forecasts.map((forecast) => (
            <tr key={forecast.date}>
              <td className="py-2">{formatLocalDate(forecast.date)}</td>
              <td className="py-2 text-right font-mono">
                {formatValue(forecast.minTemperature, '°')}/{formatValue(forecast.maxTemperature, '°')}
              </td>
              <td className="py-2 text-right">{forecast.amSky ?? '-'}</td>
              <td className="py-2 text-right">{forecast.pmSky ?? '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
