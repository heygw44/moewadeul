import { useEffect, useRef, useState, type ReactNode } from 'react';
import { listPrograms } from '../../api/programs';
import { fetchSpotsNearby } from '../../api/spots';
import type { NearbySpot, ProgramSummary } from '../../api/types';
import { Link } from '../../app/router';
import { Icon } from '../../components/icons';
import { createMapView } from '../../map/KakaoMap';
import type { RegionCandidate } from '../../map/regionSearch';
import type { RegionLabel } from '../../map/regionLabel';
import type { LatLng, MapView } from '../../map/types';
import { seatsText } from '../program/programLabels';
import { MapSearchBar } from '../spot/MapSearchBar';
import { SpotListRow } from '../spot/SpotListRow';
import { useRegionName } from '../spot/useRegionName';

// 첫 화면 안내판 지도의 처음 위치다. 경기 양평군 옥천면 일대로, 자연휴양림과 공공 야영장이 가까이 모여 있다.
const INITIAL_CENTER: LatLng = { lat: 37.555, lng: 127.47 };
const INITIAL_LEVEL = 8;
// 안내판에 번호를 붙여 보여 줄 장소 수와, 장소를 찾는 반경(km)이다.
const BOARD_SPOT_COUNT = 4;
const BOARD_RADIUS_KM = 20;
// 공원 경계 경고 예시를 찾는 반경(km)이다. 서버가 허용하는 최대값이다.
const WARNING_SEARCH_RADIUS_KM = 50;
// 경고 예시를 찾는 기준점이다. 강원 홍천·인제 사이로, 국립공원 경계 가까이 장소가 모여 있다.
const WARNING_SEARCH_CENTER: LatLng = { lat: 37.78, lng: 128.12 };

type BoardState = { center: LatLng; spots: NearbySpot[]; status: 'loading' | 'ready' | 'error' };

// 신뢰 단계마다 무엇을 뜻하는지와 그 단계에서 할 수 있는 일이다. 단계는 서버가 매번 계산하고, 여기서는 기준만 소개한다.
const TRUST_LEVELS = [
  { name: '이메일 인증', meaning: '이메일 인증을 마친 회원이에요.', can: '커뮤니티 글과 기록을 남겨요' },
  {
    name: '본인확인 · 성인',
    meaning: '본인확인을 마친 성인 회원이에요. 베이스캠프를 열거나 합류할 수 있어요.',
    can: '베이스캠프를 열어요',
  },
  {
    name: '신뢰 회원',
    meaning: '동행을 3번 이상 마쳤고, 함께 간 사람의 80% 이상이 다시 동행하고 싶다고 했어요.',
    can: '단계 2 조건의 캠프에 합류해요',
  },
];

// 처음 온 사람에게 뫼와들이 하는 일을 탐방로 안내판의 질서로 보여 주는 랜딩이다.
// 첫 화면은 제목·검색과 실제 지도 안내판(번호 핀과 같은 번호의 거리 행)이고, 그 아래로 공원 경계 근거, 신뢰 단계, 장소에서 동행·행사로 가는 흐름을 둔다.
// 안내판의 장소, 경고 예시, 행사는 모두 공개 API가 돌려준 실제 데이터다. 장소 상세는 날씨 때문에 기상청을 부르므로 랜딩에서 부르지 않는다.
export function LandingStage() {
  return (
    <>
      <LandingHero />
      <EvidenceSection />
      <TrustSection />
    </>
  );
}

function LandingHero() {
  const hostRef = useRef<HTMLDivElement>(null);
  const [view, setView] = useState<MapView | null>(null);
  const [mapFailed, setMapFailed] = useState(false);
  const [board, setBoard] = useState<BoardState>({ center: INITIAL_CENTER, spots: [], status: 'loading' });
  const regionName = useRegionName(view);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const element = document.createElement('div');
    element.className = 'absolute inset-0';
    host.appendChild(element);
    let cancelled = false;
    let created: MapView | null = null;
    // 안내판 지도는 그림처럼 고정한다. 끌거나 휠을 굴리면 페이지 스크롤이 지도에 먹히기 때문이다.
    createMapView(element, { center: INITIAL_CENTER, level: INITIAL_LEVEL, scrollWheel: false, draggable: false }).then(
      (next) => {
        if (cancelled) {
          next.destroy();
          return;
        }
        created = next;
        setView(next);
      },
      () => {
        if (!cancelled) setMapFailed(true);
      },
    );
    return () => {
      cancelled = true;
      created?.destroy();
      element.remove();
      setView(null);
    };
  }, []);

  // 안내판 기준점에서 가까운 장소를 받아 번호를 붙인다. 검색으로 기준점을 옮기면 다시 받는다.
  useEffect(() => {
    const controller = new AbortController();
    fetchSpotsNearby({ ...board.center, radiusKm: BOARD_RADIUS_KM, size: BOARD_SPOT_COUNT }, controller.signal).then(
      (page) => setBoard((previous) => ({ ...previous, spots: page.content, status: 'ready' })),
      () => {
        if (!controller.signal.aborted) setBoard((previous) => ({ ...previous, spots: [], status: 'error' }));
      },
    );
    return () => controller.abort();
  }, [board.center]);

  useEffect(() => {
    if (!view) return;
    view.showMarkers(
      board.spots.map((spot, index) => ({
        id: spot.spotId,
        kind: spot.type,
        name: spot.name,
        position: { lat: spot.lat, lng: spot.lng },
        warning: spot.parkWarning,
        closed: spot.closedNow,
        label: String(index + 1),
      })),
      [],
    );
  }, [view, board.spots]);

  function moveBoard(candidate: RegionCandidate) {
    view?.setCenter(candidate.position, Math.max(candidate.level, INITIAL_LEVEL));
    setBoard({ center: candidate.position, spots: [], status: 'loading' });
  }

  return (
    <section aria-labelledby="landing-title" className="mx-auto w-full max-w-screen-xl px-4">
      <div className="grid items-center gap-8 py-8 lg:grid-cols-[minmax(0,1fr)_40rem] lg:gap-16 lg:py-18">
        <div>
          <h1
            id="landing-title"
            className="text-[2.125rem] leading-[1.2] font-extrabold tracking-[-0.035em] text-ink lg:text-[3.75rem] lg:leading-[1.14]"
          >
            잘 수 있는 곳을 찾고
            <br />
            믿을 수 있는 사람과
            <br />
            함께 가요
          </h1>
          <p className="mt-4 max-w-[30em] text-base leading-relaxed text-ink-muted lg:mt-5 lg:text-[1.1875rem]">
            공공 야영장·자연휴양림·회원이 제보한 박지를 한 지도에서 봐요. 장소마다 공원 경계와 데이터 기준일이 붙어 있어요.
          </p>
          {view && (
            <div className="mt-6 flex max-w-[30rem] lg:mt-9">
              <MapSearchBar onSearch={() => {}} onSelect={moveBoard} mapMoving={false} floating={false} />
            </div>
          )}
          <Link
            to="/map"
            className="mt-4 inline-flex min-h-11 items-center gap-1 font-bold text-brand underline-offset-4 hover:underline"
          >
            {view ? '지도부터 볼게요' : '지도에서 박지 찾기'}
            <Icon name="chevronRight" size={16} />
          </Link>
          <p className="text-sm text-ink-muted">가입하지 않아도 지도와 장소 정보는 볼 수 있어요.</p>
        </div>

        <div className="overflow-hidden rounded-sheet bg-surface shadow-raise">
          <div className="flex items-baseline justify-between gap-3 bg-brand px-5 py-4 text-white">
            <p className="truncate text-lg font-extrabold tracking-[-0.02em]">{boardTitle(regionName)}</p>
            <p className="shrink-0 text-[0.8125rem] text-brand-ink">가까운 순</p>
          </div>
          <div
            ref={hostRef}
            role="img"
            aria-label="안내판 지도. 아래 목록과 같은 번호로 장소를 찍었어요."
            className={`relative h-60 bg-fill lg:h-[20.5rem] ${mapFailed ? 'hidden' : ''}`}
          />
          {board.spots.length > 0 ? (
            <ul aria-label="안내판의 장소" className="divide-y divide-line">
              {board.spots.slice(0, 3).map((spot, index) => (
                <SpotListRow key={spot.spotId} marker={spot} number={index + 1} distanceKm={spot.distanceKm} />
              ))}
            </ul>
          ) : (
            <p className="px-5 py-6 text-sm text-ink-muted">
              {board.status === 'loading'
                ? '가까운 장소를 찾고 있어요.'
                : board.status === 'error'
                  ? '장소를 불러오지 못했어요. 지도에서 찾아 주세요.'
                  : '이 근처에는 장소가 없어요. 다른 지역을 찾아 주세요.'}
            </p>
          )}
        </div>
      </div>
    </section>
  );
}

function boardTitle(label: RegionLabel | null): string {
  if (label?.kind === 'named') return `${label.name} 일대`;
  return '가까운 장소';
}

function EvidenceSection() {
  const [warnedSpot, setWarnedSpot] = useState<NearbySpot | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchSpotsNearby({ ...WARNING_SEARCH_CENTER, radiusKm: WARNING_SEARCH_RADIUS_KM, size: 50 }, controller.signal).then(
      (page) => setWarnedSpot(page.content.find((spot) => spot.parkWarning) ?? null),
      () => {},
    );
    return () => controller.abort();
  }, []);

  return (
    <section aria-labelledby="landing-evidence-title" className="border-t border-line bg-surface">
      <div className="mx-auto grid w-full max-w-screen-xl gap-7 px-4 py-14 lg:grid-cols-[25rem_minmax(0,1fr)] lg:gap-24 lg:py-24">
        <SectionLead
          id="landing-evidence-title"
          title={
            <>
              여기서 자도 되는지
              <br />
              근거부터 봐요
            </>
          }
        >
          박지를 등록하는 순간 공원 경계와 겹치는지 한 번 판정해요. 경고가 있는 박지에서는 베이스캠프를 열 수 없어요.
        </SectionLead>

        <div className="overflow-hidden rounded-sheet border border-line bg-surface">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 bg-warning-soft px-5 py-4">
            <span className="inline-flex items-center gap-2 text-lg font-extrabold text-warning">
              <Icon name="alert" size={20} className="shrink-0 text-warning-line" />
              공원 경계 안일 수 있어요
            </span>
            {warnedSpot && <span className="text-sm font-semibold text-warning">{warnedSpot.name}</span>}
          </div>
          <p className="px-5 pt-4 text-[0.9375rem] leading-relaxed text-ink">
            공원 경계는 참고 자료예요. 정확한 경계는 공식 고시 도면을 확인해 주세요. 자연공원 안에서는 지정된 야영장 밖의
            야영과 취사가 금지돼 있어요.
          </p>
          <dl className="mt-3">
            <div className="grid grid-cols-[6.5rem_minmax(0,1fr)] gap-3 border-t border-line px-5 py-3 text-sm">
              <dt className="text-ink-muted">경계 자료</dt>
              <dd className="font-bold text-ink">한국보호지역통합DB(KDPA)</dd>
            </div>
            <div className="grid grid-cols-[6.5rem_minmax(0,1fr)] gap-3 border-t border-line px-5 py-3 text-sm">
              <dt className="text-ink-muted">기준일</dt>
              <dd className="text-ink">장소 상세의 경고 안에 적어요</dd>
            </div>
          </dl>
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-4">
            <p className="inline-flex items-center gap-2 text-sm text-ink-muted">
              <Icon name="closed" size={18} className="shrink-0" />
              경고가 있는 박지에서는 베이스캠프를 열 수 없어요
            </p>
            <Link
              to={warnedSpot ? `/spots/${warnedSpot.spotId}` : '/map'}
              className="inline-flex min-h-11 items-center gap-1 text-sm font-bold text-brand underline-offset-4 hover:underline"
            >
              {warnedSpot ? '이 장소 자세히 보기' : '지도에서 찾아보기'}
              <Icon name="chevronRight" size={16} />
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}

function TrustSection() {
  const [program, setProgram] = useState<ProgramSummary | null>(null);

  // 신청 중인 행사가 없으면 곧 열릴 행사를 보여 준다.
  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    listPrograms('OPEN', 0, signal)
      .then((page) => page.content[0] ?? listPrograms('UPCOMING', 0, signal).then((next) => next.content[0] ?? null))
      .then(
        (found) => setProgram(found),
        () => {},
      );
    return () => controller.abort();
  }, []);

  return (
    <section aria-labelledby="landing-trust-title" className="border-t border-line">
      <div className="mx-auto w-full max-w-screen-xl px-4 py-14 lg:py-24">
        <div className="grid gap-7 lg:grid-cols-[25rem_minmax(0,1fr)] lg:gap-24">
          <SectionLead
            id="landing-trust-title"
            title={
              <>
                이 사람과 가도 되는지
                <br />
                단계로 알려 줘요
              </>
            }
          >
            신뢰 단계는 저장하지 않고 볼 때마다 다시 계산해요. 연령대와 성별 조건은 본인확인한 정보로만 따져요.
          </SectionLead>

          <ol className="overflow-hidden rounded-sheet border border-line bg-surface">
            {TRUST_LEVELS.map((level, index) => (
              <li
                key={level.name}
                className="grid grid-cols-[2.75rem_minmax(0,1fr)] items-center gap-x-4 gap-y-1 border-t border-line px-4 py-5 first:border-t-0 lg:grid-cols-[2.75rem_minmax(0,1fr)_13rem] lg:px-6"
              >
                <span
                  aria-hidden="true"
                  className={`flex size-11 items-center justify-center rounded-full font-mono text-lg font-extrabold ${
                    index === 0 ? 'bg-surface text-brand ring-2 ring-inset ring-brand' : 'bg-brand text-white'
                  }`}
                >
                  {index}
                </span>
                <div className="min-w-0">
                  <h3 className="text-lg font-extrabold tracking-[-0.015em] text-ink">
                    <span className="sr-only">신뢰 단계 {index}, </span>
                    {level.name}
                  </h3>
                  <p className="mt-0.5 text-sm text-ink-muted">{level.meaning}</p>
                </div>
                <p className="col-start-2 text-sm font-semibold text-brand lg:col-start-3 lg:text-right lg:text-ink">
                  {level.can}
                </p>
              </li>
            ))}
          </ol>
        </div>

        <ol aria-label="뫼와들에서 하는 일의 순서" className="mt-14 grid border-l-2 border-brand pl-0 lg:mt-20 lg:grid-cols-3 lg:border-t-2 lg:border-l-0">
          <TrailStop title="장소를 찾고" to="/map" linkText="지도 열기">
            지도에서 장소를 고르면 날씨·일몰·예상 인원이 함께 나와요.
          </TrailStop>
          <TrailStop title="베이스캠프에 합류하고" to="/basecamps" linkText="베이스캠프 보기">
            같은 날 같은 장소로 가는 2~6명 모임이에요. 확정된 멤버에게만 연락 수단이 열려요.
          </TrailStop>
          <TrailStop
            title="공식 행사에 신청해요"
            to={program ? `/programs/${program.programId}` : '/programs'}
            linkText={program ? '이 행사 보기' : '행사 보기'}
          >
            {program ? (
              <>
                {program.title} · 남은 자리 {seatsText(program.remainingSeats, program.capacity)}
              </>
            ) : (
              '지자체와 브랜드가 여는 백패킹 행사에 신청해요. 자리가 나면 알림으로 알려 줘요.'
            )}
          </TrailStop>
        </ol>
      </div>
    </section>
  );
}

function SectionLead({ id, title, children }: { id: string; title: ReactNode; children: ReactNode }) {
  return (
    <div>
      <h2 id={id} className="text-[1.75rem] leading-[1.2] font-extrabold tracking-[-0.03em] text-ink lg:text-[2.5rem]">
        {title}
      </h2>
      <p className="mt-4 text-base leading-relaxed text-ink-muted lg:text-[1.0625rem]">{children}</p>
    </div>
  );
}

// 흐름 줄의 한 정류장이다. 모바일은 왼쪽 세로 줄, 데스크톱은 위 가로 줄에 숲색 점을 찍는다.
function TrailStop({ title, to, linkText, children }: { title: string; to: string; linkText: string; children: ReactNode }) {
  return (
    <li className="relative pb-8 pl-6 last:pb-0 lg:pt-6 lg:pr-8 lg:pb-0 lg:pl-0">
      <span
        aria-hidden="true"
        className="absolute top-1 -left-[0.5625rem] size-3.5 rounded-full bg-brand ring-[3px] ring-canvas lg:-top-[0.5625rem] lg:left-0"
      />
      <h3 className="text-xl font-extrabold tracking-[-0.02em] text-ink">{title}</h3>
      <p className="mt-1.5 max-w-[22em] text-[0.9375rem] leading-relaxed text-ink-muted">{children}</p>
      <Link to={to} className="mt-3 inline-flex min-h-11 items-center gap-1 text-[0.9375rem] font-bold text-brand underline-offset-4 hover:underline">
        {linkText}
        <Icon name="chevronRight" size={16} />
      </Link>
    </li>
  );
}
