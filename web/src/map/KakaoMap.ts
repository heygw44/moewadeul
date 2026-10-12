import { loadKakaoMaps } from './kakaoLoader';
import { createClusterButton, createDraftPin, createSearchPin, createSpotPin } from './markers';
import { mergeClusters } from './mergeClusters';
import type { SpotPin } from './markers';
import type { Bounds, ClusterData, LatLng, MapView, SpotMarkerData } from './types';

// 선택한 핀이 다른 핀과 묶음 위로 올라오게 겹침 순서를 둔다.
const CLUSTER_Z_INDEX = 1;
const PIN_Z_INDEX = 2;
const SELECTED_PIN_Z_INDEX = 3;
const SEARCH_PIN_Z_INDEX = 4;
const DRAFT_PIN_Z_INDEX = 5;
const MENU_Z_INDEX = 6;

// 휴대폰에는 우클릭이 없어서, 지도를 움직이지 않고 이만큼 누르고 있으면 우클릭과 같이 본다.
const LONG_PRESS_MS = 500;
// 손가락은 가만히 대고 있어도 조금씩 흔들리므로, 이 거리 안의 움직임은 지도를 끈 것으로 보지 않는다.
const LONG_PRESS_MOVE_TOLERANCE_PX = 10;
// 길게 누른 뒤 손을 떼면 브라우저가 클릭을 하나 더 보낸다. 그 클릭이 방금 연 메뉴를 닫지 않도록 잠깐 무시한다.
const CLICK_SUPPRESS_MS = 600;

// 지도 위 오버레이 요소에 붙이는 표시다. 메뉴 안을 길게 눌렀을 때 메뉴를 다시 열지 않으려고 쓴다.
const OVERLAY_ATTRIBUTE = 'data-map-overlay';

// 묶음 원의 지름이 44px이라, 중심이 이보다 가까우면 원이 겹치거나 맞붙어 숫자를 읽기 어렵다.
const CLUSTER_MIN_DISTANCE_PX = 48;
// 합친 위치가 가중 평균으로 옮겨지면 이웃 묶음과 다시 가까워질 수 있어서, 더 합칠 것이 없을 때까지 몇 번 되풀이한다.
const CLUSTER_MERGE_MAX_PASSES = 4;

const MAX_ANIMATED_LEVEL_STEP = 2;

interface DrawnPin {
  overlay: kakao.maps.CustomOverlay;
  pin: SpotPin;
}

export async function createMapView(
  container: HTMLElement,
  initial: { center: LatLng; level: number },
): Promise<MapView> {
  await loadKakaoMaps();

  const toKakaoLatLng = (position: LatLng) => new kakao.maps.LatLng(position.lat, position.lng);

  const map = new kakao.maps.Map(container, { center: toKakaoLatLng(initial.center), level: initial.level });
  // 왼쪽 아래에는 지도 범례를 두므로, 카카오 로고와 저작권 표시는 오른쪽 아래로 옮긴다.
  map.setCopyrightPosition(kakao.maps.CopyrightPosition.BOTTOMRIGHT, true);

  const idleListeners = new Set<() => void>();
  const moveStartListeners = new Set<() => void>();
  const markerListeners = new Set<(id: number) => void>();
  const clusterListeners = new Set<(cluster: ClusterData) => void>();
  const mapClickListeners = new Set<(position: LatLng) => void>();
  const contextMenuListeners = new Set<(position: LatLng) => void>();

  // 구독 해제가 반복 중에 일어나도 이번 알림은 모두 받도록 복사본을 돈다.
  const handleIdle = () => {
    for (const listener of [...idleListeners]) listener();
  };
  kakao.maps.event.addListener(map, 'idle', handleIdle);

  const handleMoveStart = () => {
    for (const listener of [...moveStartListeners]) listener();
  };
  kakao.maps.event.addListener(map, 'dragstart', handleMoveStart);
  kakao.maps.event.addListener(map, 'zoom_start', handleMoveStart);

  // 길게 누른 직후에 오는 클릭은 이 시각까지 무시한다.
  let suppressClickUntil = 0;

  // 핀과 묶음은 clickable 오버레이라서 그 위를 누른 클릭은 지도 이벤트로 오지 않는다.
  const handleMapClick = (event: kakao.maps.MouseEvent) => {
    if (performance.now() < suppressClickUntil) return;
    const position = { lat: event.latLng.getLat(), lng: event.latLng.getLng() };
    for (const listener of [...mapClickListeners]) listener(position);
  };
  kakao.maps.event.addListener(map, 'click', handleMapClick);

  const emitContextMenu = (position: LatLng) => {
    for (const listener of [...contextMenuListeners]) listener(position);
  };

  // 화면에 닿아 있는 손가락이다. 두 손가락이면 확대·축소라서 길게 누르기로 보지 않는다.
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
    // 핀, 묶음, 메뉴처럼 누를 수 있는 오버레이 위에서는 길게 누르기를 세지 않는다.
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

  // 브라우저의 우클릭 메뉴와 iOS의 길게 누르기 메뉴가 지도 메뉴를 가리지 않게 막는다.
  const preventNativeMenu = (event: Event) => event.preventDefault();

  // 지도가 SDK 안에서 이벤트를 멈춰도 먼저 받도록 캡처 단계에서 듣는다.
  container.addEventListener('pointerdown', handlePointerDown, true);
  container.addEventListener('pointermove', handlePointerMove, true);
  container.addEventListener('pointerup', handlePointerEnd, true);
  container.addEventListener('pointercancel', handlePointerEnd, true);
  container.addEventListener('contextmenu', preventNativeMenu);
  container.style.setProperty('-webkit-touch-callout', 'none');
  // 지도를 끌거나 확대·축소하기 시작하면 길게 누르기가 아니다.
  kakao.maps.event.addListener(map, 'dragstart', cancelPress);
  kakao.maps.event.addListener(map, 'zoom_start', cancelPress);

  const handleMarkerClick = (id: number) => {
    for (const listener of [...markerListeners]) listener(id);
  };
  const handleClusterClick = (cluster: ClusterData) => {
    for (const listener of [...clusterListeners]) listener(cluster);
  };

  let pins = new Map<number, DrawnPin>();
  let clusterOverlays: kakao.maps.CustomOverlay[] = [];
  let draftOverlay: kakao.maps.CustomOverlay | null = null;
  let searchOverlay: kakao.maps.CustomOverlay | null = null;
  let menuOverlay: kakao.maps.CustomOverlay | null = null;
  let selectedId: number | null = null;
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

  function clearOverlays() {
    for (const { overlay } of pins.values()) overlay.setMap(null);
    for (const overlay of clusterOverlays) overlay.setMap(null);
    pins = new Map();
    clusterOverlays = [];
  }

  function subscribe<T>(listeners: Set<T>, listener: T): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }

  return {
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

    showMarkers(markers: SpotMarkerData[], clusters: ClusterData[]) {
      if (destroyed) return;
      clearOverlays();

      for (const marker of markers) {
        // 같은 id가 두 번 오면 앞의 오버레이를 잃어버려 지울 수 없게 되므로 처음 것만 그린다.
        if (pins.has(marker.id)) continue;
        const pin = createSpotPin(marker, handleMarkerClick);
        const selected = marker.id === selectedId;
        pin.setSelected(selected);
        const overlay = new kakao.maps.CustomOverlay({
          map,
          content: pin.element,
          position: toKakaoLatLng(marker.position),
          clickable: true,
          xAnchor: 0.5,
          // 핀의 뾰족한 끝이 좌표에 오도록 요소 아래쪽 끝을 기준으로 놓는다.
          yAnchor: 1,
          zIndex: selected ? SELECTED_PIN_Z_INDEX : PIN_Z_INDEX,
        });
        pins.set(marker.id, { overlay, pin });
      }

      for (const cluster of mergeOverlappingClusters(clusters)) {
        clusterOverlays.push(
          new kakao.maps.CustomOverlay({
            map,
            content: createClusterButton(cluster, handleClusterClick),
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
      const previous = selectedId === null ? undefined : pins.get(selectedId);
      if (previous) {
        previous.pin.setSelected(false);
        previous.overlay.setZIndex(PIN_Z_INDEX);
      }
      // 지금 화면에 없는 id라도 기억해 두었다가, 다음 showMarkers에서 그 핀을 선택 상태로 그린다.
      selectedId = id;
      const next = id === null ? undefined : pins.get(id);
      if (next) {
        next.pin.setSelected(true);
        next.overlay.setZIndex(SELECTED_PIN_Z_INDEX);
      }
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

    // 메뉴가 지도 밖으로 넘치지 않게, 누른 곳이 화면 오른쪽 절반이면 메뉴를 왼쪽으로, 아래쪽 절반이면 위로 펼친다.
    // SDK는 오버레이를 만들 때 내용의 크기로 기준점을 계산하는데, 그때는 화면 코드가 아직 내용을 그리기 전이라 크기가 0이다.
    // 그래서 기준점은 왼쪽 위로 두고, 펼칠 방향은 내용 요소를 CSS로 옮겨서 정한다. 열 때마다 새로 만든다.
    showMenu(position: LatLng | null) {
      menuOverlay?.setMap(null);
      menuOverlay = null;
      if (destroyed || position === null) return null;
      const kakaoPosition = toKakaoLatLng(position);
      const point = map.getProjection().containerPointFromCoords(kakaoPosition);
      const element = document.createElement('div');
      element.setAttribute(OVERLAY_ATTRIBUTE, '');
      const shiftX = point.x > container.clientWidth / 2 ? '-100%' : '0';
      const shiftY = point.y > container.clientHeight / 2 ? '-100%' : '0';
      element.style.transform = `translate(${shiftX}, ${shiftY})`;
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

    // 임시 핀은 오버레이 하나를 만들어 두고 위치만 옮긴다.
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

    // 검색 위치 핀도 오버레이 하나를 만들어 두고 위치만 옮긴다.
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

    // 지형도는 기본 지도 위에 겹치는 타일이다. 이미 켜져 있으면 다시 겹치지 않는다.
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
      map.relayout();
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
      clearOverlays();
      draftOverlay?.setMap(null);
      draftOverlay = null;
      searchOverlay?.setMap(null);
      searchOverlay = null;
      menuOverlay?.setMap(null);
      menuOverlay = null;
      idleListeners.clear();
      moveStartListeners.clear();
      markerListeners.clear();
      clusterListeners.clear();
      mapClickListeners.clear();
      contextMenuListeners.clear();
    },
  };
}
