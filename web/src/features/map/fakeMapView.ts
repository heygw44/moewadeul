import { vi } from 'vitest';
import type { Mock } from 'vitest';
import type { Bounds, LatLng, MapView } from '../../map/types';

type Listeners<T extends unknown[] = []> = Set<(...args: T) => void>;

export type FakeMapView = MapView & {
  emit: {
    idle: () => void;
    moveStart: () => void;
    markerClick: (id: number) => void;
    mapClick: (position: LatLng) => void;
    contextMenu: (position: LatLng) => void;
  };
  menuHost: HTMLElement;
  level: number;
  bounds: Bounds;
  showMenu: Mock<MapView['showMenu']>;
  showSearchPin: Mock<MapView['showSearchPin']>;
  showDraft: Mock<MapView['showDraft']>;
  showSelectedSpot: Mock<MapView['showSelectedSpot']>;
  setCenter: Mock<MapView['setCenter']>;
  panTo: Mock<MapView['panTo']>;
  relayout: Mock<MapView['relayout']>;
  destroy: Mock<MapView['destroy']>;
};

function subscribe<T extends unknown[]>(listeners: Listeners<T>, listener: (...args: T) => void) {
  listeners.add(listener);
  return () => void listeners.delete(listener);
}

// 줌 레벨이 바뀌는 setCenter는 애니메이션하는 실제 지도처럼 호출이 끝난 뒤에 움직임 시작을 알리고, 그때까지 getLevel은 옛 레벨이다.
export function createFakeMapView(initialLevel = 12): FakeMapView {
  const idle: Listeners = new Set();
  const moveStart: Listeners = new Set();
  const markerClick: Listeners<[number]> = new Set();
  const mapClick: Listeners<[LatLng]> = new Set();
  const contextMenu: Listeners<[LatLng]> = new Set();
  let center: LatLng = { lat: 36.35, lng: 127.85 };
  const menuHost = document.createElement('div');

  const view: FakeMapView = {
    level: initialLevel,
    bounds: { sw: { lat: 36, lng: 127 }, ne: { lat: 37, lng: 128 } },
    menuHost,
    emit: {
      idle: () => idle.forEach((listener) => listener()),
      moveStart: () => moveStart.forEach((listener) => listener()),
      markerClick: (id) => markerClick.forEach((listener) => listener(id)),
      mapClick: (position) => mapClick.forEach((listener) => listener(position)),
      contextMenu: (position) => contextMenu.forEach((listener) => listener(position)),
    },
    getBounds: () => view.bounds,
    getLevel: () => view.level,
    getCenter: () => center,
    getVisibleCenter: () => center,
    setCenter: vi.fn((position: LatLng, level?: number) => {
      center = position;
      if (level !== undefined && level !== view.level) {
        queueMicrotask(() => {
          view.emit.moveStart();
          view.level = level;
          view.emit.idle();
        });
      }
    }),
    panTo: vi.fn(),
    showMarkers: vi.fn(),
    setSelected: vi.fn(),
    showSelectedSpot: vi.fn(),
    onIdle: (listener) => subscribe(idle, listener),
    onMoveStart: (listener) => subscribe(moveStart, listener),
    onMarkerClick: (listener) => subscribe(markerClick, listener),
    onClusterClick: () => () => {},
    onMapClick: (listener) => subscribe(mapClick, listener),
    onContextMenu: (listener) => subscribe(contextMenu, listener),
    showMenu: vi.fn((position) => (position === null ? null : menuHost)),
    showDraft: vi.fn(),
    showSearchPin: vi.fn(),
    fitBounds: vi.fn(),
    showRadius: vi.fn(),
    setTerrain: vi.fn(),
    relayout: vi.fn(),
    destroy: vi.fn(),
  };
  return view;
}
