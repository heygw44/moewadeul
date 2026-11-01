import { loadKakaoMaps } from './kakaoLoader';
import {
  createClusterButton,
  createDraftPin,
  createSearchPin,
  createSpotDot,
  createSpotPin,
  createSpread,
} from './markers';
import { mergeClusters } from './mergeClusters';
import { arrangeSpread, stackPins } from './stackPins';
import type { SpotPin } from './markers';
import type { PinStack, PlacedPin } from './stackPins';
import type { Bounds, ClusterData, LatLng, MapView, SpotMarkerData } from './types';

const CLUSTER_Z_INDEX = 1;
const PIN_Z_INDEX = 2;
const SELECTED_PIN_Z_INDEX = 3;
const SPREAD_Z_INDEX = 4;
const SEARCH_PIN_Z_INDEX = 5;
const DRAFT_PIN_Z_INDEX = 6;
const MENU_Z_INDEX = 7;
const MENU_MARGIN = 8;

// 카카오가 그린 요소의 구조에 기대므로, 찾지 못하면 아무것도 하지 않는다.
function raiseCopyright(container: HTMLElement) {
  const logo = [...container.querySelectorAll('a')].find((link) => link.href.includes('map.kakao.com'));
  let box = logo?.parentElement ?? null;
  while (box && box !== container && box.style.position !== 'absolute') box = box.parentElement;
  if (box && box !== container) box.style.bottom = 'var(--sheet-top, 0px)';
}

function menuOffset(at: number, size: number, limit: number): number {
  const preferred = at > limit / 2 ? -size : 0;
  const clamped = Math.min(preferred, limit - MENU_MARGIN - size - at);
  return Math.max(clamped, MENU_MARGIN - at);
}

const LONG_PRESS_MS = 500;
const LONG_PRESS_MOVE_TOLERANCE_PX = 10;
// 길게 누른 뒤 손을 떼면 브라우저가 클릭을 하나 더 보낸다. 그 클릭이 방금 연 메뉴를 닫지 않도록 잠깐 무시한다.
const CLICK_SUPPRESS_MS = 600;

const OVERLAY_ATTRIBUTE = 'data-map-overlay';

const CLUSTER_MIN_DISTANCE_PX = 48;
const CLUSTER_MERGE_MAX_PASSES = 4;

const DIMMED_OPACITY = '0.35';

const MAX_ANIMATED_LEVEL_STEP = 2;

const FIT_PADDING_PX = 48;
// 반경 원의 색이다. 숲색 판 토큰(brand)과 같은 값이고, 지도 SDK에는 CSS 변수를 넘길 수 없어서 값을 적는다.
const RADIUS_COLOR = '#2c6a45';

interface DrawnPin {
  overlay: kakao.maps.CustomOverlay;
  pin: SpotPin;
}

interface Spread {
  overlay: kakao.maps.CustomOverlay;
  topId: number;
}

export async function createMapView(
  container: HTMLElement,
  initial: { center: LatLng; level: number; scrollWheel?: boolean; draggable?: boolean; markerStyle?: 'pin' | 'dot' },
): Promise<MapView> {
  await loadKakaoMaps();

  const toKakaoLatLng = (position: LatLng) => new kakao.maps.LatLng(position.lat, position.lng);

  const map = new kakao.maps.Map(container, {
    center: toKakaoLatLng(initial.center),
    level: initial.level,
    scrollwheel: initial.scrollWheel ?? true,
    draggable: initial.draggable ?? true,
    keyboardShortcuts: initial.draggable ?? true,
  });
  map.setCopyrightPosition(kakao.maps.CopyrightPosition.BOTTOMRIGHT, true);
  // 폰 지도 화면은 지도 아래쪽을 시트가 덮는다. 카카오는 로고 자리를 아래쪽으로만 고르게 하므로,
  // 로고 칸을 바깥 칸이 정한 CSS 변수 --sheet-top(시트 윗변 높이)만큼 올려 시트 위에 보이게 한다. 변수가 없으면 맨 아래다.
  raiseCopyright(container);

  const idleListeners = new Set<() => void>();
  const moveStartListeners = new Set<() => void>();
  const markerListeners = new Set<(id: number) => void>();
  const clusterListeners = new Set<(cluster: ClusterData) => void>();
  const mapClickListeners = new Set<(position: LatLng) => void>();
  const contextMenuListeners = new Set<(position: LatLng) => void>();

  const handleIdle = () => {
    if (!destroyed && layoutLevel !== null && map.getLevel() !== layoutLevel) layoutPins();
    for (const listener of [...idleListeners]) listener();
  };
  kakao.maps.event.addListener(map, 'idle', handleIdle);

  const handleMoveStart = () => {
    collapse();
    for (const listener of [...moveStartListeners]) listener();
  };
  kakao.maps.event.addListener(map, 'dragstart', handleMoveStart);
  kakao.maps.event.addListener(map, 'zoom_start', handleMoveStart);

  let suppressClickUntil = 0;

  const handleMapClick = (event: kakao.maps.MouseEvent) => {
    collapse();
    if (performance.now() < suppressClickUntil) return;
    const position = { lat: event.latLng.getLat(), lng: event.latLng.getLng() };
    for (const listener of [...mapClickListeners]) listener(position);
  };
  kakao.maps.event.addListener(map, 'click', handleMapClick);

  const emitContextMenu = (position: LatLng) => {
    for (const listener of [...contextMenuListeners]) listener(position);
  };

  const touchPointers = new Set<number>();
  let press: { pointerId: number; x: number; y: number; timer: number } | null = null;

  const cancelPress = () => {
    if (press) window.clearTimeout(press.timer);
    press = null;
  };

  // 안드로이드 브라우저는 길게 누르면 우클릭 이벤트도 함께 보낸다. 손가락이 닿아 있거나 막 길게 누른 뒤라면
  // 길게 누르기 쪽에서 이미 알렸으므로 우클릭은 버린다.
  const handleRightClick = (event: kakao.maps.MouseEvent) => {
    if (touchPointers.size > 0 || performance.now() < suppressClickUntil) return;
    emitContextMenu({ lat: event.latLng.getLat(), lng: event.latLng.getLng() });
  };
  kakao.maps.event.addListener(map, 'rightclick', handleRightClick);

  const handlePointerDown = (event: PointerEvent) => {
    if (event.pointerType !== 'touch') return;
    touchPointers.add(event.pointerId);
    if (touchPointers.size > 1) {
      cancelPress();
      return;
    }
    if (event.target instanceof Element && event.target.closest(`button, a, input, [${OVERLAY_ATTRIBUTE}]`)) return;
    cancelPress();
    const { pointerId, clientX, clientY } = event;
    const timer = window.setTimeout(() => {
      press = null;
      const rect = container.getBoundingClientRect();
      const point = new kakao.maps.Point(clientX - rect.left, clientY - rect.top);
      const latLng = map.getProjection().coordsFromContainerPoint(point);
      suppressClickUntil = performance.now() + CLICK_SUPPRESS_MS;
      emitContextMenu({ lat: latLng.getLat(), lng: latLng.getLng() });
    }, LONG_PRESS_MS);
    press = { pointerId, x: clientX, y: clientY, timer };
  };

  const handlePointerMove = (event: PointerEvent) => {
    if (!press || event.pointerId !== press.pointerId) return;
    if (Math.hypot(event.clientX - press.x, event.clientY - press.y) > LONG_PRESS_MOVE_TOLERANCE_PX) cancelPress();
  };

  const handlePointerEnd = (event: PointerEvent) => {
    touchPointers.delete(event.pointerId);
    if (press && event.pointerId === press.pointerId) cancelPress();
  };

  const preventNativeMenu = (event: Event) => event.preventDefault();

  // 지도가 SDK 안에서 이벤트를 멈춰도 먼저 받도록 캡처 단계에서 듣는다.
  container.addEventListener('pointerdown', handlePointerDown, true);
  container.addEventListener('pointermove', handlePointerMove, true);
  container.addEventListener('pointerup', handlePointerEnd, true);
  container.addEventListener('pointercancel', handlePointerEnd, true);
  container.addEventListener('contextmenu', preventNativeMenu);
  container.style.setProperty('-webkit-touch-callout', 'none');
  kakao.maps.event.addListener(map, 'dragstart', cancelPress);
  kakao.maps.event.addListener(map, 'zoom_start', cancelPress);

  const handleMarkerClick = (id: number) => {
    for (const listener of [...markerListeners]) listener(id);
  };
  const handleClusterClick = (cluster: ClusterData) => {
    for (const listener of [...clusterListeners]) listener(cluster);
  };

  let markers: SpotMarkerData[] = [];
  let pins = new Map<number, DrawnPin>();
  let spread: Spread | null = null;
  let layoutLevel: number | null = null;
  let clusterOverlays: kakao.maps.CustomOverlay[] = [];
  let clusterElements: HTMLElement[] = [];
  let draftOverlay: kakao.maps.CustomOverlay | null = null;
  let searchOverlay: kakao.maps.CustomOverlay | null = null;
  let menuOverlay: kakao.maps.CustomOverlay | null = null;
  let menuResize: ResizeObserver | null = null;
  let selectedId: number | null = null;
  let selectedSpot: SpotMarkerData | null = null;
  let radiusCircle: kakao.maps.Circle | null = null;
  let terrainOn = false;
  let destroyed = false;

  function mergeOverlappingClusters(clusters: ClusterData[]): ClusterData[] {
    const projection = map.getProjection();
    let merged = clusters;
    for (let pass = 0; pass < CLUSTER_MERGE_MAX_PASSES; pass++) {
      const placed = merged.map((cluster) => {
        const point = projection.containerPointFromCoords(toKakaoLatLng(cluster.position));
        return { cluster, x: point.x, y: point.y };
      });
      const next = mergeClusters(placed, CLUSTER_MIN_DISTANCE_PX);
      if (next.length === merged.length) return next;
      merged = next;
    }
    return merged;
  }

  function collapse(restoreFocus = false) {
    if (spread === null) return;
    spread.overlay.setMap(null);
    const drawn = pins.get(spread.topId);
    spread = null;
    setOthersDimmed(false);
    if (!drawn) return;
    drawn.overlay.setMap(map);
    if (restoreFocus) drawn.pin.element.focus();
  }

  function setOthersDimmed(dimmed: boolean) {
    const opacity = dimmed ? DIMMED_OPACITY : '';
    for (const { pin } of pins.values()) pin.element.style.opacity = opacity;
    for (const element of clusterElements) element.style.opacity = opacity;
  }

  function expand(stack: PinStack) {
    collapse();
    const anchor = stack.top;
    const placements = arrangeSpread(
      stack.members.map((member) => ({
        item: member.marker,
        origin: { x: member.x - anchor.x, y: member.y - anchor.y },
      })),
    );
    const entries = placements.map(({ item, origin, at }) => ({ marker: item, origin, at }));
    const { element, buttons } = createSpread(entries, selectedId, (id) => {
      collapse();
      handleMarkerClick(id);
    });
    element.setAttribute(OVERLAY_ATTRIBUTE, '');
    element.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape') return;
      event.stopPropagation();
      collapse(true);
    });

    const overlay = new kakao.maps.CustomOverlay({
      map,
      content: element,
      position: toKakaoLatLng(anchor.marker.position),
      clickable: true,
      xAnchor: 0,
      yAnchor: 0,
      zIndex: SPREAD_Z_INDEX,
    });
    pins.get(anchor.marker.id)?.overlay.setMap(null);
    spread = { overlay, topId: anchor.marker.id };
    setOthersDimmed(true);
    buttons[0]?.focus();
  }

  function layoutPins() {
    collapse();
    clearPins();
    layoutLevel = map.getLevel();
    const projection = map.getProjection();
    const placed: PlacedPin[] = [];
    const seen = new Set<number>();
    for (const marker of markers) {
      if (seen.has(marker.id)) continue;
      seen.add(marker.id);
      const point = projection.containerPointFromCoords(toKakaoLatLng(marker.position));
      placed.push({ marker, x: point.x, y: point.y });
    }
    if (selectedSpot !== null && selectedSpot.id === selectedId && !seen.has(selectedSpot.id)) {
      const point = projection.containerPointFromCoords(toKakaoLatLng(selectedSpot.position));
      placed.push({ marker: selectedSpot, x: point.x, y: point.y });
    }

    if (initial.markerStyle === 'dot') {
      for (const { marker } of placed) {
        const pin = createSpotDot(marker, handleMarkerClick);
        const selected = marker.id === selectedId;
        pin.setSelected(selected);
        const overlay = new kakao.maps.CustomOverlay({
          map,
          content: pin.element,
          position: toKakaoLatLng(marker.position),
          clickable: true,
          xAnchor: 0.5,
          yAnchor: 0.5,
          zIndex: selected ? SELECTED_PIN_Z_INDEX : PIN_Z_INDEX,
        });
        pins.set(marker.id, { overlay, pin });
      }
      return;
    }

    for (const stack of stackPins(placed, selectedId)) {
      const top = stack.top.marker;
      const stackedCount = stack.members.length - 1;
      const pin = createSpotPin(top, stackedCount > 0 ? () => expand(stack) : handleMarkerClick, stackedCount);
      const selected = top.id === selectedId;
      pin.setSelected(selected);
      const overlay = new kakao.maps.CustomOverlay({
        map,
        content: pin.element,
        position: toKakaoLatLng(top.position),
        clickable: true,
        xAnchor: 0.5,
        yAnchor: 1,
        zIndex: selected ? SELECTED_PIN_Z_INDEX : PIN_Z_INDEX,
      });
      pins.set(top.id, { overlay, pin });
    }
  }

  function clearPins() {
    for (const { overlay } of pins.values()) overlay.setMap(null);
    pins = new Map();
  }

  function clearClusters() {
    for (const overlay of clusterOverlays) overlay.setMap(null);
    clusterOverlays = [];
    clusterElements = [];
  }

  function subscribe<T>(listeners: Set<T>, listener: T): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }

  const view: MapView = {
    getBounds(): Bounds {
      const bounds = map.getBounds();
      const sw = bounds.getSouthWest();
      const ne = bounds.getNorthEast();
      return { sw: { lat: sw.getLat(), lng: sw.getLng() }, ne: { lat: ne.getLat(), lng: ne.getLng() } };
    },

    getLevel() {
      return map.getLevel();
    },

    getCenter(): LatLng {
      const center = map.getCenter();
      return { lat: center.getLat(), lng: center.getLng() };
    },

    getVisibleCenter(visibleHeight?: number): LatLng {
      const height = Math.min(visibleHeight ?? container.clientHeight, container.clientHeight);
      const point = new kakao.maps.Point(container.clientWidth / 2, height / 2);
      const position = map.getProjection().coordsFromContainerPoint(point);
      return { lat: position.getLat(), lng: position.getLng() };
    },

    setCenter(position: LatLng, level?: number) {
      const current = map.getLevel();
      if (level === undefined || level === current) {
        map.setCenter(toKakaoLatLng(position));
        return;
      }
      // 카카오맵은 두 단계 이하로 바꿀 때만 확대·축소를 애니메이션으로 보여 준다.
      // 그보다 크게 바꿀 때 넓은 축척에서 먼저 중심을 잡으면 화면 한 점이 수백 m라서, 확대한 뒤 목표가 가운데에서 벗어난다.
      // 그래서 크게 바꿀 때는 확대 수준을 먼저 바꾸고 중심을 잡는다.
      if (Math.abs(level - current) > MAX_ANIMATED_LEVEL_STEP) {
        map.setLevel(level);
        map.setCenter(toKakaoLatLng(position));
        return;
      }
      map.setCenter(toKakaoLatLng(position));
      map.setLevel(level, { animate: true });
    },

    panTo(position: LatLng, level?: number) {
      const current = map.getLevel();
      if (level !== undefined && Math.abs(level - current) > MAX_ANIMATED_LEVEL_STEP) {
        view.setCenter(position, level);
        return;
      }
      map.panTo(toKakaoLatLng(position));
      if (level !== undefined && level !== current)
        map.setLevel(level, { animate: true, anchor: toKakaoLatLng(position) });
    },

    showMarkers(nextMarkers: SpotMarkerData[], clusters: ClusterData[]) {
      if (destroyed) return;
      markers = nextMarkers;
      layoutPins();
      clearClusters();

      for (const cluster of mergeOverlappingClusters(clusters)) {
        const element = createClusterButton(cluster, handleClusterClick);
        clusterElements.push(element);
        clusterOverlays.push(
          new kakao.maps.CustomOverlay({
            map,
            content: element,
            position: toKakaoLatLng(cluster.position),
            clickable: true,
            xAnchor: 0.5,
            yAnchor: 0.5,
            zIndex: CLUSTER_Z_INDEX,
          }),
        );
      }
    },

    setSelected(id: number | null) {
      if (id === selectedId) return;
      selectedId = id;
      if (!destroyed) layoutPins();
    },

    showSelectedSpot(marker: SpotMarkerData | null) {
      if (marker === selectedSpot) return;
      selectedSpot = marker;
      if (!destroyed) layoutPins();
    },

    onIdle(listener: () => void) {
      return subscribe(idleListeners, listener);
    },

    onMoveStart(listener: () => void) {
      return subscribe(moveStartListeners, listener);
    },

    onMarkerClick(listener: (id: number) => void) {
      return subscribe(markerListeners, listener);
    },

    onClusterClick(listener: (cluster: ClusterData) => void) {
      return subscribe(clusterListeners, listener);
    },

    onMapClick(listener: (position: LatLng) => void) {
      return subscribe(mapClickListeners, listener);
    },

    onContextMenu(listener: (position: LatLng) => void) {
      return subscribe(contextMenuListeners, listener);
    },

    // SDK는 오버레이를 만들 때 내용의 크기로 기준점을 계산하는데, 그때는 화면 코드가 아직 내용을 그리기 전이라 크기가 0이다.
    // 그래서 기준점은 왼쪽 위로 두고, 펼칠 방향은 내용 요소를 CSS로 옮겨서 정한다. 열 때마다 새로 만든다.
    showMenu(position: LatLng | null, visibleHeight?: number) {
      menuOverlay?.setMap(null);
      menuOverlay = null;
      menuResize?.disconnect();
      menuResize = null;
      if (destroyed || position === null) return null;
      const kakaoPosition = toKakaoLatLng(position);
      const point = map.getProjection().containerPointFromCoords(kakaoPosition);
      const element = document.createElement('div');
      element.setAttribute(OVERLAY_ATTRIBUTE, '');
      const place = () => {
        const height = Math.min(visibleHeight ?? container.clientHeight, container.clientHeight);
        const x = menuOffset(point.x, element.offsetWidth, container.clientWidth);
        const y = menuOffset(point.y, element.offsetHeight, height);
        element.style.transform = `translate(${x}px, ${y}px)`;
      };
      place();
      menuResize = new ResizeObserver(place);
      menuResize.observe(element);
      menuOverlay = new kakao.maps.CustomOverlay({
        map,
        content: element,
        position: kakaoPosition,
        clickable: true,
        xAnchor: 0,
        yAnchor: 0,
        zIndex: MENU_Z_INDEX,
      });
      return element;
    },

    showDraft(position: LatLng | null) {
      if (destroyed) return;
      if (position === null) {
        draftOverlay?.setMap(null);
        return;
      }
      if (draftOverlay === null) {
        draftOverlay = new kakao.maps.CustomOverlay({
          content: createDraftPin(),
          position: toKakaoLatLng(position),
          clickable: false,
          xAnchor: 0.5,
          yAnchor: 1,
          zIndex: DRAFT_PIN_Z_INDEX,
        });
      } else {
        draftOverlay.setPosition(toKakaoLatLng(position));
      }
      draftOverlay.setMap(map);
    },

    showSearchPin(position: LatLng | null) {
      if (destroyed) return;
      if (position === null) {
        searchOverlay?.setMap(null);
        return;
      }
      if (searchOverlay === null) {
        searchOverlay = new kakao.maps.CustomOverlay({
          content: createSearchPin(),
          position: toKakaoLatLng(position),
          clickable: false,
          xAnchor: 0.5,
          yAnchor: 1,
          zIndex: SEARCH_PIN_Z_INDEX,
        });
      } else {
        searchOverlay.setPosition(toKakaoLatLng(position));
      }
      searchOverlay.setMap(map);
    },

    fitBounds(bounds: Bounds) {
      if (destroyed) return;
      const area = new kakao.maps.LatLngBounds(toKakaoLatLng(bounds.sw), toKakaoLatLng(bounds.ne));
      map.setBounds(area, FIT_PADDING_PX, FIT_PADDING_PX, FIT_PADDING_PX, FIT_PADDING_PX);
    },

    showRadius(area: { center: LatLng; radiusKm: number } | null) {
      if (destroyed) return;
      if (area === null) {
        radiusCircle?.setMap(null);
        return;
      }
      const options = { center: toKakaoLatLng(area.center), radius: area.radiusKm * 1000 };
      if (radiusCircle === null) {
        radiusCircle = new kakao.maps.Circle({
          ...options,
          strokeWeight: 1,
          strokeColor: RADIUS_COLOR,
          strokeOpacity: 0.8,
          fillColor: RADIUS_COLOR,
          fillOpacity: 0.08,
          zIndex: 0,
        });
        // 원은 눌림을 가로채므로, 원 안을 누르거나 우클릭해도 지도를 누른 것과 같게 넘긴다.
        kakao.maps.event.addListener(radiusCircle, 'click', handleMapClick);
        kakao.maps.event.addListener(radiusCircle, 'rightclick', handleRightClick);
      } else {
        radiusCircle.setOptions(options);
      }
      radiusCircle.setMap(map);
    },

    setTerrain(on: boolean) {
      if (destroyed || on === terrainOn) return;
      if (on) {
        map.addOverlayMapTypeId(kakao.maps.MapTypeId.TERRAIN);
      } else {
        map.removeOverlayMapTypeId(kakao.maps.MapTypeId.TERRAIN);
      }
      terrainOn = on;
    },

    relayout() {
      if (!destroyed) map.relayout();
    },

    destroy() {
      if (destroyed) return;
      destroyed = true;
      kakao.maps.event.removeListener(map, 'idle', handleIdle);
      kakao.maps.event.removeListener(map, 'click', handleMapClick);
      kakao.maps.event.removeListener(map, 'dragstart', handleMoveStart);
      kakao.maps.event.removeListener(map, 'zoom_start', handleMoveStart);
      kakao.maps.event.removeListener(map, 'rightclick', handleRightClick);
      kakao.maps.event.removeListener(map, 'dragstart', cancelPress);
      kakao.maps.event.removeListener(map, 'zoom_start', cancelPress);
      container.removeEventListener('pointerdown', handlePointerDown, true);
      container.removeEventListener('pointermove', handlePointerMove, true);
      container.removeEventListener('pointerup', handlePointerEnd, true);
      container.removeEventListener('pointercancel', handlePointerEnd, true);
      container.removeEventListener('contextmenu', preventNativeMenu);
      cancelPress();
      touchPointers.clear();
      collapse();
      clearPins();
      clearClusters();
      draftOverlay?.setMap(null);
      draftOverlay = null;
      searchOverlay?.setMap(null);
      searchOverlay = null;
      if (radiusCircle) {
        kakao.maps.event.removeListener(radiusCircle, 'click', handleMapClick);
        kakao.maps.event.removeListener(radiusCircle, 'rightclick', handleRightClick);
        radiusCircle.setMap(null);
      }
      radiusCircle = null;
      menuOverlay?.setMap(null);
      menuOverlay = null;
      menuResize?.disconnect();
      menuResize = null;
      idleListeners.clear();
      moveStartListeners.clear();
      markerListeners.clear();
      clusterListeners.clear();
      mapClickListeners.clear();
      contextMenuListeners.clear();
    },
  };
  return view;
}
