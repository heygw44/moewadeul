// 지도 래퍼의 공개 계약이다. 화면 코드는 이 타입만 쓰고, 카카오맵 SDK는 src/map 안에서만 다룬다.
// 그래서 지도 SDK를 바꿀 때 이 폴더만 고치면 된다.

export interface LatLng {
  lat: number;
  lng: number;
}

export interface Bounds {
  sw: LatLng;
  ne: LatLng;
}

export type MarkerKind = 'CAMPSITE' | 'FOREST' | 'BAKJI';

export interface SpotMarkerData {
  id: number;
  kind: MarkerKind;
  name: string;
  position: LatLng;
  warning: boolean;
  closed: boolean;
  // 이 장소의 모집 중 베이스캠프 수다. 베이스캠프를 찾는 화면에서만 채운다.
  count?: number;
}

// 서버가 묶어서 보낸 장소 묶음이다. 지도 래퍼는 화면에서 겹치는 묶음만 합쳐서 그린다.
export interface ClusterData {
  position: LatLng;
  count: number;
}

export interface MapView {
  getBounds(): Bounds;
  getLevel(): number;
  getCenter(): LatLng;
  // level을 넘기면 그 확대 수준으로 바꾼다. 두 단계 이하로 바꿀 때만 확대·축소를 애니메이션으로 보여 준다.
  setCenter(position: LatLng, level?: number): void;
  // 지금 그려진 마커와 묶음을 모두 지우고 새로 그린다.
  showMarkers(markers: SpotMarkerData[], clusters: ClusterData[]): void;
  // 선택한 id는 showMarkers를 다시 불러도 유지된다.
  setSelected(id: number | null): void;
  // 지도 이동·확대가 멈출 때마다 부른다. 반환한 함수를 부르면 구독을 끊는다.
  onIdle(listener: () => void): () => void;
  // 사용자가 지도를 끌기 시작하거나 확대·축소가 시작될 때 부른다. 멈추면 onIdle이 온다.
  onMoveStart(listener: () => void): () => void;
  onMarkerClick(listener: (id: number) => void): () => void;
  onClusterClick(listener: (cluster: ClusterData) => void): () => void;
  // 지도의 빈 곳을 눌렀을 때 그 위치를 알려 준다. 마커와 묶음을 누른 클릭은 오지 않는다.
  onMapClick(listener: (position: LatLng) => void): () => void;
  // 지도를 우클릭하거나, 터치 화면에서 지도를 움직이지 않고 길게 눌렀을 때 그 위치를 알려 준다.
  onContextMenu(listener: (position: LatLng) => void): () => void;
  // 그 위치에 메뉴 칸 하나를 띄우고, 화면 코드가 내용을 그려 넣을 빈 요소를 돌려준다. null이면 지우고 null을 돌려준다.
  // 메뉴 칸 안을 누른 클릭은 지도 클릭으로 오지 않는다.
  showMenu(position: LatLng | null): HTMLElement | null;
  // 제보할 위치에 임시 핀 하나를 그린다. null이면 지운다.
  showDraft(position: LatLng | null): void;
  // 주소 검색으로 찾은 지점에 핀 하나를 그린다. null이면 지운다.
  showSearchPin(position: LatLng | null): void;
  setTerrain(on: boolean): void;
  // 지도를 담은 요소의 크기가 바뀐 뒤에 부른다.
  relayout(): void;
  // 오버레이와 이벤트 구독을 모두 지운다.
  destroy(): void;
}

// 추억지도에 그리는 핀 하나다. 사진이 있으면 사진 칸, 없으면 핀 모양으로 그린다.
export interface MemoryPinData {
  id: number;
  position: LatLng;
  thumbnailUrl?: string;
  // 스크린 리더가 핀 이름으로 읽는 글이다.
  label: string;
}

export interface MemoryMapView {
  // 그려진 핀을 모두 지우고 새로 그린다.
  showPins(pins: MemoryPinData[]): void;
  onPinClick(listener: (id: number) => void): () => void;
  // 선택한 핀을 키우고 흰 테두리를 더한다. null이면 선택을 푼다.
  setSelected(id: number | null): void;
  // 모든 핀이 보이게 지도를 맞춘다. 핀이 하나면 그 자리로 옮기고, 없으면 처음 화면(대한민국 전체)으로 돌아간다.
  fitTo(): void;
  relayout(): void;
  destroy(): void;
}
