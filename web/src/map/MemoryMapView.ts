import { loadKakaoMaps } from './kakaoLoader';
import { createPhotoPin } from './markers';
import type { PhotoPin } from './markers';
import type { LatLng, MemoryMapView, MemoryPinData } from './types';

// 대한민국 전체가 보이는 처음 화면이다. 지도 화면(MapPage)의 처음 화면과 같다.
const DEFAULT_VIEW = { center: { lat: 36.35, lng: 127.85 }, level: 12 };
// 핀이 하나일 때의 확대 수준이다. 좌표를 약 1km로 줄여 받으므로 동네 단위가 보이는 정도로 둔다.
const SINGLE_PIN_LEVEL = 6;
// 핀이 화면 가장자리에 붙지 않도록 둘레에 두는 여백(px)이다.
const FIT_PADDING_PX = 48;
const PIN_Z_INDEX = 1;
const SELECTED_PIN_Z_INDEX = 2;

interface DrawnPin {
  overlay: kakao.maps.CustomOverlay;
  pin: PhotoPin;
}

// 추억 기록을 사진 핀으로 보여 주는 작은 지도다. 장소 지도(createMapView)와 달리 묶음·펼침이 없다.
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
      // 모든 핀이 같은 자리면 영역이 점이 되어 setBounds가 끝까지 확대하므로 핀 하나일 때와 같이 다룬다.
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
