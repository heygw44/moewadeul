import { useCallback, useEffect, useEffectEvent, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import { createPortal } from 'react-dom';
import type { AiInterpretResponse, BasecampDetail, NearbySpot, SpotDetail } from '../../api/types';
import { BottomTabBar } from '../../app/layout/BottomTabBar';
import { SiteHeader } from '../../app/layout/SiteHeader';
import { Link, navigate } from '../../app/router';
import type { SpotType } from '../../api/types';
import { ActiveFilterChips } from '../../components/ActiveFilterChips';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { Sheet } from '../../components/Sheet';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { createMapView } from '../../map/KakaoMap';
import { MapLoadError } from '../../map/kakaoLoader';
import type { MapLoadErrorReason } from '../../map/kakaoLoader';
import { searchRegions } from '../../map/regionSearch';
import type { RegionCandidate } from '../../map/regionSearch';
import type { RegionLabel } from '../../map/regionLabel';
import { radiusBounds } from '../../map/radiusBounds';
import type { LatLng, MapView, SpotMarkerData } from '../../map/types';
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
import { BakjiEditPanel } from './BakjiEditPanel';
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
import { readMapScreen, rememberMapScreen } from './mapScreenMemory';
import type { RadiusSearch } from './mapScreenMemory';
import type { SpotsNearby } from './useSpotsNearby';

type SheetSnap = 'peek' | 'half' | 'full';

const SHEET_TOP_DVH: Record<SheetSnap, number> = { peek: 28, half: 55, full: 92 };

function visibleMapHeight(host: HTMLElement | null): number | undefined {
  if (!host || window.matchMedia(DESKTOP_QUERY).matches) return undefined;
  const sheet = host.closest('[data-map-root]')?.querySelector('[data-sheet]');
  if (!sheet) return undefined;
  return sheet.getBoundingClientRect().top - host.getBoundingClientRect().top;
}

const MIN_OPEN_SPAN_PX = 48;

function openMapSpan(
  host: HTMLElement,
  controls: HTMLElement | null,
  snap: SheetSnap,
): { top: number; bottom: number } {
  const rect = host.getBoundingClientRect();
  if (window.matchMedia(DESKTOP_QUERY).matches) return { top: 0, bottom: rect.height };
  const sheetTop = window.innerHeight * (1 - SHEET_TOP_DVH[snap] / 100);
  const bottom = Math.min(rect.height, sheetTop - rect.top);
  const top = controls ? Math.max(0, controls.getBoundingClientRect().bottom - rect.top) : 0;
  if (bottom - top >= MIN_OPEN_SPAN_PX) return { top, bottom };
  return bottom >= MIN_OPEN_SPAN_PX ? { top: 0, bottom } : { top: 0, bottom: rect.height };
}

function centerPlacing(map: MapView, position: LatLng, level: number, mapHeight: number, targetY: number): LatLng {
  if (mapHeight <= 0) return position;
  const { sw, ne } = map.getBounds();
  const latPerPx = ((ne.lat - sw.lat) / mapHeight) * 2 ** (level - map.getLevel());
  return { lat: position.lat - (mapHeight / 2 - targetY) * latPerPx, lng: position.lng };
}

function selectedSpotMarker(detail: SpotDetail): SpotMarkerData {
  return {
    id: detail.spotId,
    kind: detail.type,
    name: detail.name,
    position: { lat: detail.lat, lng: detail.lng },
    warning: detail.parkWarning.warned,
    closed: detail.publicDetail?.closedNow ?? false,
  };
}

function basecampSpotMarker(detail: BasecampDetail): SpotMarkerData | null {
  const { spot } = detail;
  if (spot === undefined) return null;
  return {
    id: spot.spotId,
    kind: spot.type,
    name: spot.name,
    position: { lat: spot.lat, lng: spot.lng },
    warning: false,
    closed: false,
  };
}

function sheetTop(snap: SheetSnap, aboveBottomNav: boolean): string {
  if (snap === 'full' || !aboveBottomNav) return `${SHEET_TOP_DVH[snap]}dvh`;
  return `calc(${SHEET_TOP_DVH[snap]}dvh + var(--bottom-nav-h))`;
}

type MapFailure = { reason: MapLoadErrorReason; message: string };

type ContextMenu = { position: LatLng; host: HTMLElement };

const INITIAL_VIEW = { center: { lat: 36.35, lng: 127.85 }, level: 12 };

const SPOT_FOCUS_LEVEL = 7;

const DRAFT_FOCUS_LEVEL = 5;

const CLUSTER_ZOOM_STEP = 2;

const DESKTOP_QUERY = '(min-width: 64rem)';

const MAP_BUTTON_CLASS =
  'pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-[0.875rem] bg-surface px-3.5 text-[0.9375rem] font-semibold text-ink shadow-raise hover:bg-fill';
const MAP_BUTTON_ON_CLASS =
  'pointer-events-auto inline-flex min-h-11 items-center gap-2 rounded-[0.875rem] bg-brand px-3.5 text-[0.9375rem] font-semibold text-white shadow-raise hover:bg-brand-strong';

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
  basecampId?: number;
  openSpotId?: number;
  mode?: 'browse' | 'report' | 'bakjiEdit' | 'basecamps' | 'basecampOpen';
  initialDraft?: LatLng;
  nextAfterReport?: 'basecamp';
  editSpotId?: number;
};

export function MapPage({
  spotId,
  basecampId,
  openSpotId,
  mode = 'browse',
  initialDraft,
  nextAfterReport,
  editSpotId,
}: MapPageProps) {
  const reporting = mode === 'report';
  const editing = mode === 'bakjiEdit' && editSpotId !== undefined;
  const placing = reporting || editing;
  const basecampMode = mode === 'basecamps';
  const opening = mode === 'basecampOpen';
  const contentOpen = spotId !== undefined || placing || basecampId !== undefined || opening;
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
  const searchMoveLevelRef = useRef<number | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [contextMenu, setContextMenu] = useState<ContextMenu | null>(null);
  const [pickSpots, setPickSpots] = useState<NearbySpot[] | null>(null);
  const [appliedDraftKey, setAppliedDraftKey] = useState<string | null>(null);
  const [filters, setFilters] = useState<BasecampFilters>(() => readMapScreen().basecampFilters);
  const [spotFilters, setSpotFilters] = useState<SpotFilters>(() => readMapScreen().spotFilters);
  const [filterDialogOpen, setFilterDialogOpen] = useState(false);
  const [radiusSearch, setRadiusSearch] = useState<RadiusSearch | null>(() => readMapScreen().radiusSearch);
  const [aiSummary, setAiSummary] = useState<AiSummary | null>(() => readMapScreen().aiSummary);
  const focusKey =
    spotId !== undefined
      ? `spot:${spotId}`
      : basecampId !== undefined
        ? `basecamp:${basecampId}`
        : editing
          ? `edit:${editSpotId}`
          : null;
  const [spotFocus, setSpotFocus] = useState<{ key: string; marker: SpotMarkerData } | null>(null);
  if (spotFocus !== null && spotFocus.key !== focusKey) setSpotFocus(null);
  const focusedSpot = spotFocus?.marker ?? null;
  const focusOn = (key: string | null, marker: SpotMarkerData | null) => {
    if (key !== null && marker !== null) {
      setSpotFocus((previous) => (previous?.key === key ? previous : { key, marker }));
    }
  };
  const [spotFilterId, setSpotFilterId] = useState<number | null>(null);
  const [previewId, setPreviewId] = useState<number | null>(null);
  const desktop = useIsDesktop();
  const browsing = mode === 'browse';
  const previewEnabled = browsing && desktop && spotId === undefined;
  if (!previewEnabled && previewId !== null) setPreviewId(null);
  if (!browsing && (radiusSearch !== null || aiSummary !== null)) {
    setRadiusSearch(null);
    setAiSummary(null);
  }
  const radiusActive = browsing && radiusSearch !== null;
  const spotFilterChipList = browsing ? spotFilterChips(spotFilters, setSpotFilters) : [];
  const basecampFilterChipList = basecampMode ? basecampFilterChips(filters, setFilters) : [];
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
  const mapButtonsRef = useRef<HTMLDivElement>(null);
  const keepOverlay = useCallback(() => overlayRef.current?.contains(document.activeElement) ?? false, []);
  const mapMoving = useMapMoving(map, keepOverlay);

  if (!placing && submitted) setSubmitted(false);

  const initialDraftKey = reporting && initialDraft ? `${initialDraft.lat},${initialDraft.lng}` : null;
  if (initialDraftKey !== appliedDraftKey) {
    setAppliedDraftKey(initialDraftKey);
    if (initialDraft && initialDraftKey !== null) setDraft(initialDraft);
  }

  if (snapOpen !== contentOpen) {
    setSnapOpen(contentOpen);
    setSnap(contentOpen ? 'half' : 'peek');
  }

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const element = document.createElement('div');
    element.className = 'absolute inset-0';
    host.appendChild(element);

    let cancelled = false;
    let created: MapView | null = null;
    createMapView(element, readMapScreen().view ?? INITIAL_VIEW).then(
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

  useEffect(() => {
    const host = hostRef.current;
    if (!map || !host) return;
    const observer = new ResizeObserver(() => map.relayout());
    observer.observe(host);
    return () => observer.disconnect();
  }, [map]);

  useEffect(() => {
    rememberMapScreen({ spotFilters, basecampFilters: filters, radiusSearch, aiSummary });
  }, [spotFilters, filters, radiusSearch, aiSummary]);

  useEffect(() => {
    if (!map) return;
    return map.onIdle(() => rememberMapScreen({ view: { center: map.getCenter(), level: map.getLevel() } }));
  }, [map]);

  const moveToSpot = useEffectEvent((position: LatLng) => {
    const host = hostRef.current;
    if (!map || !host) return;
    map.relayout();
    const level = Math.min(map.getLevel(), SPOT_FOCUS_LEVEL);
    const { top, bottom } = openMapSpan(host, mapButtonsRef.current, snap);
    map.panTo(centerPlacing(map, position, level, host.getBoundingClientRect().height, (top + bottom) / 2), level);
  });
  useEffect(() => {
    if (map && focusedSpot) moveToSpot(focusedSpot.position);
  }, [map, focusedSpot]);

  useEffect(() => {
    map?.showSelectedSpot(editing ? null : focusedSpot);
  }, [map, focusedSpot, editing]);

  useEffect(() => {
    if (!map) return;
    const offMarker = map.onMarkerClick((id) => {
      if (placing) return;
      if (basecampMode) {
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
  }, [map, placing, basecampMode, basecampId, previewEnabled]);

  useEffect(() => {
    if (!map || !placing || submitted) return;
    const offClick = map.onMapClick(setDraft);
    return () => {
      offClick();
      map.showDraft(null);
      setDraft(null);
    };
  }, [map, placing, submitted]);

  useEffect(() => {
    map?.showDraft(draft);
  }, [map, draft]);

  const initialLat = reporting ? initialDraft?.lat : undefined;
  const initialLng = reporting ? initialDraft?.lng : undefined;
  useEffect(() => {
    if (!map || initialLat === undefined || initialLng === undefined) return;
    const { sw, ne } = map.getBounds();
    const inside = initialLat >= sw.lat && initialLat <= ne.lat && initialLng >= sw.lng && initialLng <= ne.lng;
    if (!inside) map.setCenter({ lat: initialLat, lng: initialLng }, Math.min(map.getLevel(), DRAFT_FOCUS_LEVEL));
  }, [map, initialLat, initialLng]);

  const contextMenuEnabled = browsing || basecampMode;
  const closeContextMenu = useCallback(() => {
    map?.showMenu(null);
    setContextMenu(null);
  }, [map]);

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

  useEffect(() => {
    if (!map || !previewEnabled) return;
    return map.onMapClick(() => setPreviewId(null));
  }, [map, previewEnabled]);

  useEffect(() => {
    map?.showSearchPin(searchPin);
  }, [map, searchPin]);

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

  const basecampSelectedId = basecampId !== undefined ? (focusedSpot?.id ?? null) : spotFilterId;
  useEffect(() => {
    map?.setSelected(basecampMode ? basecampSelectedId : opening ? (openSpotId ?? null) : (spotId ?? previewId));
  }, [map, spotId, basecampMode, basecampSelectedId, opening, openSpotId, previewId]);

  useEffect(() => {
    map?.setTerrain(terrainOn);
  }, [map, terrainOn]);

  useEffect(() => {
    if (spotId !== undefined || basecampId !== undefined) return;
    if (reporting) document.title = '박지 제보 · 뫼와들';
    else if (editing) document.title = '박지 수정 · 뫼와들';
    else if (opening) document.title = '베이스캠프 열기 · 뫼와들';
    else if (basecampMode) document.title = '베이스캠프 · 뫼와들';
    else document.title = '지도 · 뫼와들';
  }, [spotId, basecampId, reporting, editing, opening, basecampMode]);

  let sheetLabel = '장소 목록';
  if (reporting) sheetLabel = '박지 제보';
  else if (editing) sheetLabel = '박지 수정';
  else if (opening) sheetLabel = '베이스캠프 열기';
  else if (basecampId !== undefined) sheetLabel = '베이스캠프 상세';
  else if (basecampMode) sheetLabel = '베이스캠프 목록';
  else if (spotId !== undefined) sheetLabel = '장소 상세';

  function moveToRegion(candidate: RegionCandidate) {
    if (!map) return;
    searchMoveLevelRef.current = candidate.level;
    map.setCenter(candidate.position, candidate.level);
    if (map.getLevel() === candidate.level) searchMoveLevelRef.current = null;
    setSearchPin(candidate.pinned ? candidate.position : null);
    if (basecampId !== undefined) navigate('/basecamps');
    else if (mode === 'browse' && spotId !== undefined) navigate('/map');
  }

  function selectRegion(candidate: RegionCandidate) {
    setRadiusSearch(null);
    setAiSummary(null);
    moveToRegion(candidate);
  }

  const selectSearchedRegion = useEffectEvent((candidate: RegionCandidate) => selectRegion(candidate));
  useEffect(() => {
    if (!map || mode !== 'browse') return;
    const query = new URLSearchParams(window.location.search).get('q')?.trim();
    if (!query) return;
    navigate('/map', { replace: true });
    let cancelled = false;
    searchRegions(query)
      .then(([first]) => {
        if (!cancelled && first) selectSearchedRegion(first);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [map, mode]);

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
            <BasecampDetailPanel
              key={basecampId}
              basecampId={basecampId}
              onBack={() => navigate('/basecamps')}
              onLoaded={(detail) => focusOn(focusKey, basecampSpotMarker(detail))}
            />
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
                setSubmitted(true);
                setDraft(null);
                area.retry();
              }}
            />
          ) : editing ? (
            <BakjiEditPanel
              key={editSpotId}
              spotId={editSpotId}
              draft={draft}
              onCancel={() => navigate(`/spots/${editSpotId}`)}
              onLoaded={(detail) => {
                setDraft({ lat: detail.lat, lng: detail.lng });
                focusOn(focusKey, selectedSpotMarker(detail));
              }}
              onSubmitted={() => {
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
            <SpotDetailPanel
              key={spotId}
              spotId={spotId}
              onBack={() => navigate('/map')}
              onLoaded={(detail) => focusOn(focusKey, selectedSpotMarker(detail))}
              onBakjiDeleted={area.retry}
            />
          )}
        </Sheet>

        <div
          style={{ '--mobile-sheet-top': sheetTop(snap, !contentOpen) } as CSSProperties}
          className="absolute inset-0 z-0 [--sheet-top:var(--mobile-sheet-top)] lg:relative lg:mr-4 lg:mb-4 lg:flex-1 lg:overflow-hidden lg:rounded-media lg:[--sheet-top:0px]"
        >
          <div ref={hostRef} role="region" aria-label="지도" className="absolute inset-0" />
          {!mapFailure && (
            <>
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

          <div
            ref={overlayRef}
            inert={mapMoving.hidden}
            className={`pointer-events-none absolute inset-x-0 top-0 z-10 flex flex-col gap-2 px-4 pt-3 transition duration-200 motion-reduce:translate-y-0 motion-reduce:transition-none ${
              mapMoving.hidden ? '-translate-y-3 opacity-0' : ''
            }`}
          >
            <h1 className="sr-only">뫼와들 지도</h1>
            <div className="flex flex-col gap-2 lg:hidden">{renderControls(false)}</div>
            {map && (
              <div ref={mapButtonsRef} className="flex flex-wrap justify-end gap-2 lg:justify-start">
                {!placing && !basecampMode && !opening && <ReportButton />}
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
      {!contentOpen && <BottomTabBar />}
    </div>
  );
}

const LIST_BOARD_CLASS = 'mx-2 mb-4 flex flex-col gap-0.5 lg:mx-3';

function totalCount(area: SpotsInView): number {
  if (area.markers.length > 0) return area.markers.length;
  return area.clusters.reduce((sum, cluster) => sum + cluster.count, 0);
}

function regionTitle(label: RegionLabel | null): string {
  if (label?.kind === 'named') return label.name;
  if (label?.kind === 'wide') return '여러 시·도';
  return '이 지역';
}

function ListHeader({ area, regionName }: { area: SpotsInView; regionName: RegionLabel | null }) {
  const counted = area.status === 'ready' || area.markers.length > 0 || area.clusters.length > 0;
  return (
    <h2 className="flex items-baseline justify-between gap-3">
      <span className="truncate text-lg font-extrabold tracking-[-0.02em] lg:text-[1.625rem]">
        {regionTitle(regionName)}
        <span className="sr-only"> 장소</span>
      </span>
      {counted && <span className="shrink-0 text-sm tabular-nums text-ink-muted">장소 {totalCount(area)}곳</span>}
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
          ? 'bg-ink text-white'
          : compact
            ? 'bg-surface text-ink hover:bg-fill'
            : 'bg-fill text-ink hover:bg-fill-strong',
      ].join(' ')}
    >
      <Icon name="sliders" size={18} />
      {!compact && '조건'}
      {active && <span className="tabular-nums">{count}</span>}
    </button>
  );
}

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
            className={`inline-flex min-h-11 min-w-0 flex-1 items-center justify-center rounded-control px-1 text-sm font-semibold whitespace-nowrap ${
              pressed ? 'bg-ink text-white' : 'bg-fill text-ink hover:bg-fill-strong'
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
      <span className="truncate text-lg font-extrabold tracking-[-0.02em] lg:text-[1.625rem]">
        {regionTitle(regionName)}
        <span className="sr-only"> 베이스캠프</span>
      </span>
      {counted && (
        <span className="shrink-0 text-sm tabular-nums text-ink-muted">
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
          <div key={index} className="flex min-h-16 items-center gap-3 px-4 py-3 lg:px-5">
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
          <ErrorNotice title="장소를 불러오지 못했어요" error={area.error} traceId={area.traceId ?? undefined} />
          <div>
            <Button variant="secondary" onClick={area.retry}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}

      {area.status === 'ready' && area.markers.length === 0 && area.clusters.length > 0 && (
        <div className="px-4 pt-4 lg:px-5">
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
          <p className="flex justify-between px-4 pt-1 pb-2 text-[0.8125rem] text-ink-muted lg:px-6">
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

function NearbyList({ nearby, waitingForMap }: { nearby: SpotsNearby; waitingForMap: boolean }) {
  const showSkeleton = useDelayedFlag((nearby.status === 'loading' && nearby.items.length === 0) || waitingForMap);

  if (showSkeleton) {
    return (
      <div aria-busy="true" className="flex flex-col">
        {[0, 1, 2].map((index) => (
          <div key={index} className="flex min-h-16 items-center gap-3 px-4 py-3 lg:px-5">
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
          <ErrorNotice title="장소를 불러오지 못했어요" error={nearby.error} traceId={nearby.traceId ?? undefined} />
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
            <div role="alert" className="flex flex-col gap-1">
              <p className="text-sm text-danger">{nearby.moreError}</p>
              {nearby.traceId && <p className="font-mono text-xs text-ink-muted">문의 번호 {nearby.traceId}</p>}
            </div>
          )}
          <Button variant="secondary" fullWidth loading={nearby.loadingMore} onClick={nearby.loadMore}>
            더 보기
          </Button>
        </div>
      )}
    </div>
  );
}

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
