import { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import type { NearbySpot } from '../../api/types';
import { BottomTabBar } from '../../app/layout/BottomTabBar';
import { SiteHeader } from '../../app/layout/SiteHeader';
import { Link, navigate } from '../../app/router';
import { BrandMark } from '../../components/BrandMark';
import { ActiveFilterChips } from '../../components/ActiveFilterChips';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { Sheet } from '../../components/Sheet';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { createMapView } from '../../map/KakaoMap';
import { MapLoadError } from '../../map/kakaoLoader';
import type { MapLoadErrorReason } from '../../map/kakaoLoader';
import type { RegionCandidate } from '../../map/regionSearch';
import type { RegionLabel } from '../../map/regionLabel';
import type { LatLng, MapView } from '../../map/types';
import { BasecampDetailPanel } from '../basecamp/BasecampDetailPanel';
import { BasecampFilterDialog } from '../basecamp/BasecampFilterDialog';
import { BasecampListPanel } from '../basecamp/BasecampListPanel';
import { BasecampOpenPanel } from '../basecamp/BasecampOpenPanel';
import { MapOpenMenu, SpotPickDialog } from '../basecamp/MapOpenMenu';
import { useBasecampsInView } from '../basecamp/useBasecampsInView';
import { NO_BASECAMP_FILTERS, basecampFilterChips } from '../basecamp/basecampFilters';
import type { BasecampFilters } from '../basecamp/basecampFilters';
import type { BasecampsInView } from '../basecamp/useBasecampsInView';
import { withNext } from '../member/nextPath';
import { NotificationBellLink } from '../notification/NotificationLink';
import { useSession } from '../member/session';
import { BakjiReportPanel } from './BakjiReportPanel';
import { MapLegend } from './MapLegend';
import { MapRegionChip } from './MapRegionChip';
import { MapSafetyNotice } from './MapSafetyNotice';
import { MapSearchBar } from './MapSearchBar';
import { SpotDetailPanel } from './SpotDetailPanel';
import { SpotFilterDialog } from './SpotFilterDialog';
import { NO_SPOT_FILTERS, spotFilterChips } from './spotFilters';
import type { SpotFilters } from './spotFilters';
import { SpotListRow } from './SpotListRow';
import { useMapMoving } from './useMapMoving';
import { useRegionName } from './useRegionName';
import { useSpotsInView } from './useSpotsInView';
import type { SpotsInView } from './useSpotsInView';

type SheetSnap = 'peek' | 'half' | 'full';

type MapFailure = { reason: MapLoadErrorReason; message: string };

// 지도를 우클릭하거나 길게 누른 곳에 띄운 메뉴다. host는 지도 래퍼가 만든 메뉴 칸 요소다.
type ContextMenu = { position: LatLng; host: HTMLElement };

// 처음에는 전국이 한 화면에 들어오도록 국토 가운데쯤을 중심으로 크게 축소해서 연다.
const INITIAL_VIEW = { center: { lat: 36.35, lng: 127.85 }, level: 12 };

// 제보 화면을 좌표와 함께 열었는데 그 좌표가 화면 밖이면, 이 확대 수준까지 확대해서 옮긴다.
const DRAFT_FOCUS_LEVEL = 5;

// 묶음을 누르면 이만큼 확대해서 안의 장소가 풀려 보이게 한다.
const CLUSTER_ZOOM_STEP = 2;

const CHIP_CLASS =
  'pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-control border border-contour bg-card px-3';

type MapPageProps = {
  spotId?: number;
  // basecampId는 mode가 basecamps일 때 베이스캠프 상세를 연다.
  basecampId?: number;
  // basecampOpen에서 베이스캠프를 열 장소다.
  openSpotId?: number;
  // report는 지도를 눌러 박지 위치를 찍는 제보 화면이고, basecamps는 베이스캠프 목록과 상세,
  // basecampOpen은 장소에서 베이스캠프를 여는 화면이다.
  mode?: 'browse' | 'report' | 'basecamps' | 'basecampOpen';
  // report에서 처음 찍어 둘 박지 위치다. 지도 메뉴에서 근처에 장소가 없는 곳을 골랐을 때 넘어온다.
  initialDraft?: LatLng;
  // report에서 제보가 끝난 뒤 갈 곳이다. basecamp면 새 박지에서 베이스캠프 열기 폼을 연다.
  nextAfterReport?: 'basecamp';
};

export function MapPage({
  spotId,
  basecampId,
  openSpotId,
  mode = 'browse',
  initialDraft,
  nextAfterReport,
}: MapPageProps) {
  const reporting = mode === 'report';
  const basecampMode = mode === 'basecamps';
  const opening = mode === 'basecampOpen';
  // 시트에 목록이 아니라 상세·폼이 열려 있는지다.
  const contentOpen = spotId !== undefined || reporting || basecampId !== undefined || opening;
  const session = useSession();
  const hostRef = useRef<HTMLDivElement>(null);
  const [map, setMap] = useState<MapView | null>(null);
  const [mapFailure, setMapFailure] = useState<MapFailure | null>(null);
  const [mapAttempt, setMapAttempt] = useState(0);
  const [terrainOn, setTerrainOn] = useState(false);
  const [snap, setSnap] = useState<SheetSnap>(contentOpen ? 'half' : 'peek');
  const [snapOpen, setSnapOpen] = useState(contentOpen);
  const [draft, setDraft] = useState<LatLng | null>(null);
  const [searchPin, setSearchPin] = useState<LatLng | null>(null);
  // 검색으로 지도를 옮기는 확대·축소도 움직임 시작으로 온다. 그래서 옮기는 중에는 목표 확대 수준을 기억해 두고,
  // 그 수준에서 지도가 멈춘 뒤부터 움직임이 시작되면 핀을 지운다. 옮기는 중이 아니면 null이다.
  const searchMoveLevelRef = useRef<number | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [contextMenu, setContextMenu] = useState<ContextMenu | null>(null);
  // 지도 메뉴로 찾은 근처 장소다. 값이 있으면 그중 베이스캠프를 열 곳을 고르는 다이얼로그를 띄운다.
  const [pickSpots, setPickSpots] = useState<NearbySpot[] | null>(null);
  // 넘겨받은 처음 위치를 이미 찍었는지 기억한다. 같은 주소에서 다시 그려도 사용자가 옮긴 위치를 덮어쓰지 않기 위해서다.
  const [appliedDraftKey, setAppliedDraftKey] = useState<string | null>(null);
  const [filters, setFilters] = useState<BasecampFilters>(NO_BASECAMP_FILTERS);
  const [spotFilters, setSpotFilters] = useState<SpotFilters>(NO_SPOT_FILTERS);
  const [filterDialogOpen, setFilterDialogOpen] = useState(false);
  // 지도에서 장소 마커를 눌러 고른 장소다. 베이스캠프 목록을 그 장소로 거른다.
  const [spotFilterId, setSpotFilterId] = useState<number | null>(null);
  // 장소 조건은 지도를 둘러보는 화면에서만 쓴다. 박지를 제보하거나 베이스캠프를 여는 중에는 근처 장소를 빠짐없이 보여 준다.
  const browsing = mode === 'browse';
  const spotFilterChipList = browsing ? spotFilterChips(spotFilters, setSpotFilters) : [];
  const basecampFilterChipList = basecampMode ? basecampFilterChips(filters, setFilters) : [];
  // 조건 버튼과 칩 줄은 장소를 둘러볼 때와 베이스캠프를 찾을 때만 보이고, 화면마다 자기 조건을 보여 준다.
  const filterChipList = basecampMode ? basecampFilterChipList : spotFilterChipList;
  const resetFilters = basecampMode ? () => setFilters(NO_BASECAMP_FILTERS) : () => setSpotFilters(NO_SPOT_FILTERS);
  // 베이스캠프를 찾는 동안에는 지도의 마커를 베이스캠프가 맡고, 장소 조회는 쉰다.
  const area = useSpotsInView(map, !basecampMode, browsing ? spotFilters : NO_SPOT_FILTERS);
  const basecamps = useBasecampsInView(
    map,
    basecampMode && session.status !== 'loading',
    filters,
    session.me?.memberId ?? null,
  );

  const regionName = useRegionName(map);
  const overlayRef = useRef<HTMLDivElement>(null);
  // 검색 칸에 입력하던 중에 지도를 건드려도 쓰던 검색어가 사라지지 않게, 오버레이 안에 포커스가 있으면 숨기지 않는다.
  const keepOverlay = useCallback(() => overlayRef.current?.contains(document.activeElement) ?? false, []);
  const mapMoving = useMapMoving(map, keepOverlay);

  // 제보 화면을 벗어나면 이전 제보의 완료 상태를 버린다. 그래야 다시 들어왔을 때 지도를 눌러 위치를 찍을 수 있다.
  if (!reporting && submitted) setSubmitted(false);

  // 제보 화면을 좌표와 함께 열면 그 자리에 임시 핀을 찍어 둔다. 제보 화면을 벗어나면 다음에 다시 찍도록 잊는다.
  const initialDraftKey = reporting && initialDraft ? `${initialDraft.lat},${initialDraft.lng}` : null;
  if (initialDraftKey !== appliedDraftKey) {
    setAppliedDraftKey(initialDraftKey);
    if (initialDraft && initialDraftKey !== null) setDraft(initialDraft);
  }

  // 상세나 폼이 열리면 내용이 보이도록 시트를 절반으로 열고, 목록으로 돌아오면 접는다.
  // 렌더 중에 이전 값과 비교해서 바꾸므로, 화면이 한 번 그려진 뒤 다시 그리는 일이 없다.
  if (snapOpen !== contentOpen) {
    setSnapOpen(contentOpen);
    setSnap(contentOpen ? 'half' : 'peek');
  }

  // 지도는 시도할 때마다 새 요소에 만든다. 개발 모드에서 효과가 두 번 실행돼도 지도가 한 요소에 겹쳐 생기지 않게 하기 위해서다.
  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const element = document.createElement('div');
    element.className = 'absolute inset-0';
    host.appendChild(element);

    let cancelled = false;
    let created: MapView | null = null;
    createMapView(element, INITIAL_VIEW).then(
      (view) => {
        if (cancelled) {
          view.destroy();
          return;
        }
        created = view;
        setMap(view);
      },
      (error: unknown) => {
        if (cancelled) return;
        const failure = error instanceof MapLoadError ? error : new MapLoadError('LOAD_FAILED');
        setMapFailure({ reason: failure.reason, message: failure.message });
      },
    );

    return () => {
      cancelled = true;
      created?.destroy();
      element.remove();
      setMap(null);
    };
  }, [mapAttempt]);

  // 지도 요소의 크기가 바뀌면 지도에 알린다. 모바일·데스크톱 배치가 바뀔 때와, 위의 알림 띠를 닫아 지도 칸이 커질 때다.
  useEffect(() => {
    const host = hostRef.current;
    if (!map || !host) return;
    const observer = new ResizeObserver(() => map.relayout());
    observer.observe(host);
    return () => observer.disconnect();
  }, [map]);

  useEffect(() => {
    if (!map) return;
    // 제보 중에는 핀을 눌러도 화면을 옮기지 않는다. 위치를 찍다가 실수로 상세로 나가지 않게 하기 위해서다.
    const offMarker = map.onMarkerClick((id) => {
      if (reporting) return;
      if (basecampMode) {
        // 베이스캠프 화면에서는 마커가 장소의 베이스캠프 목록을 거르는 스위치다. 같은 마커를 다시 누르면 푼다.
        setSpotFilterId((previous) => (previous === id ? null : id));
        if (basecampId !== undefined) navigate('/basecamps');
        return;
      }
      navigate(`/spots/${id}`);
    });
    const offCluster = map.onClusterClick((cluster) => {
      map.setCenter(cluster.position, Math.max(1, map.getLevel() - CLUSTER_ZOOM_STEP));
    });
    return () => {
      offMarker();
      offCluster();
    };
  }, [map, reporting, basecampMode, basecampId]);

  // 제보 화면에서만 지도를 눌러 박지 위치를 찍는다. 화면을 벗어나면 임시 핀을 지우고 찍은 위치도 버린다.
  useEffect(() => {
    if (!map || !reporting || submitted) return;
    const offClick = map.onMapClick(setDraft);
    return () => {
      offClick();
      map.showDraft(null);
      setDraft(null);
    };
  }, [map, reporting, submitted]);

  useEffect(() => {
    map?.showDraft(draft);
  }, [map, draft]);

  // 넘겨받은 처음 위치가 지금 화면 밖이면 그 자리로 옮긴다. 주소로 바로 들어오면 지도가 전국 축척으로 열리기 때문이다.
  const initialLat = reporting ? initialDraft?.lat : undefined;
  const initialLng = reporting ? initialDraft?.lng : undefined;
  useEffect(() => {
    if (!map || initialLat === undefined || initialLng === undefined) return;
    const { sw, ne } = map.getBounds();
    const inside = initialLat >= sw.lat && initialLat <= ne.lat && initialLng >= sw.lng && initialLng <= ne.lng;
    if (!inside) map.setCenter({ lat: initialLat, lng: initialLng }, Math.min(map.getLevel(), DRAFT_FOCUS_LEVEL));
  }, [map, initialLat, initialLng]);

  // 지도를 둘러보거나 베이스캠프를 찾는 화면에서만 우클릭·길게 누르기 메뉴를 띄운다.
  // 제보나 베이스캠프 열기 중에는 쓰던 폼을 지키려고 띄우지 않는다.
  const contextMenuEnabled = browsing || basecampMode;
  const closeContextMenu = useCallback(() => {
    map?.showMenu(null);
    setContextMenu(null);
  }, [map]);

  // 지도를 움직이거나 빈 곳을 누르면 메뉴를 닫는다. 다른 장소·베이스캠프로 화면이 바뀌어도 닫는다.
  useEffect(() => {
    if (!map || !contextMenuEnabled) return;
    const offContextMenu = map.onContextMenu((position) => {
      const host = map.showMenu(position);
      setContextMenu(host ? { position, host } : null);
    });
    const offMoveStart = map.onMoveStart(closeContextMenu);
    const offClick = map.onMapClick(closeContextMenu);
    return () => {
      offContextMenu();
      offMoveStart();
      offClick();
      closeContextMenu();
    };
  }, [map, contextMenuEnabled, closeContextMenu, spotId, basecampId]);

  useEffect(() => {
    map?.showSearchPin(searchPin);
  }, [map, searchPin]);

  // 사용자가 지도를 끌거나 확대·축소하면 검색 위치 핀을 지운다.
  useEffect(() => {
    if (!map) return;
    const offIdle = map.onIdle(() => {
      if (searchMoveLevelRef.current === map.getLevel()) searchMoveLevelRef.current = null;
    });
    const offMoveStart = map.onMoveStart(() => {
      if (searchMoveLevelRef.current === null) setSearchPin(null);
    });
    return () => {
      offIdle();
      offMoveStart();
    };
  }, [map]);

  useEffect(() => {
    map?.setSelected(basecampMode ? spotFilterId : opening ? (openSpotId ?? null) : (spotId ?? null));
  }, [map, spotId, basecampMode, spotFilterId, opening, openSpotId]);

  useEffect(() => {
    map?.setTerrain(terrainOn);
  }, [map, terrainOn]);

  // 상세 화면에서는 장소 이름을 불러온 뒤 상세 패널이 제목을 바꾼다.
  useEffect(() => {
    if (spotId !== undefined || basecampId !== undefined) return;
    if (reporting) document.title = '박지 제보 · 뫼와들';
    else if (opening) document.title = '베이스캠프 열기 · 뫼와들';
    else if (basecampMode) document.title = '베이스캠프 · 뫼와들';
    else document.title = '지도 · 뫼와들';
  }, [spotId, basecampId, reporting, opening, basecampMode]);

  let sheetLabel = '장소 목록';
  if (reporting) sheetLabel = '박지 제보';
  else if (opening) sheetLabel = '베이스캠프 열기';
  else if (basecampId !== undefined) sheetLabel = '베이스캠프 상세';
  else if (basecampMode) sheetLabel = '베이스캠프 목록';
  else if (spotId !== undefined) sheetLabel = '장소 상세';

  // 검색한 지역으로 지도를 옮긴다. 장소·베이스캠프 상세를 보고 있었다면 그 지역 목록이 보이도록 목록으로 돌아간다.
  // 제보나 베이스캠프 열기 중에는 쓰던 폼을 지키려고 화면을 바꾸지 않는다.
  // 번지까지 있는 주소로 옮기면 그 지점에 핀을 두고, 아니면 앞서 둔 핀을 지운다.
  function moveToRegion(candidate: RegionCandidate) {
    if (!map) return;
    searchMoveLevelRef.current = candidate.level;
    map.setCenter(candidate.position, candidate.level);
    // 애니메이션 없이 옮기면 이벤트가 호출 안에서 모두 끝난다. 같은 자리로 다시 옮기면 idle이 오지 않으므로 여기서 끝낸다.
    if (map.getLevel() === candidate.level) searchMoveLevelRef.current = null;
    setSearchPin(candidate.pinned ? candidate.position : null);
    if (basecampId !== undefined) navigate('/basecamps');
    else if (mode === 'browse' && spotId !== undefined) navigate('/map');
  }

  function retryMap() {
    setMapFailure(null);
    setMapAttempt((value) => value + 1);
  }

  return (
    <div className="flex h-dvh w-full flex-col overflow-hidden bg-paper">
      <SiteHeader className="hidden lg:block" />
      <div className="relative min-h-0 flex-1 lg:flex">
        <Sheet
          aboveBottomNav={!contentOpen}
          snap={snap}
          onSnapChange={setSnap}
          label={sheetLabel}
          header={
            contentOpen ? undefined : basecampMode ? (
              <BasecampListHeader
                result={basecamps}
                regionName={regionName}
              />
            ) : (
              <ListHeader area={area} regionName={regionName} />
            )
          }
        >
          {opening && openSpotId !== undefined ? (
            <BasecampOpenPanel key={openSpotId} spotId={openSpotId} onBack={() => navigate(`/spots/${openSpotId}`)} />
          ) : basecampId !== undefined ? (
            <BasecampDetailPanel key={basecampId} basecampId={basecampId} onBack={() => navigate('/basecamps')} />
          ) : basecampMode ? (
            <BasecampListPanel
              result={basecamps}
              filtered={basecampFilterChipList.length > 0}
              spotFilterId={spotFilterId}
              onClearSpotFilter={() => setSpotFilterId(null)}
              waitingForMap={map === null && mapFailure === null}
            />
          ) : reporting ? (
            <BakjiReportPanel
              draft={draft}
              nextBasecamp={nextAfterReport === 'basecamp'}
              onCancel={() => navigate('/map')}
              onSubmitted={() => {
                // 새 박지가 지도에 보이도록 마커를 다시 불러오고, 등록이 끝났으니 임시 핀은 치운다.
                setSubmitted(true);
                setDraft(null);
                area.retry();
              }}
            />
          ) : spotId === undefined ? (
            <SpotList
              area={area}
              waitingForMap={map === null && mapFailure === null}
              filtered={spotFilterChipList.length > 0}
            />
          ) : (
            <SpotDetailPanel key={spotId} spotId={spotId} onBack={() => navigate('/map')} />
          )}
        </Sheet>

        {/* z-0으로 쌓임 맥락을 따로 만들어서, 지도와 위에 뜬 버튼이 모바일 시트보다 아래에 그려지게 한다. */}
        <div className="absolute inset-0 z-0 lg:relative lg:flex-1">
          <div ref={hostRef} role="region" aria-label="지도" className="absolute inset-0" />
          {!mapFailure && (
            <>
              {/* 지도를 끌거나 확대·축소하는 동안에만 지도 가운데를 십자로 표시한다. 지역 이름을 짓는 가운데 점이 어디인지 보여 주기 위해서다. */}
              <svg
                aria-hidden="true"
                width="20"
                height="20"
                viewBox="0 0 20 20"
                className={`pointer-events-none absolute left-1/2 top-1/2 z-10 -translate-x-1/2 -translate-y-1/2 stroke-ink-subtle transition-opacity duration-150 motion-reduce:transition-none ${
                  mapMoving.moving ? 'opacity-100' : 'opacity-0'
                }`}
              >
                <path d="M10 0v20M0 10h20" strokeWidth="1.5" />
              </svg>
              <MapLegend />
              <MapRegionChip label={regionName} />
            </>
          )}

          {mapFailure && (
            <div className="absolute inset-0 flex items-center justify-center bg-paper p-4">
              <div className="w-full max-w-sm">
                <Notice tone="danger" title="지도를 불러오지 못했어요">
                  <div className="flex flex-col gap-3">
                    <p>{mapFailure.message}</p>
                    {mapFailure.reason === 'NO_KEY' ? (
                      <p>저장소 루트의 .env에 VITE_KAKAO_JS_KEY를 넣고 개발 서버를 다시 켜 주세요.</p>
                    ) : (
                      <div>
                        <Button variant="secondary" onClick={retryMap}>
                          다시 시도
                        </Button>
                      </div>
                    )}
                  </div>
                </Notice>
              </div>
            </div>
          )}

          {/* 지도를 끌거나 확대·축소하는 동안에는 지도를 가리지 않게 위로 살짝 밀며 감추고, 멈추면 다시 보여 준다. */}
          <div
            ref={overlayRef}
            inert={mapMoving.hidden}
            className={`pointer-events-none absolute inset-x-0 top-0 z-10 flex flex-col gap-2 px-4 pt-3 transition duration-200 motion-reduce:translate-y-0 motion-reduce:transition-none ${
              mapMoving.hidden ? '-translate-y-3 opacity-0' : ''
            }`}
          >
            <h1 className="sr-only">뫼와들 지도</h1>
            <div className="flex items-start justify-between gap-2">
              {/* 데스크톱은 위의 머리글이 서비스 이름과 메뉴를 보여 주므로, 지도 위에는 모바일에서만 이름을 띄운다. */}
              <Link to="/" className={`${CHIP_CLASS} text-ink lg:invisible`}>
                <BrandMark className="text-lg" />
              </Link>
              <div className="flex flex-wrap justify-end gap-2">
                <NotificationBellLink className="pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-control border border-contour bg-card px-3 text-ink hover:bg-paper-deep lg:hidden" />
                {map && (
                  <>
                    {!reporting && !basecampMode && !opening && <ReportButton />}
                    <button
                      type="button"
                      aria-pressed={terrainOn}
                      onClick={() => setTerrainOn((value) => !value)}
                      className={
                        terrainOn
                          ? 'pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-control border border-forest bg-forest-soft px-3 text-base font-semibold text-forest-deep'
                          : 'pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-control border border-contour bg-card px-3 text-base font-semibold text-ink hover:bg-paper-deep'
                      }
                    >
                      <Icon name="terrain" size={20} />
                      지형
                    </button>
                  </>
                )}
              </div>
            </div>
            {map && (
              <div className="flex w-full items-start gap-2 lg:max-w-md">
                <MapSearchBar onSearch={() => setSearchPin(null)} onSelect={moveToRegion} mapMoving={mapMoving.moving} />
                {(browsing || basecampMode) && (
                  <FilterButton count={filterChipList.length} onClick={() => setFilterDialogOpen(true)} />
                )}
              </div>
            )}
            <ActiveFilterChips chips={filterChipList} onReset={resetFilters} className="lg:max-w-md" />
            <MapSafetyNotice />
          </div>
        </div>
      </div>
      {filterDialogOpen && basecampMode && (
        <BasecampFilterDialog
          initial={filters}
          onApply={(next) => {
            setFilters(next);
            setFilterDialogOpen(false);
          }}
          onClose={() => setFilterDialogOpen(false)}
        />
      )}
      {filterDialogOpen && browsing && (
        <SpotFilterDialog
          initial={spotFilters}
          onApply={(next) => {
            setSpotFilters(next);
            setFilterDialogOpen(false);
          }}
          onClose={() => setFilterDialogOpen(false)}
        />
      )}
      {contextMenu &&
        createPortal(
          <MapOpenMenu
            key={`${contextMenu.position.lat},${contextMenu.position.lng}`}
            position={contextMenu.position}
            onClose={closeContextMenu}
            onCandidates={(spots) => {
              closeContextMenu();
              setPickSpots(spots);
            }}
          />,
          contextMenu.host,
        )}
      {pickSpots && <SpotPickDialog spots={pickSpots} onClose={() => setPickSpots(null)} />}
      {/* 상세·폼이 열려 있을 때는 시트 아래 고정 버튼 자리를 지키려고 하단 탭을 숨긴다. 패널의 뒤로 가기로 목록에 돌아오면 다시 보인다. */}
      {!contentOpen && <BottomTabBar />}
    </div>
  );
}

// 서버는 장소가 너무 많으면 markers를 비우고 clusters만 준다. 그래서 묶음일 때는 묶음 개수의 합이 지금 화면의 장소 수다.
function totalCount(area: SpotsInView): number {
  if (area.markers.length > 0) return area.markers.length;
  return area.clusters.reduce((sum, cluster) => sum + cluster.count, 0);
}

// 목록 머리 앞에 붙는 지역 이름이다. 화면에 시·도가 여럿 걸치면 "지도에 보이는"으로 적어 개수가 화면 전체의 수임을 드러낸다.
// 지역 이름을 아직 모르거나 바다처럼 행정구역이 없는 곳에서는 "이 지역"으로 적는다.
function regionPhrase(label: RegionLabel | null): string {
  if (label?.kind === 'named') return label.name;
  if (label?.kind === 'wide') return '지도에 보이는';
  return '이 지역';
}

function ListHeader({
  area,
  regionName,
}: {
  area: SpotsInView;
  regionName: RegionLabel | null;
}) {
  const counted = area.status === 'ready' || area.markers.length > 0 || area.clusters.length > 0;
  return (
    <h2 className="truncate text-lg font-semibold text-ink">
      {regionPhrase(regionName)} 장소{counted && <span className="tabular-nums"> {totalCount(area)}곳</span>}
    </h2>
  );
}

// 검색 칸 오른쪽의 상세 조건 버튼이다. 켜진 조건이 있으면 개수를 함께 적고 선택 색으로 바꾼다.
function FilterButton({ count, onClick }: { count: number; onClick: () => void }) {
  const active = count > 0;
  return (
    <button
      type="button"
      onClick={onClick}
      aria-haspopup="dialog"
      aria-label={active ? `조건 ${count}개 켜짐, 조건 바꾸기` : '조건 고르기'}
      className={
        active
          ? 'pointer-events-auto inline-flex min-h-11 shrink-0 items-center gap-2 rounded-control border border-forest bg-forest-soft px-3 text-base font-semibold text-forest-deep'
          : 'pointer-events-auto inline-flex min-h-11 shrink-0 items-center gap-2 rounded-control border border-ink-subtle bg-card px-3 text-base font-semibold text-ink hover:bg-paper-deep'
      }
    >
      <Icon name="sliders" size={20} />
      조건{active && <span className="tabular-nums">{count}</span>}
    </button>
  );
}

function BasecampListHeader({
  result,
  regionName,
}: {
  result: BasecampsInView;
  regionName: RegionLabel | null;
}) {
  const counted = result.status === 'ready' || result.items.length > 0;
  return (
    <h2 className="truncate text-lg font-semibold text-ink">
      {regionPhrase(regionName)} 베이스캠프
      {counted && (
        <span className="tabular-nums">
          {' '}
          {result.items.length}
          {result.hasNext ? '개 이상' : '개'}
        </span>
      )}
    </h2>
  );
}

function SpotList({
  area,
  waitingForMap,
  filtered,
}: {
  area: SpotsInView;
  waitingForMap: boolean;
  filtered: boolean;
}) {
  const showSkeleton = useDelayedFlag(area.status === 'loading' || waitingForMap);

  if (showSkeleton) {
    return (
      <div aria-busy="true" className="flex flex-col">
        {[0, 1, 2].map((index) => (
          <div key={index} className="flex min-h-14 items-center gap-3 border-b border-contour px-4 py-3">
            <Skeleton className="size-8 shrink-0" />
            <div className="flex flex-1 flex-col gap-2">
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="h-3 w-1/3" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="flex flex-col">
      {area.status === 'error' && area.error && (
        <div className="flex flex-col gap-3 px-4 pt-4">
          <Notice tone="danger" title="장소를 불러오지 못했어요">
            {area.error}
          </Notice>
          <div>
            <Button variant="secondary" onClick={area.retry}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}

      {area.status === 'ready' && area.markers.length === 0 && area.clusters.length > 0 && (
        <div className="px-4 pt-4">
          {/* 머리의 장소 수가 지역 전체가 아니라 지금 화면의 수라는 것을 함께 적는다. 상태 안내라서 상자로 감싸지 않는다. */}
          <p className="text-sm text-ink-muted">
            지금 지도에 보이는 곳에 장소가 모두 <span className="tabular-nums">{totalCount(area)}곳</span> 있어요. 지도를
            확대하면 하나씩 볼 수 있어요.
          </p>
        </div>
      )}

      {area.status === 'ready' && area.markers.length === 0 && area.clusters.length === 0 && (
        filtered ? (
          <EmptyState title="조건에 맞는 장소가 없어요" description="조건을 줄이거나 지도를 옮기면 다시 찾아요." />
        ) : (
          <EmptyState title="이 지역에는 장소가 없어요" description="지도를 옮기거나 축소하면 다시 찾아요." />
        )
      )}

      {area.markers.length > 0 && (
        <ul aria-label="이 지역 장소">
          {area.markers.map((marker) => (
            <SpotListRow key={marker.spotId} marker={marker} />
          ))}
        </ul>
      )}
    </div>
  );
}

// 비회원은 로그인한 뒤 제보 화면으로 돌아오게 보낸다. 이메일 인증 여부는 제보 화면이 안내한다.
function ReportButton() {
  const session = useSession();
  const anonymous = session.status === 'anonymous' || (session.status === 'authenticated' && session.me === null);
  const target = anonymous ? withNext('/login', '/bakjis/new') : '/bakjis/new';

  return (
    <Link
      to={target}
      className="pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-control border border-contour bg-card px-3 text-base font-semibold text-ink hover:bg-paper-deep"
    >
      <Icon name="backpack" size={20} />
      박지 제보
    </Link>
  );
}
