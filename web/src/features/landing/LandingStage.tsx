import { useEffect, useRef, useState, type ReactNode } from 'react';
import { listPrograms } from '../../api/programs';
import { searchBasecamps } from '../../api/basecamps';
import { fetchSpotsNearby } from '../../api/spots';
import type { BasecampSearchItem, NearbySpot, ProgramSummary } from '../../api/types';
import { Link, navigate } from '../../app/router';
import { Evidence } from '../../components/Evidence';
import { Icon } from '../../components/icons';
import { StatusText } from '../../components/StatusText';
import { formatKstDateTime, formatLocalDate } from '../../lib/datetime';
import { createMapView } from '../../map/KakaoMap';
import type { RegionCandidate } from '../../map/regionSearch';
import type { LatLng, MapView, SpotMarkerData } from '../../map/types';
import { headcountText, joinConditionSummary } from '../basecamp/basecampLabels';
import { trustLevelBadge } from '../member/memberLabels';
import { seatsText } from '../program/programLabels';
import { MapSearchBar } from '../spot/MapSearchBar';

// 첫 장면 지도의 위치다. 강원 홍천·인제 사이로, 공공 야영장과 자연휴양림이 고르게 모여 있어서 점이 묶이지 않고 보인다.
const INITIAL_CENTER: LatLng = { lat: 37.78, lng: 128.12 };
const INITIAL_LEVEL = 9;
// 마지막 장면은 남한 전체를 보여 준다.
const KOREA_CENTER: LatLng = { lat: 36.3, lng: 127.8 };
const KOREA_LEVEL = 12;
// 모집 중인 베이스캠프를 찾는 범위다. 남한 전체를 덮는 사각형이다.
const KOREA_BOUNDS = { swLat: 33, swLng: 124.5, neLat: 38.7, neLng: 131 };
// 경고 장소를 찾는 반경(km)이다. 서버가 허용하는 최대값이다.
const WARNING_SEARCH_RADIUS_KM = 50;
// 모바일에서 장소를 화면 세로 폭의 이만큼 위로 올려 패널에 가리지 않게 한다.
const MOBILE_CENTER_SHIFT = 0.25;

type MapState = 'loading' | 'ready' | 'failed';

// 장면마다 지도가 옮겨 갈 곳이다. 데이터가 없으면 첫 장면 위치에 머문다.
type SceneTarget = { center: LatLng; level: number; marker: SpotMarkerData | null };

// 랜딩은 화면을 채운 실제 지도 하나가 무대다. 지도는 머리글 아래에 고정되고, 방문자가 스크롤하면 장면 문장이 지도 위를 지나간다.
// 장면이 바뀔 때마다 지도가 그 장면의 장소로 움직이고, 그 장소 하나만 점으로 찍는다. 장소와 베이스캠프, 행사는 모두 공개 API가 돌려준 실제 데이터다.
// 영역 안의 장소를 모두 찍지 않는 이유는 장소가 많은 곳에서 묶음 숫자가 지도를 덮어 장면의 장소가 묻히기 때문이다.
// 지도를 불러오지 못하면(키 없음, SDK 실패) 지도 자리를 옅은 면으로 두고 문장만 같은 순서로 보여 준다.
export function LandingStage() {
  const hostRef = useRef<HTMLDivElement>(null);
  const scenesRef = useRef<HTMLOListElement>(null);
  const [view, setView] = useState<MapView | null>(null);
  const [mapState, setMapState] = useState<MapState>('loading');
  const [moving, setMoving] = useState(false);
  const [activeScene, setActiveScene] = useState(0);
  const [warnedSpot, setWarnedSpot] = useState<NearbySpot | null>(null);
  const [basecamp, setBasecamp] = useState<BasecampSearchItem | null>(null);
  const [program, setProgram] = useState<ProgramSummary | null>(null);

  // 지도는 시도할 때마다 새 요소에 만든다. 개발 모드에서 효과가 두 번 실행돼도 한 요소에 지도가 겹쳐 생기지 않게 하기 위해서다.
  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const element = document.createElement('div');
    element.className = 'absolute inset-0';
    host.appendChild(element);

    // 모바일에서 고정한 지도를 끌 수 있으면 손가락 스크롤이 지도에 먹혀 다음 장면으로 넘어갈 수 없다.
    const draggable = window.matchMedia('(min-width: 64rem)').matches;
    let cancelled = false;
    let created: MapView | null = null;
    createMapView(element, {
      center: INITIAL_CENTER,
      level: INITIAL_LEVEL,
      scrollWheel: false,
      draggable,
      markerStyle: 'dot',
    }).then(
      (next) => {
        if (cancelled) {
          next.destroy();
          return;
        }
        created = next;
        setView(next);
        setMapState('ready');
      },
      () => {
        if (!cancelled) setMapState('failed');
      },
    );
    return () => {
      cancelled = true;
      created?.destroy();
      element.remove();
      setView(null);
    };
  }, []);

  // 장면 데이터는 처음 한 번만 받는다. 하나가 실패해도 그 장면은 데이터 없이 설명만 보여 준다.
  // 장소 상세는 날씨 때문에 기상청을 부르므로 여기서 부르지 않는다. 방문할 때마다 부르면 하루 호출 한도를 방문자 수만큼 쓰게 된다.
  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    fetchSpotsNearby({ ...INITIAL_CENTER, radiusKm: WARNING_SEARCH_RADIUS_KM, size: 50 }, signal).then(
      (page) => setWarnedSpot(page.content.find((spot) => spot.parkWarning) ?? null),
      () => {},
    );
    searchBasecamps({ ...KOREA_BOUNDS, size: 1 }, signal).then(
      (page) => setBasecamp(page.content[0] ?? null),
      () => {},
    );
    // 신청 중인 행사가 없으면 곧 열릴 행사를 보여 준다.
    listPrograms('OPEN', 0, signal)
      .then((page) => page.content[0] ?? listPrograms('UPCOMING', 0, signal).then((next) => next.content[0] ?? null))
      .then(
        (found) => setProgram(found),
        () => {},
      );
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!view) return;
    const offMarker = view.onMarkerClick((id) => navigate(`/spots/${id}`));
    const offMoveStart = view.onMoveStart(() => setMoving(true));
    const offIdle = view.onIdle(() => setMoving(false));
    return () => {
      offMarker();
      offMoveStart();
      offIdle();
    };
  }, [view]);

  // 화면 세로 가운데 줄을 지나는 장면을 지금 장면으로 본다.
  useEffect(() => {
    const list = scenesRef.current;
    if (!list) return;
    const scenes = [...list.children];
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const index = scenes.indexOf(entry.target);
          if (index >= 0) setActiveScene(index);
        }
      },
      { rootMargin: '-50% 0px -50% 0px' },
    );
    for (const scene of scenes) observer.observe(scene);
    return () => observer.disconnect();
  }, []);

  // 지금 장면의 장소로 지도를 옮긴다. 움직임 줄이기 설정이면 미끄러지지 않고 바로 옮긴다.
  useEffect(() => {
    if (!view) return;
    const target = sceneTarget(activeScene, warnedSpot, basecamp);
    const center = target.marker ? visibleCenter(view, target.marker.position, target.level) : target.center;
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduceMotion) view.setCenter(center, target.level);
    else view.panTo(center, target.level);
    view.showMarkers(target.marker ? [target.marker] : [], []);
    view.setSelected(target.marker?.id ?? null);
  }, [view, activeScene, warnedSpot, basecamp]);

  function moveToRegion(candidate: RegionCandidate) {
    view?.setCenter(candidate.position, candidate.level);
  }

  return (
    <section aria-label="뫼와들 소개" className="relative">
      {/* 지도는 머리글(52px) 바로 아래에 붙어 화면 끝까지 채운다. 장면 문장은 이 위를 지나간다. */}
      <div className="sticky top-13 h-[calc(100dvh-3.25rem)] overflow-hidden bg-fill">
        <div ref={hostRef} className="absolute inset-0" />
      </div>

      <ol ref={scenesRef} className="pointer-events-none relative z-10 -mt-[calc(100dvh-3.25rem)]">
        <Scene>
          <h1 className="font-serif text-5xl font-bold tracking-[-0.01em] text-ink lg:text-[4rem] lg:leading-none">뫼와들</h1>
          <p className="mt-4 text-xl leading-snug font-medium text-ink lg:text-[1.375rem]">
            잘 수 있는 곳을 찾고
            <br />
            믿을 수 있는 사람과 함께 가요.
          </p>
          {mapState === 'ready' && (
            <div className="mt-6 flex">
              <MapSearchBar onSearch={() => {}} onSelect={moveToRegion} mapMoving={moving} floating={false} />
            </div>
          )}
          <SceneLink to="/map">{mapState === 'ready' ? '지도에서 크게 보기' : '지도에서 박지 찾기'}</SceneLink>
          <p className="mt-1 text-sm text-ink-muted">가입하지 않아도 지도와 장소 정보는 볼 수 있어요.</p>
        </Scene>

        <Scene>
          <SceneTitle>
            여기서 자도
            <br />
            될까요?
          </SceneTitle>
          {warnedSpot && (
            <div className="mt-5 flex flex-col gap-1">
              <p className="text-lg font-semibold text-ink">{warnedSpot.name}</p>
              <StatusText tone="warning" icon="alert">
                공원 경계 안일 수 있어요
              </StatusText>
            </div>
          )}
          <p className="mt-4 text-base leading-relaxed text-ink">
            국립공원 같은 자연공원 안으로 예상되는 장소는 경고해요. 경고가 있는 박지에서는 베이스캠프를 열 수 없어요.
          </p>
          <p className="mt-2 text-sm text-ink-muted">
            공원 경계는 법적 효력이 없는 참고 자료예요. 경계 자료의 출처와 기준일은 장소 상세에서 보여 줘요.
          </p>
          {warnedSpot ? (
            <SceneLink to={`/spots/${warnedSpot.spotId}`}>이 장소 자세히 보기</SceneLink>
          ) : (
            <SceneLink to="/map">지도에서 찾아보기</SceneLink>
          )}
        </Scene>

        <Scene>
          <SceneTitle>
            함께 갈 사람은
            <br />
            기록으로 골라요
          </SceneTitle>
          {basecamp && (
            <div className="mt-5">
              <p className="text-lg font-semibold text-ink">{basecamp.title}</p>
              <p className="text-sm text-ink-muted">{basecamp.spot.name}</p>
              <div className="mt-3">
                <Evidence
                  items={[
                    { label: '출발', value: formatLocalDate(basecamp.startDate) },
                    { label: '인원', value: headcountText(basecamp.headcount, basecamp.capacity) },
                    { label: '합류 조건', value: joinConditionSummary(basecamp.joinCondition) },
                  ]}
                />
              </div>
            </div>
          )}
          <dl className="mt-5 flex flex-col gap-2">
            {TRUST_LEVEL_MEANINGS.map((meaning, level) => {
              const badge = trustLevelBadge(level);
              return (
                <div key={badge.label} className="flex flex-col gap-0.5">
                  <dt>
                    <StatusText tone={badge.tone} icon={badge.icon}>
                      {badge.label}
                    </StatusText>
                  </dt>
                  <dd className="text-sm leading-relaxed text-ink">{meaning}</dd>
                </div>
              );
            })}
          </dl>
          {basecamp ? (
            <SceneLink to={`/basecamps/${basecamp.basecampId}`}>이 베이스캠프 보기</SceneLink>
          ) : (
            <SceneLink to="/basecamps">베이스캠프 찾기</SceneLink>
          )}
        </Scene>

        <Scene>
          <SceneTitle>
            혼자 가기 어렵다면
            <br />
            공식 행사로 가요
          </SceneTitle>
          {program && (
            <div className="mt-5">
              <p className="text-lg font-semibold text-ink">{program.title}</p>
              <p className="text-sm text-ink-muted">{program.locationText}</p>
              <div className="mt-3">
                <Evidence
                  items={[
                    { label: '신청 시작', value: formatKstDateTime(program.applyOpenAt) },
                    { label: '남은 자리', value: seatsText(program.remainingSeats, program.capacity) },
                  ]}
                />
              </div>
            </div>
          )}
          <p className="mt-4 text-base leading-relaxed text-ink">
            관리자가 여는 백패킹 행사에 선착순으로 신청해요. 자리가 나면 알림으로 알려 줘요.
          </p>
          {program ? (
            <SceneLink to={`/programs/${program.programId}`}>이 행사 보기</SceneLink>
          ) : (
            <SceneLink to="/programs">공식 행사 보기</SceneLink>
          )}
        </Scene>
      </ol>
    </section>
  );
}

// 신뢰 단계마다 무엇을 뜻하는지 한 줄로 적는다. 단계는 서버가 매번 계산하고, 여기서는 기준만 소개한다.
const TRUST_LEVEL_MEANINGS = [
  '이메일 인증을 마친 회원이에요.',
  '본인확인을 마친 성인 회원이에요. 베이스캠프를 열거나 합류할 수 있어요.',
  '동행을 3번 이상 마쳤고, 함께 간 사람의 80% 이상이 다시 동행하고 싶다고 했어요.',
];

// 모바일에서는 문장 패널이 화면 아래 절반을 덮는다. 그래서 장소가 패널 위 빈 지도에 오도록 지도 가운데를 아래로 내린다.
// 목표 확대 수준의 화면 세로 폭은 지금 폭에서 한 단계마다 두 배로 바뀐다고 보고 어림한다.
function visibleCenter(view: MapView, position: LatLng, level: number): LatLng {
  if (window.matchMedia('(min-width: 64rem)').matches) return position;
  const { sw, ne } = view.getBounds();
  const span = (ne.lat - sw.lat) * 2 ** (level - view.getLevel());
  return { lat: position.lat - span * MOBILE_CENTER_SHIFT, lng: position.lng };
}

function sceneTarget(scene: number, warnedSpot: NearbySpot | null, basecamp: BasecampSearchItem | null): SceneTarget {
  const initial = { center: INITIAL_CENTER, level: INITIAL_LEVEL, marker: null };
  if (scene === 1 && warnedSpot) {
    const position = { lat: warnedSpot.lat, lng: warnedSpot.lng };
    return {
      center: position,
      level: 7,
      marker: { id: warnedSpot.spotId, kind: warnedSpot.type, name: warnedSpot.name, position, warning: true, closed: warnedSpot.closedNow },
    };
  }
  if (scene === 2 && basecamp) {
    const { spot } = basecamp;
    const position = { lat: spot.lat, lng: spot.lng };
    return {
      center: position,
      level: 8,
      marker: { id: spot.spotId, kind: spot.type, name: spot.name, position, warning: false, closed: false },
    };
  }
  if (scene === 3) return { center: KOREA_CENTER, level: KOREA_LEVEL, marker: null };
  return initial;
}

// 장면 하나는 머리글 아래 화면 높이만큼 차지하고, 문장 패널을 왼쪽 아래에 둔다. 패널 밖은 눌러도 지도가 받는다.
function Scene({ children }: { children: ReactNode }) {
  return (
    <li className="flex min-h-[calc(100dvh-3.25rem)] items-end">
      <div className="mx-auto w-full max-w-screen-xl px-4 pb-6 lg:pb-16">
        <div className="pointer-events-auto w-full max-w-[28rem] rounded-sheet bg-canvas p-6 shadow-raise lg:p-8">{children}</div>
      </div>
    </li>
  );
}

function SceneTitle({ children }: { children: ReactNode }) {
  return (
    <h2 className="font-serif text-3xl leading-tight font-bold tracking-[-0.01em] text-ink lg:text-[2.5rem]">{children}</h2>
  );
}

function SceneLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="mt-4 inline-flex min-h-11 items-center gap-1 self-start font-semibold text-accent hover:underline">
      {children}
      <Icon name="chevronRight" size={18} />
    </Link>
  );
}
