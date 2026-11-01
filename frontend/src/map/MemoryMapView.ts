import { loadKakaoMaps } from './kakaoLoader';
import { createPhotoPin } from './markers';
import type { PhotoPin } from './markers';
import type { LatLng, MemoryMapView, MemoryPinData } from './types';

const DEFAULT_VIEW = { center: { lat: 36.35, lng: 127.85 }, level: 12 };
const SINGLE_PIN_LEVEL = 6;
const FIT_PADDING_PX = 48;
const PIN_Z_INDEX = 1;
const SELECTED_PIN_Z_INDEX = 2;

interface DrawnPin {
  overlay: kakao.maps.CustomOverlay;
  pin: PhotoPin;
}

export async function createMemoryMapView(container: HTMLElement): Promise<MemoryMapView> {
  await loadKakaoMaps();

  const toKakaoLatLng = (position: LatLng) => new kakao.maps.LatLng(position.lat, position.lng);
  const map = new kakao.maps.Map(container, {
    center: toKakaoLatLng(DEFAULT_VIEW.center),
    level: DEFAULT_VIEW.level,
  });
  map.setCopyrightPosition(kakao.maps.CopyrightPosition.BOTTOMRIGHT, true);

  const listeners = new Set<(id: number) => void>();
  let drawn = new Map<number, DrawnPin>();
  let positions: LatLng[] = [];
  let selectedId: number | null = null;
  let destroyed = false;

  function clearPins() {
    for (const { overlay } of drawn.values()) overlay.setMap(null);
    drawn = new Map();
  }

  function applySelection() {
    for (const [id, { overlay, pin }] of drawn) {
      const selected = id === selectedId;
      pin.setSelected(selected);
      overlay.setZIndex(selected ? SELECTED_PIN_Z_INDEX : PIN_Z_INDEX);
    }
  }

  return {
    showPins(pins: MemoryPinData[]) {
      if (destroyed) return;
      clearPins();
      positions = pins.map((pin) => pin.position);
      for (const data of pins) {
        const pin = createPhotoPin(data, () => {
          for (const listener of [...listeners]) listener(data.id);
        });
        const overlay = new kakao.maps.CustomOverlay({
          map,
          content: pin.element,
          position: toKakaoLatLng(data.position),
          clickable: true,
          xAnchor: 0.5,
          yAnchor: pin.yAnchor,
          zIndex: PIN_Z_INDEX,
        });
        drawn.set(data.id, { overlay, pin });
      }
      applySelection();
    },

    onPinClick(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },

    setSelected(id) {
      selectedId = id;
      if (!destroyed) applySelection();
    },

    fitTo() {
      if (destroyed) return;
      const [first, ...rest] = positions;
      if (!first) {
        map.setLevel(DEFAULT_VIEW.level);
        map.setCenter(toKakaoLatLng(DEFAULT_VIEW.center));
        return;
      }
      if (rest.every((position) => position.lat === first.lat && position.lng === first.lng)) {
        map.setLevel(SINGLE_PIN_LEVEL);
        map.setCenter(toKakaoLatLng(first));
        return;
      }
      const bounds = new kakao.maps.LatLngBounds();
      for (const position of positions) bounds.extend(toKakaoLatLng(position));
      map.setBounds(bounds, FIT_PADDING_PX, FIT_PADDING_PX, FIT_PADDING_PX, FIT_PADDING_PX);
    },

    relayout() {
      if (!destroyed) map.relayout();
    },

    destroy() {
      if (destroyed) return;
      destroyed = true;
      clearPins();
      listeners.clear();
    },
  };
}
