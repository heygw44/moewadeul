import { useCallback, useEffect, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import { createPortal } from 'react-dom';
import type { AiInterpretResponse, NearbySpot } from '../../api/types';
import { BottomTabBar } from '../../app/layout/BottomTabBar';
import { SiteHeader } from '../../app/layout/SiteHeader';
import { Link, navigate } from '../../app/router';
import type { SpotType } from '../../api/types';
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
import { radiusBounds } from '../../map/radiusBounds';
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
import { useSession } from '../member/session';
import { BakjiReportPanel } from './BakjiReportPanel';
import { MapLegend } from './MapLegend';
import { MapRegionChip } from './MapRegionChip';
import { MapSafetyNotice } from './MapSafetyNotice';
import { AiInterpretSummary } from './AiInterpretSummary';
import type { AiSummary } from './AiInterpretSummary';
import { MapSearchBar } from './MapSearchBar';
import { SpotDetailPanel } from './SpotDetailPanel';
import { SpotFilterDialog } from './SpotFilterDialog';
import { NO_SPOT_FILTERS, filtersFromInterpretation, spotFilterChips } from './spotFilters';
import type { SpotFilters } from './spotFilters';
import { SpotListRow } from './SpotListRow';
import { SpotPreviewBoard } from './SpotPreviewBoard';
import { useMapMoving } from './useMapMoving';
import { useRegionName } from './useRegionName';
import { useSpotsInView } from './useSpotsInView';
import type { SpotsInView } from './useSpotsInView';
import { useSpotsNearby } from './useSpotsNearby';
import type { SpotsNearby } from './useSpotsNearby';

type SheetSnap = 'peek' | 'half' | 'full';

// 모바일에서 시트 윗변이 화면 아래에서 떨어진 높이다. Sheet의 단계별 높이(28dvh, 55dvh, 92dvh)와 같은 값이다.
// 하단 탭 위에 뜨는 전체 단계는 시트 높이에서 탭 높이를 빼고 그만큼 올리므로, 윗변은 탭과 상관없이 92dvh다.
const SHEET_TOP: Record<SheetSnap, string> = { peek: '28dvh', half: '55dvh', full: '92dvh' };

// 폰에서 지도 위쪽 끝부터 시트 윗변까지의 높이(px)다. 데스크톱은 시트가 지도 옆 패널이라 지도 높이 전체가 보인다.
function visibleMapHeight(host: HTMLElement | null): number | undefined {
  if (!host || window.matchMedia(DESKTOP_QUERY).matches) return undefined;
  const sheet = host.closest('[data-map-root]')?.querySelector('[data-sheet]');
  if (!sheet) return undefined;
  return sheet.getBoundingClientRect().top - host.getBoundingClientRect().top;
}

function sheetTop(snap: SheetSnap, aboveBottomNav: boolean): string {
  if (snap === 'full' || !aboveBottomNav) return SHEET_TOP[snap];
  return `calc(${SHEET_TOP[snap]} + var(--bottom-nav-h))`;
}

type MapFailure = { reason: MapLoadErrorReason; message: string };

// 지도를 우클릭하거나 길게 누른 곳에 띄운 메뉴다. host는 지도 래퍼가 만든 메뉴 칸 요소다.
type ContextMenu = { position: LatLng; host: HTMLElement };

// 반경 검색의 기준점과 반경이다. label은 목록 머리에 적는 기준점 이름이다.
type RadiusSearch = { center: LatLng; radiusKm: number; label: string };

// 처음에는 전국이 한 화면에 들어오도록 국토 가운데쯤을 중심으로 크게 축소해서 연다.
const INITIAL_VIEW = { center: { lat: 36.35, lng: 127.85 }, level: 12 };

// 제보 화면을 좌표와 함께 열었는데 그 좌표가 화면 밖이면, 이 확대 수준까지 확대해서 옮긴다.
const DRAFT_FOCUS_LEVEL = 5;

// 묶음을 누르면 이만큼 확대해서 안의 장소가 풀려 보이게 한다.
const CLUSTER_ZOOM_STEP = 2;

// 데스크톱(lg)부터 지도 왼쪽에 패널을 두고, 장소를 고르면 지도 위에 근거 판을 띄운다.
const DESKTOP_QUERY = '(min-width: 64rem)';

// 지도 위에 띄우는 버튼(박지 제보·지형)이다. 흰 판에 그림자로 지도에서 띄운다.
const MAP_BUTTON_CLASS =
  'pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-control border border-transparent bg-surface px-3 text-base font-semibold text-ink shadow-raise hover:bg-fill';
const MAP_BUTTON_ON_CLASS =
  'pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-control border border-brand bg-brand px-3 text-base font-semibold text-white shadow-raise';

// 목록 위 유형 선택 칸이다. 상세 조건의 유형을 하나만 고르는 지름길이고, 전체는 유형 조건을 푼다.
const TYPE_SEGMENTS: ReadonlyArray<{ value: SpotType | null; label: string }> = [
  { value: null, label: '전체' },
  { value: 'CAMPSITE', label: '야영장' },
  { value: 'FOREST', label: '휴양림' },
  { value: 'BAKJI', label: '박지' },
];

function useIsDesktop(): boolean {
  const [desktop, setDesktop] = useState(() => window.matchMedia(DESKTOP_QUERY).matches);
  useEffect(() => {
    const query = window.matchMedia(DESKTOP_QUERY);
    const onChange = () => setDesktop(query.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);
  return desktop;
}

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
  // AI가 반경을 읽어 냈을 때의 반경 검색이다. 값이 있으면 지도 영역 대신 이 기준점에서 가까운 순으로 장소를 보여 준다.
  const [radiusSearch, setRadiusSearch] = useState<RadiusSearch | null>(null);
  const [aiSummary, setAiSummary] = useState<AiSummary | null>(null);
  // 지도에서 장소 마커를 눌러 고른 장소다. 베이스캠프 목록을 그 장소로 거른다.
  const [spotFilterId, setSpotFilterId] = useState<number | null>(null);
  // 데스크톱에서 목록을 둔 채 지도 위 근거 판으로 보고 있는 장소다.
  const [previewId, setPreviewId] = useState<number | null>(null);
  const desktop = useIsDesktop();
  // 장소 조건은 지도를 둘러보는 화면에서만 쓴다. 박지를 제보하거나 베이스캠프를 여는 중에는 근처 장소를 빠짐없이 보여 준다.
  const browsing = mode === 'browse';
  // 근거 판은 데스크톱에서 장소를 둘러볼 때, 상세가 열려 있지 않을 때만 띄운다.
  const previewEnabled = browsing && desktop && spotId === undefined;
  if (!previewEnabled && previewId !== null) setPreviewId(null);
  // 반경 검색과 AI 요약은 지도를 둘러보는 화면에서만 쓴다. 다른 화면으로 가면 버린다.
  if (!browsing && (radiusSearch !== null || aiSummary !== null)) {
    setRadiusSearch(null);
    setAiSummary(null);
  }
  const radiusActive = browsing && radiusSearch !== null;
  const spotFilterChipList = browsing ? spotFilterChips(spotFilters, setSpotFilters) : [];
  const basecampFilterChipList = basecampMode ? basecampFilterChips(filters, setFilters) : [];
  // 조건 버튼과 칩 줄은 장소를 둘러볼 때와 베이스캠프를 찾을 때만 보이고, 화면마다 자기 조건을 보여 준다.
  // 반경 칩은 상세 조건이 아니라서 조건 버튼의 개수에는 세지 않는다.
  const filterChipList = basecampMode
    ? basecampFilterChipList
    : radiusSearch !== null
      ? [
          {
            key: 'radius',
            label: `반경 ${radiusSearch.radiusKm}km`,
            onRemove: () => setRadiusSearch(null),
          },
          ...spotFilterChipList,
        ]
      : spotFilterChipList;
  const resetFilters = basecampMode
    ? () => setFilters(NO_BASECAMP_FILTERS)
    : () => {
        setSpotFilters(NO_SPOT_FILTERS);
        setRadiusSearch(null);
        setAiSummary(null);
      };
  // 베이스캠프를 찾는 동안에는 지도의 마커를 베이스캠프가 맡고, 장소 조회는 쉰다. 반경 검색 중에도 영역 조회는 쉰다.
  const area = useSpotsInView(map, !basecampMode && !radiusActive, browsing ? spotFilters : NO_SPOT_FILTERS);
  const nearby = useSpotsNearby(map, radiusSearch, spotFilters, radiusActive);
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
      if (previewEnabled) {
        setPreviewId(id);
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
  }, [map, reporting, basecampMode, basecampId, previewEnabled]);

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
      const host = map.showMenu(position, visibleMapHeight(hostRef.current));
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

  // 지도의 빈 곳을 누르면 근거 판을 닫는다.
  useEffect(() => {
    if (!map || !previewEnabled) return;
    return map.onMapClick(() => setPreviewId(null));
  }, [map, previewEnabled]);

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
    map?.setSelected(basecampMode ? spotFilterId : opening ? (openSpotId ?? null) : (spotId ?? previewId));
  }, [map, spotId, basecampMode, spotFilterId, opening, openSpotId, previewId]);

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

  // 일반 지역 검색으로 지역을 고르면 반경 검색과 AI 요약은 끝내고, 지도 영역 조회로 돌아간다.
  function selectRegion(candidate: RegionCandidate) {
    setRadiusSearch(null);
    setAiSummary(null);
    moveToRegion(candidate);
  }

  // AI가 읽은 조건으로 장소 조건을 통째로 바꾼다. 반경이 있으면 지역 후보(없으면 지도 가운데)를 기준점으로 반경 검색을 하고,
  // 없으면 지역 후보로 지도만 옮긴다. 상세 화면을 보고 있었다면 결과 목록이 보이도록 지도 화면으로 돌아간다.
  function applyAiResult(result: AiInterpretResponse, candidate: RegionCandidate | null) {
    if (!map) return;
    const { filters: nextFilters, droppedSettings } = filtersFromInterpretation(result);
    setSpotFilters(nextFilters);
    setSearchPin(null);
    if (result.radiusKm !== undefined) {
      const center = candidate?.position ?? map.getCenter();
      setRadiusSearch({ center, radiusKm: result.radiusKm, label: candidate?.name ?? '지도 가운데' });
      map.fitBounds(radiusBounds(center, result.radiusKm));
      if (spotId !== undefined) navigate('/map');
    } else {
      setRadiusSearch(null);
      if (candidate) moveToRegion(candidate);
      else if (spotId !== undefined) navigate('/map');
    }
    const hasFilter = spotFilterChips(nextFilters, () => {}).length > 0;
    setAiSummary({
      unparsed: result.unparsed,
      missingRegion: result.region !== undefined && candidate === null ? result.region : undefined,
      droppedSettings,
      empty: result.region === undefined && result.radiusKm === undefined && !hasFilter && droppedSettings.length === 0,
    });
  }

  // 검색 줄(검색 칸·AI 검색·조건), 유형 선택, 켜진 조건 칩, AI 요약이다.
  // 모바일은 지도 위에 띄우고, 데스크톱은 왼쪽 패널 위쪽 도구 줄에 둔다. 둘 중 하나만 화면에 보인다.
  function renderControls(inPanel: boolean) {
    if (!map) return null;
    const filterable = browsing || basecampMode;
    return (
      <>
        <div className={`flex w-full items-start gap-2 ${inPanel ? '' : 'pointer-events-none'}`}>
          <MapSearchBar
            onSearch={() => setSearchPin(null)}
            onSelect={selectRegion}
            mapMoving={mapMoving.moving}
            inPanel={inPanel}
            ai={browsing ? { onResult: applyAiResult } : undefined}
          />
          {filterable && !inPanel && (
            <FilterButton
              compact
              count={basecampMode ? basecampFilterChipList.length : spotFilterChipList.length}
              onClick={() => setFilterDialogOpen(true)}
            />
          )}
        </div>
        {inPanel && filterable && (
          <div className="flex gap-1.5">
            {browsing && (
              <TypeSegments types={spotFilters.types} onChange={(types) => setSpotFilters({ ...spotFilters, types })} />
            )}
            <FilterButton
              count={basecampMode ? basecampFilterChipList.length : spotFilterChipList.length}
              onClick={() => setFilterDialogOpen(true)}
            />
          </div>
        )}
        <ActiveFilterChips chips={filterChipList} onReset={resetFilters} />
        {aiSummary && <AiInterpretSummary summary={aiSummary} className={inPanel ? 'w-full' : 'w-fit max-w-full'} />}
      </>
    );
  }

  function retryMap() {
    setMapFailure(null);
    setMapAttempt((value) => value + 1);
  }

  // 폰에서 시트를 전체로 올렸거나 길게 누른 메뉴가 떠 있으면 범례와 지역 칩을 숨긴다. 메뉴를 가리지 않게 하기 위해서다.
  const hideMapOverlays = snap === 'full' || contextMenu !== null;

  return (
    <div className="flex h-dvh w-full flex-col overflow-hidden bg-canvas">
      <SiteHeader />
      <div data-map-root className="relative min-h-0 flex-1 lg:flex">
        <Sheet
          aboveBottomNav={!contentOpen}
          snap={snap}
          onSnapChange={setSnap}
          label={sheetLabel}
          toolbar={map ? renderControls(true) : undefined}
          header={
            contentOpen ? undefined : basecampMode ? (
              <BasecampListHeader result={basecamps} regionName={regionName} />
            ) : radiusActive && radiusSearch ? (
              <NearbyListHeader nearby={nearby} radius={radiusSearch} />
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
          ) : spotId === undefined && radiusActive ? (
            <NearbyList nearby={nearby} waitingForMap={map === null && mapFailure === null} />
          ) : spotId === undefined ? (
            <SpotList
              area={area}
              waitingForMap={map === null && mapFailure === null}
              filtered={spotFilterChipList.length > 0}
              previewId={previewId}
              onPreview={previewEnabled ? setPreviewId : undefined}
            />
          ) : (
            <SpotDetailPanel key={spotId} spotId={spotId} onBack={() => navigate('/map')} />
          )}
        </Sheet>

        {/* z-0으로 쌓임 맥락을 따로 만들어서, 지도와 위에 뜬 버튼이 모바일 시트보다 아래에 그려지게 한다. */}
        {/* 폰에서 시트 윗변 높이를 --sheet-top으로 넘긴다. 범례·지역 칩·카카오 로고가 이 높이 위에 선다. 데스크톱은 시트가 옆 패널이라 0이다. */}
        <div
          style={{ '--mobile-sheet-top': sheetTop(snap, !contentOpen) } as CSSProperties}
          className="absolute inset-0 z-0 [--sheet-top:var(--mobile-sheet-top)] lg:relative lg:flex-1 lg:[--sheet-top:0px]"
        >
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
              <MapLegend hiddenOnMobile={hideMapOverlays} />
              <MapRegionChip label={regionName} hiddenOnMobile={hideMapOverlays} />
            </>
          )}

          {mapFailure && (
            <div className="absolute inset-0 flex items-center justify-center bg-canvas p-4">
              <div className="w-full max-w-sm">
                <Notice tone="danger" title="지도를 불러오지 못했어요">
                  <div className="flex flex-col gap-3">
                    <p>{mapFailure.message}</p>
                    {mapFailure.reason === 'NO_KEY' ? (
                      import.meta.env.DEV && (
                        <p>저장소 루트의 .env에 VITE_KAKAO_JS_KEY를 넣고 개발 서버를 다시 켜 주세요.</p>
                      )
                    ) : (
                      <div>
                        <Button variant="secondary" onClick={retryMap}>
                          다시 불러오기
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
            {/* 모바일은 검색 줄을 지도 위 맨 위에 띄우고, 데스크톱은 왼쪽 패널 도구 줄이 맡는다. */}
            <div className="flex flex-col gap-2 lg:hidden">{renderControls(false)}</div>
            {map && (
              <div className="flex flex-wrap justify-end gap-2 lg:justify-start">
                {!reporting && !basecampMode && !opening && <ReportButton />}
                <button
                  type="button"
                  aria-pressed={terrainOn}
                  onClick={() => setTerrainOn((value) => !value)}
                  className={terrainOn ? MAP_BUTTON_ON_CLASS : MAP_BUTTON_CLASS}
                >
                  <Icon name="terrain" size={20} />
                  지형
                </button>
              </div>
            )}
            <MapSafetyNotice />
          </div>

          {previewEnabled && previewId !== null && (
            <div className="pointer-events-none absolute top-4 right-4 z-10">
              <SpotPreviewBoard key={previewId} spotId={previewId} onClose={() => setPreviewId(null)} />
            </div>
          )}
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

// 목록 행을 담는 흰 목록판이다. 패널 안쪽 여백만큼 띄우고 행 사이에 선을 긋는다.
const LIST_BOARD_CLASS =
  'mx-3 mb-4 divide-y divide-line overflow-hidden rounded-sheet border border-line bg-surface lg:mx-5';

// 서버는 장소가 너무 많으면 markers를 비우고 clusters만 준다. 그래서 묶음일 때는 묶음 개수의 합이 지금 화면의 장소 수다.
function totalCount(area: SpotsInView): number {
  if (area.markers.length > 0) return area.markers.length;
  return area.clusters.reduce((sum, cluster) => sum + cluster.count, 0);
}

// 목록 머리 띠의 제목이다. 장소 수는 띠 오른쪽에 따로 적으므로 지역 이름만 쓴다.
function regionTitle(label: RegionLabel | null): string {
  if (label?.kind === 'named') return label.name;
  if (label?.kind === 'wide') return '여러 시·도';
  return '이 지역';
}

function ListHeader({ area, regionName }: { area: SpotsInView; regionName: RegionLabel | null }) {
  const counted = area.status === 'ready' || area.markers.length > 0 || area.clusters.length > 0;
  return (
    <h2 className="flex items-baseline justify-between gap-3">
      <span className="truncate text-lg font-extrabold tracking-[-0.02em] lg:text-[1.375rem]">
        {regionTitle(regionName)}
        <span className="sr-only"> 장소</span>
      </span>
      {counted && (
        <span className="shrink-0 text-sm tabular-nums text-ink-muted lg:text-brand-ink">
          장소 {totalCount(area)}곳
        </span>
      )}
    </h2>
  );
}

function NearbyListHeader({ nearby, radius }: { nearby: SpotsNearby; radius: RadiusSearch }) {
  const counted = nearby.status === 'ready' || nearby.items.length > 0;
  return (
    <h2 className="truncate text-lg font-extrabold tracking-[-0.02em]">
      {radius.label}에서 {radius.radiusKm}km 안 장소
      {counted && (
        <span className="tabular-nums">
          {' '}
          {nearby.items.length}
          {nearby.hasNext ? '곳 이상' : '곳'}
        </span>
      )}
    </h2>
  );
}

// 상세 조건 버튼이다. 켜진 조건이 있으면 개수를 함께 적고 먹색으로 뒤집는다.
// compact는 모바일 지도 위 검색 줄에 둘 때 켠다. 아이콘만 그리고 지도 위라서 그림자로 띄운다.
function FilterButton({ count, onClick, compact = false }: { count: number; onClick: () => void; compact?: boolean }) {
  const active = count > 0;
  return (
    <button
      type="button"
      onClick={onClick}
      aria-haspopup="dialog"
      aria-label={active ? `조건 ${count}개 켜짐, 조건 바꾸기` : '조건 고르기'}
      className={[
        'pointer-events-auto inline-flex min-h-11 shrink-0 items-center justify-center gap-1.5 rounded-control text-sm font-semibold whitespace-nowrap',
        compact ? 'min-w-11 px-2.5 shadow-raise' : 'px-3',
        active
          ? 'border border-ink bg-ink text-white'
          : compact
            ? 'border border-transparent bg-surface text-ink hover:bg-fill'
            : 'border border-line bg-surface text-ink hover:bg-fill',
      ].join(' ')}
    >
      <Icon name="sliders" size={18} />
      {!compact && '조건'}
      {active && <span className="tabular-nums">{count}</span>}
    </button>
  );
}

// 유형을 하나만 고르는 칸이다. 상세 조건에서 유형을 여럿 골랐으면 어느 칸도 눌리지 않은 채로 둔다.
function TypeSegments({ types, onChange }: { types: SpotType[]; onChange: (types: SpotType[]) => void }) {
  return (
    <div role="group" aria-label="장소 유형" className="flex min-w-0 flex-1 gap-1.5">
      {TYPE_SEGMENTS.map((segment) => {
        const pressed = segment.value === null ? types.length === 0 : types.length === 1 && types[0] === segment.value;
        return (
          <button
            key={segment.label}
            type="button"
            aria-pressed={pressed}
            onClick={() => onChange(segment.value === null ? [] : [segment.value])}
            className={`inline-flex min-h-11 min-w-0 flex-1 items-center justify-center rounded-control px-1 text-sm whitespace-nowrap ${
              pressed
                ? 'border border-ink bg-ink font-bold text-white'
                : 'border border-line bg-surface text-ink hover:bg-fill'
            }`}
          >
            {segment.label}
          </button>
        );
      })}
    </div>
  );
}

function BasecampListHeader({ result, regionName }: { result: BasecampsInView; regionName: RegionLabel | null }) {
  const counted = result.status === 'ready' || result.items.length > 0;
  return (
    <h2 className="flex items-baseline justify-between gap-3">
      <span className="truncate text-lg font-extrabold tracking-[-0.02em] lg:text-[1.375rem]">
        {regionTitle(regionName)}
        <span className="sr-only"> 베이스캠프</span>
      </span>
      {counted && (
        <span className="shrink-0 text-sm tabular-nums text-ink-muted lg:text-brand-ink">
          베이스캠프 {result.items.length}
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
  previewId,
  onPreview,
}: {
  area: SpotsInView;
  waitingForMap: boolean;
  filtered: boolean;
  previewId: number | null;
  onPreview?: (spotId: number) => void;
}) {
  const showSkeleton = useDelayedFlag(area.status === 'loading' || waitingForMap);

  if (showSkeleton) {
    return (
      <div aria-busy="true" className="flex flex-col">
        {[0, 1, 2].map((index) => (
          <div key={index} className="flex min-h-16 items-center gap-3 border-b border-line px-4 py-3 lg:px-5">
            <Skeleton className="size-7 shrink-0 rounded-full" />
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
        <div className="px-4 pt-4 lg:px-5">
          {/* 머리의 장소 수가 지역 전체가 아니라 지금 화면의 수라는 것을 함께 적는다. 상태 안내라서 상자로 감싸지 않는다. */}
          <p className="text-sm text-ink-muted">
            장소가 많아서 지도에 묶어서 보여요. 지도를 확대하면 목록으로 볼 수 있어요.
          </p>
        </div>
      )}

      {area.status === 'ready' &&
        area.markers.length === 0 &&
        area.clusters.length === 0 &&
        (filtered ? (
          <EmptyState title="조건에 맞는 장소가 없어요" description="조건을 줄이거나 지도를 다른 곳으로 옮겨 주세요." />
        ) : (
          <EmptyState title="이 지역에는 장소가 없어요" description="지도를 옮기거나 축소해 주세요." />
        ))}

      {area.markers.length > 0 && (
        <>
          <p className="flex justify-between px-4 pt-3 pb-2 text-[0.8125rem] text-ink-muted lg:px-5">
            <span>지도 가운데에서 가까운 순</span>
            <span className="tabular-nums">{area.markers.length}곳</span>
          </p>
          <ul aria-label="이 지역의 장소" className={LIST_BOARD_CLASS}>
            {area.markers.map((marker, index) => (
              <SpotListRow
                key={marker.spotId}
                marker={marker}
                number={index + 1}
                distanceKm={marker.distanceKm}
                selected={marker.spotId === previewId}
                onPreview={onPreview}
              />
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

// 반경 검색 결과 목록이다. 기준점에서 가까운 순서로 오고, 행마다 거리를 함께 적는다.
function NearbyList({ nearby, waitingForMap }: { nearby: SpotsNearby; waitingForMap: boolean }) {
  const showSkeleton = useDelayedFlag((nearby.status === 'loading' && nearby.items.length === 0) || waitingForMap);

  if (showSkeleton) {
    return (
      <div aria-busy="true" className="flex flex-col">
        {[0, 1, 2].map((index) => (
          <div key={index} className="flex min-h-16 items-center gap-3 border-b border-line px-4 py-3 lg:px-5">
            <Skeleton className="size-7 shrink-0 rounded-full" />
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
      {nearby.status === 'error' && nearby.error && (
        <div className="flex flex-col gap-3 px-4 pt-4">
          <Notice tone="danger" title="장소를 불러오지 못했어요">
            {nearby.error}
          </Notice>
          <div>
            <Button variant="secondary" onClick={nearby.retry}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}

      {nearby.status === 'ready' && nearby.items.length === 0 && (
        <EmptyState title="반경 안에 조건에 맞는 장소가 없어요" description="반경 칩을 빼거나 조건을 줄여 주세요." />
      )}

      {nearby.items.length > 0 && (
        <ul aria-label="반경 안의 장소" className={`${LIST_BOARD_CLASS} mt-3`}>
          {nearby.items.map((item, index) => (
            <SpotListRow key={item.spotId} marker={item} number={index + 1} distanceKm={item.distanceKm} />
          ))}
        </ul>
      )}

      {nearby.hasNext && (
        <div className="flex flex-col gap-2 px-4 py-4">
          {nearby.moreError && (
            <p role="alert" className="text-sm text-danger">
              {nearby.moreError}
            </p>
          )}
          <Button variant="secondary" fullWidth loading={nearby.loadingMore} onClick={nearby.loadMore}>
            더 보기
          </Button>
        </div>
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
    <Link to={target} className={MAP_BUTTON_CLASS}>
      <Icon name="backpack" size={20} />
      박지 제보
    </Link>
  );
}
