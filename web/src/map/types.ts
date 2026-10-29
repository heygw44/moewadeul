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
  count?: number;
  label?: string;
}

export interface ClusterData {
  position: LatLng;
  count: number;
}

export interface MapView {
  getBounds(): Bounds;
  getLevel(): number;
  getCenter(): LatLng;
  setCenter(position: LatLng, level?: number): void;
  // setCenter와 같지만 중심을 부드럽게 옮긴다. 화면 밖 먼 곳이면 카카오맵이 바로 옮긴다.
  panTo(position: LatLng, level?: number): void;
  showMarkers(markers: SpotMarkerData[], clusters: ClusterData[]): void;
  setSelected(id: number | null): void;
  showSelectedSpot(marker: SpotMarkerData | null): void;
  onIdle(listener: () => void): () => void;
  onMoveStart(listener: () => void): () => void;
  onMarkerClick(listener: (id: number) => void): () => void;
  onClusterClick(listener: (cluster: ClusterData) => void): () => void;
  onMapClick(listener: (position: LatLng) => void): () => void;
  onContextMenu(listener: (position: LatLng) => void): () => void;
  showMenu(position: LatLng | null, visibleHeight?: number): HTMLElement | null;
  showDraft(position: LatLng | null): void;
  showSearchPin(position: LatLng | null): void;
  fitBounds(bounds: Bounds): void;
  showRadius(area: { center: LatLng; radiusKm: number } | null): void;
  setTerrain(on: boolean): void;
  relayout(): void;
  destroy(): void;
}

export interface MemoryPinData {
  id: number;
  position: LatLng;
  thumbnailUrl?: string;
  label: string;
}

export interface MemoryMapView {
  showPins(pins: MemoryPinData[]): void;
  onPinClick(listener: (id: number) => void): () => void;
  setSelected(id: number | null): void;
  fitTo(): void;
  relayout(): void;
  destroy(): void;
}
