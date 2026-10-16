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

// 선택한 핀이 다른 핀과 묶음 위로, 펼친 핀은 모든 장소 핀 위로 올라오게 겹침 순서를 둔다.
const CLUSTER_Z_INDEX = 1;
const PIN_Z_INDEX = 2;
const SELECTED_PIN_Z_INDEX = 3;
const SPREAD_Z_INDEX = 4;
const SEARCH_PIN_Z_INDEX = 5;
const DRAFT_PIN_Z_INDEX = 6;
const MENU_Z_INDEX = 7;
// 메뉴를 지도 가장자리에서 띄울 거리(px)다.
const MENU_MARGIN = 8;

// 카카오 로고 링크에서 위로 올라가며 절대 위치로 놓인 저작권 칸을 찾아 아래 여백을 바꾼다.
// 카카오가 그린 요소의 구조에 기대므로, 찾지 못하면 아무것도 하지 않는다.
function raiseCopyright(container: HTMLElement) {
  const logo = [...container.querySelectorAll('a')].find((link) => link.href.includes('map.kakao.com'));
  let box = logo?.parentElement ?? null;
  while (box && box !== container && box.style.position !== 'absolute') box = box.parentElement;
  if (box && box !== container) box.style.bottom = 'var(--sheet-top, 0px)';
}

// 누른 점에서 메뉴를 얼마나 옮길지(px) 정한다. 누른 점이 뒤쪽 절반이면 앞쪽으로 펼치고,
// 그래도 가장자리를 넘으면 안쪽으로 밀어 넣는다. 메뉴가 영역보다 크면 앞쪽 끝을 맞춘다.
function menuOffset(at: number, size: number, limit: number): number {
  const preferred = at > limit / 2 ? -size : 0;
  const clamped = Math.min(preferred, limit - MENU_MARGIN - size - at);
  return Math.max(clamped, MENU_MARGIN - at);
}

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

// 겹친 핀을 펼친 동안 나머지 핀과 묶음을 이만큼 흐리게 해서, 어디까지가 펼친 핀인지 드러낸다.
const DIMMED_OPACITY = '0.35';

const MAX_ANIMATED_LEVEL_STEP = 2;

// 영역에 맞출 때 장소가 화면 가장자리에 붙지 않도록 둘레에 두는 여백(px)이다.
const FIT_PADDING_PX = 48;
// 반경 원의 색이다. 숲색 판 토큰(brand)과 같은 값이고, 지도 SDK에는 CSS 변수를 넘길 수 없어서 값을 적는다.
const RADIUS_COLOR = '#1d3b2c';

interface DrawnPin {
  overlay: kakao.maps.CustomOverlay;
  pin: SpotPin;
}

interface Spread {
  overlay: kakao.maps.CustomOverlay;
  // 펼치는 동안 숨긴 맨 위 핀의 id다.
  topId: number;
}

export async function createMapView(
  container: HTMLElement,
  // scrollWheel을 끄면 마우스 휠이 지도를 확대·축소하지 않고 페이지를 스크롤한다. 랜딩처럼 지도가 페이지 안에 있을 때 끈다.
  // markerStyle이 'dot'이면 장소를 유형별 핀 대신 작은 점으로 그리고, 겹친 점을 묶지 않는다.
  // draggable을 끄면 지도를 끌어 옮길 수 없다. 랜딩처럼 화면에 고정한 지도에서 터치 스크롤이 지도에 먹히지 않게 할 때 끈다.
  initial: { center: LatLng; level: number; scrollWheel?: boolean; draggable?: boolean; markerStyle?: 'pin' | 'dot' },
): Promise<MapView> {
  await loadKakaoMaps();

  const toKakaoLatLng = (position: LatLng) => new kakao.maps.LatLng(position.lat, position.lng);

  const map = new kakao.maps.Map(container, {
    center: toKakaoLatLng(initial.center),
    level: initial.level,
    scrollwheel: initial.scrollWheel ?? true,
    draggable: initial.draggable ?? true,
  });
  // 왼쪽 아래에는 지도 범례를 두므로, 카카오 로고와 저작권 표시는 오른쪽 아래로 옮긴다.
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

  // 구독 해제가 반복 중에 일어나도 이번 알림은 모두 받도록 복사본을 돈다.
  // 확대 수준이 바뀌면 핀 사이의 화면 거리가 바뀌므로 다시 묶는다. 끌기만 했으면 거리가 그대로라 다시 묶지 않는다.
  const handleIdle = () => {
    if (!destroyed && layoutLevel !== null && map.getLevel() !== layoutLevel) layoutPins();
    for (const listener of [...idleListeners]) listener();
  };
  kakao.maps.event.addListener(map, 'idle', handleIdle);

  // 지도를 끌거나 확대·축소하면 펼친 핀을 접는다. 펼친 위치는 그때의 화면 거리로 계산한 것이라 움직이면 맞지 않는다.
  const handleMoveStart = () => {
    collapse();
    for (const listener of [...moveStartListeners]) listener();
  };
  kakao.maps.event.addListener(map, 'dragstart', handleMoveStart);
  kakao.maps.event.addListener(map, 'zoom_start', handleMoveStart);

  // 길게 누른 직후에 오는 클릭은 이 시각까지 무시한다.
  let suppressClickUntil = 0;

  // 핀과 묶음은 clickable 오버레이라서 그 위를 누른 클릭은 지도 이벤트로 오지 않는다.
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

  let markers: SpotMarkerData[] = [];
  // 맨 위 핀의 id로 찾는다. 겹쳐서 가려진 핀은 따로 그리지 않는다.
  let pins = new Map<number, DrawnPin>();
  let spread: Spread | null = null;
  // 마지막으로 핀을 묶은 때의 확대 수준이다.
  let layoutLevel: number | null = null;
  let clusterOverlays: kakao.maps.CustomOverlay[] = [];
  let clusterElements: HTMLElement[] = [];
  let draftOverlay: kakao.maps.CustomOverlay | null = null;
  let searchOverlay: kakao.maps.CustomOverlay | null = null;
  let menuOverlay: kakao.maps.CustomOverlay | null = null;
  let menuResize: ResizeObserver | null = null;
  let selectedId: number | null = null;
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

  // 펼친 핀을 지우고 숨겼던 맨 위 핀을 다시 보인다. 키보드로 접었으면 포커스를 그 핀으로 돌려놓는다.
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

  // 겹친 핀을 원래 위치에서 펼친다. 지도는 확대하지 않는다.
  function expand(stack: PinStack) {
    collapse();
    const anchor = stack.top;
    // 화면 좌표는 묶을 때 구한 값이다. 그 뒤로 지도를 끌었더라도 핀 사이의 거리는 그대로라 기준점과의 차이만 쓴다.
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
      // 내용 요소의 크기가 0이라 기준점은 왼쪽 위로 두고, 안의 핀과 선을 이 점에서 떨어진 거리로 놓는다.
      xAnchor: 0,
      yAnchor: 0,
      zIndex: SPREAD_Z_INDEX,
    });
    pins.get(anchor.marker.id)?.overlay.setMap(null);
    spread = { overlay, topId: anchor.marker.id };
    setOthersDimmed(true);
    buttons[0]?.focus();
  }

  // 지금 확대 수준의 화면 좌표로 핀을 묶고, 묶음마다 맨 위 핀 하나만 그린다.
  function layoutPins() {
    collapse();
    clearPins();
    layoutLevel = map.getLevel();
    const projection = map.getProjection();
    const placed: PlacedPin[] = [];
    const seen = new Set<number>();
    for (const marker of markers) {
      // 같은 id가 두 번 오면 앞의 오버레이를 잃어버려 지울 수 없게 되므로 처음 것만 그린다.
      if (seen.has(marker.id)) continue;
      seen.add(marker.id);
      const point = projection.containerPointFromCoords(toKakaoLatLng(marker.position));
      placed.push({ marker, x: point.x, y: point.y });
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
        // 핀의 뾰족한 끝이 좌표에 오도록 요소 아래쪽 끝을 기준으로 놓는다.
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
        // 확대 수준을 크게 바꾸면 애니메이션이 없으므로 setCenter와 같이 바로 옮긴다.
        view.setCenter(position, level);
        return;
      }
      map.panTo(toKakaoLatLng(position));
      if (level !== undefined && level !== current)
        map.setLevel(level, { animate: true, anchor: toKakaoLatLng(position) });
    },

    // 서버 묶음은 핀과 섞어 묶지 않는다. 묶음끼리만 합쳐서 그린다.
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

    // 고른 핀은 겹친 핀 묶음의 맨 위로 올라와야 하므로 핀을 다시 묶는다.
    // 지금 화면에 없는 id라도 기억해 두었다가, 다음 showMarkers에서 그 핀을 선택 상태로 그린다.
    setSelected(id: number | null) {
      if (id === selectedId) return;
      selectedId = id;
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

    // 메뉴가 지도 밖으로 넘치지 않게, 누른 곳이 화면 오른쪽 절반이면 메뉴를 왼쪽으로, 아래쪽 절반이면 위로 펼친다.
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
      // 메뉴 내용은 화면 코드가 나중에 그려 넣으므로, 크기가 정해질 때마다 다시 자리를 잡는다.
      // 폰에서는 메뉴가 지도 폭보다 넓을 수 있고 지도 아래쪽은 시트가 덮으므로, 보이는 영역 안으로 밀어 넣는다.
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

    fitBounds(bounds: Bounds) {
      if (destroyed) return;
      const area = new kakao.maps.LatLngBounds(toKakaoLatLng(bounds.sw), toKakaoLatLng(bounds.ne));
      map.setBounds(area, FIT_PADDING_PX, FIT_PADDING_PX, FIT_PADDING_PX, FIT_PADDING_PX);
    },

    // 원은 하나만 만들어 두고 중심과 반지름만 바꾼다.
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
      collapse();
      clearPins();
      clearClusters();
      draftOverlay?.setMap(null);
      draftOverlay = null;
      searchOverlay?.setMap(null);
      searchOverlay = null;
      radiusCircle?.setMap(null);
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
