import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from '../../app/router';
import { buttonClassName } from '../../components/buttonClass';
import { Evidence } from '../../components/Evidence';
import { Icon } from '../../components/icons';
import { StatusText } from '../../components/StatusText';
import { createMapView } from '../../map/KakaoMap';
import type { MapView } from '../../map/types';
import type { RegionCandidate } from '../../map/regionSearch';
import { fetchSpotDetail } from '../../api/spots';
import type { SpotDetail, SpotMarker } from '../../api/types';
import { MapSearchBar } from '../spot/MapSearchBar';
import { SPOT_TYPE_META } from '../spot/spotLabels';
import { useSpotsInView } from '../spot/useSpotsInView';
import { HeroPreview } from './LandingPreviews';

// 첫 화면 지도의 처음 위치다. 강원 홍천·인제 사이로, 공공 야영장과 자연휴양림이 고르게 모여 있어서 핀이 묶이지 않고 보인다.
const INITIAL_CENTER = { lat: 37.78, lng: 128.12 };
const INITIAL_LEVEL = 9;

// 묶음을 누르면 이만큼 확대한다. 지도 화면과 같은 값이다.
const CLUSTER_ZOOM_STEP = 2;

type MapState = 'loading' | 'ready' | 'failed';

// 랜딩 첫 화면이다. 왼쪽에 서비스 이름·한 줄 소개·지역 검색을, 오른쪽에 실제 지도와 고른 장소의 근거 시트를 둔다.
// 지도와 핀은 지도 화면과 같은 래퍼와 영역 조회를 쓰므로, 방문자는 예시가 아니라 지금 뫼와들에 있는 장소를 본다.
// 지도를 불러오지 못하면(키 없음, SDK 실패) 같은 자리에 예시 화면을 대신 둔다.
export function LandingHero() {
  const hostRef = useRef<HTMLDivElement>(null);
  const [view, setView] = useState<MapView | null>(null);
  const [mapState, setMapState] = useState<MapState>('loading');
  const [moving, setMoving] = useState(false);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  // 방문자가 직접 고른 장소의 상세다. 처음 열 때 자동으로 고른 장소는 불러오지 않는다.
  // 상세 조회는 날씨 때문에 기상청을 부르므로, 방문할 때마다 부르면 하루 호출 한도를 방문자 수만큼 쓰게 된다.
  const [detail, setDetail] = useState<SpotDetail | null>(null);
  const area = useSpotsInView(view);

  // 지도는 시도할 때마다 새 요소에 만든다. 개발 모드에서 효과가 두 번 실행돼도 한 요소에 지도가 겹쳐 생기지 않게 하기 위해서다.
  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const element = document.createElement('div');
    element.className = 'absolute inset-0';
    host.appendChild(element);

    let cancelled = false;
    let created: MapView | null = null;
    createMapView(element, { center: INITIAL_CENTER, level: INITIAL_LEVEL, scrollWheel: false, markerStyle: 'dot' }).then(
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

  useEffect(() => {
    if (!view) return;
    const offMarker = view.onMarkerClick((id) => setSelectedId(id));
    const offCluster = view.onClusterClick((cluster) =>
      view.setCenter(cluster.position, Math.max(1, view.getLevel() - CLUSTER_ZOOM_STEP)),
    );
    const offMoveStart = view.onMoveStart(() => setMoving(true));
    const offIdle = view.onIdle(() => setMoving(false));
    return () => {
      offMarker();
      offCluster();
      offMoveStart();
      offIdle();
    };
  }, [view]);

  // 고른 장소가 지금 영역에 없으면 지도 가운데에서 가장 가까운 장소를 대신 고른다. 처음 열었을 때도 시트가 비어 있지 않게 하기 위해서다.
  // 공원 경계 경고가 있는 장소가 영역 안에 있으면 그중 가장 가까운 곳을 먼저 고른다. 이 서비스가 보여 주는 근거가 바로 드러나기 때문이다.
  const selected = useMemo(() => {
    const found = area.markers.find((marker) => marker.spotId === selectedId);
    if (found || !view || area.markers.length === 0) return found ?? null;
    const center = view.getCenter();
    const warned = area.markers.filter((marker) => marker.parkWarning);
    const candidates = warned.length > 0 ? warned : area.markers;
    return candidates.reduce((best, marker) => (distance(marker, center) < distance(best, center) ? marker : best));
  }, [area.markers, selectedId, view]);

  // 방문자가 고른 장소만 상세를 불러온다. 다른 장소를 고르면 앞선 요청을 취소한다.
  useEffect(() => {
    if (selectedId === null) return;
    const controller = new AbortController();
    fetchSpotDetail(selectedId, controller.signal).then(
      (next) => setDetail(next),
      () => {},
    );
    return () => controller.abort();
  }, [selectedId]);
  const selectedDetail = detail && selected && detail.spotId === selected.spotId ? detail : null;

  useEffect(() => {
    view?.setSelected(selected?.spotId ?? null);
  }, [view, selected, area.markers]);

  const spotCount = area.markers.length + area.clusters.reduce((sum, cluster) => sum + cluster.count, 0);

  function moveToRegion(candidate: RegionCandidate) {
    setSelectedId(null);
    setDetail(null);
    view?.setCenter(candidate.position, candidate.level);
  }

  // 왼쪽 글은 머리글과 같은 가장자리(가운데 80rem 열 + 1rem)에서 시작하고, 지도는 화면 오른쪽 끝까지 나간다.
  // 패딩의 %는 부모 폭 기준이라 스크롤 막대 자리를 뺀 실제 폭으로 계산된다.
  return (
    <section className="grid items-center gap-8 px-4 pt-8 pb-12 lg:grid-cols-[minmax(0,26rem)_minmax(0,1fr)] lg:gap-16 lg:pt-16 lg:pb-24 lg:pr-0 xl:pl-[max(1rem,calc((100%-80rem)/2+1rem))]">
      <div className="flex flex-col">
        <h1 className="text-5xl font-bold tracking-tight text-ink lg:text-[4rem] lg:leading-none">뫼와들</h1>
        <p className="mt-5 text-[1.375rem] leading-snug font-medium text-ink">
          잘 수 있는 곳을 찾고
          <br />
          믿을 수 있는 사람과 함께 가요.
        </p>
        <p className="mt-4 hidden text-[0.9375rem] leading-relaxed text-ink-muted lg:block">
          {INTRO}
        </p>
        {mapState === 'ready' && (
          <div className="mt-8 flex">
            <MapSearchBar onSearch={() => setSelectedId(null)} onSelect={moveToRegion} mapMoving={moving} floating={false} />
          </div>
        )}
        <Link
          to="/map"
          className="mt-4 inline-flex min-h-11 items-center gap-1 self-start font-semibold text-accent hover:underline"
        >
          {mapState === 'ready' ? '지도에서 크게 보기' : '지도에서 박지 찾기'}
          <Icon name="chevronRight" size={18} />
        </Link>
        <p className="mt-1 hidden text-sm text-ink-muted lg:block">{NO_SIGNUP_NOTE}</p>
      </div>

      {mapState === 'failed' ? (
        <HeroPreview />
      ) : (
        // 모바일은 지도를 화면 폭으로 펼치고 시트를 아래에 붙인다. 위쪽 지도가 시트에 가리지 않게 하기 위해서다.
        // 데스크톱은 지도를 오른쪽 화면 끝까지 붙여 제품 화면을 크게 보여 준다.
        <div className="relative -mx-4 h-[34rem] overflow-hidden bg-fill lg:mx-0 lg:h-[40rem] lg:rounded-l-sheet">
          <div ref={hostRef} className="absolute inset-0" />
          {selected && <SpotSheet spot={selected} detail={selectedDetail} />}
          {mapState === 'ready' && area.status !== 'loading' && (
            <p
              aria-live="polite"
              className="pointer-events-none absolute top-3 left-3 z-10 rounded-control bg-surface px-3 py-2 text-sm text-ink ring-1 ring-line lg:top-auto lg:bottom-4 lg:left-4"
            >
              이 지역의 장소 <span className="font-mono">{spotCount.toLocaleString('ko-KR')}</span>곳
            </p>
          )}
        </div>
      )}

      <div className="flex flex-col gap-1 lg:hidden">
        <p className="text-[0.9375rem] leading-relaxed text-ink-muted">{INTRO}</p>
        <p className="text-sm text-ink-muted">{NO_SIGNUP_NOTE}</p>
      </div>
    </section>
  );
}

// 지도에서 고른 장소의 근거 시트다. 처음에는 영역 조회 응답에 있는 값(유형, 공원 경계 경고, 휴장)만 적는다.
// 방문자가 직접 고른 장소는 상세를 불러와 주소와, 경고가 있으면 경계 자료의 출처·기준일을 더한다. 응답에 없는 값은 지어내지 않는다.
function SpotSheet({ spot, detail }: { spot: SpotMarker; detail: SpotDetail | null }) {
  const meta = SPOT_TYPE_META[spot.type];
  const warning = detail?.parkWarning;
  return (
    <aside
      aria-label="고른 장소"
      className="absolute inset-x-3 bottom-3 z-10 flex flex-col rounded-sheet bg-surface p-4 shadow-raise sm:inset-x-auto sm:right-4 sm:w-80 lg:top-4 lg:bottom-auto"
    >
      <h2 className="truncate text-lg font-semibold tracking-tight text-ink">{spot.name}</h2>
      <p className="mt-0.5 truncate text-sm text-ink-muted">
        {detail?.address ? `${meta.label} · ${detail.address}` : meta.label}
      </p>
      <div className="mt-3">
        <Evidence
          items={[
            {
              label: '공원 경계',
              value: spot.parkWarning ? (
                <StatusText tone="warning" icon="alert">
                  안쪽일 수 있어요
                </StatusText>
              ) : (
                '경고 없음'
              ),
            },
            ...(warning?.warned && warning.source ? [{ label: '출처', value: warning.source }] : []),
            ...(warning?.warned && warning.sourceDate ? [{ label: '기준일', value: warning.sourceDate }] : []),
            {
              label: '운영',
              value: spot.closedNow ? (
                <StatusText tone="closed" icon="closed">
                  휴장 중
                </StatusText>
              ) : (
                '휴장 아님'
              ),
            },
          ]}
        />
      </div>
      <Link to={`/spots/${spot.spotId}`} className={`${buttonClassName('primary')} mt-3 w-full`}>
        이 장소 자세히 보기
      </Link>
    </aside>
  );
}

const INTRO = '공원 경계 경고는 출처와 기준일까지 보여 줘요. 함께 갈 사람은 본인확인 여부와 동행 후기로 골라요.';
const NO_SIGNUP_NOTE = '가입하지 않아도 지도와 장소 정보는 볼 수 있어요.';

// 화면에서 가까운 장소를 고를 때만 쓰므로 위도·경도 차이의 제곱합으로 충분하다.
function distance(marker: SpotMarker, center: { lat: number; lng: number }): number {
  return (marker.lat - center.lat) ** 2 + (marker.lng - center.lng) ** 2;
}
