import type { LatLng } from '../map/types';

const EARTH_RADIUS_KM = 6371;

function toRadians(degrees: number): number {
  return (degrees * Math.PI) / 180;
}

// 두 좌표 사이의 대원 거리(km)다. 목록을 지도 가운데에서 가까운 순으로 줄 세우고 행마다 거리를 적을 때 쓴다.
export function distanceKm(a: LatLng, b: LatLng): number {
  const dLat = toRadians(b.lat - a.lat);
  const dLng = toRadians(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRadians(a.lat)) * Math.cos(toRadians(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)));
}

// 목록에 적는 거리 글이다. 10km 안은 소수 첫째 자리(4.0처럼 0도 적는다), 그 밖은 정수로 줄인다.
export function formatDistanceKm(km: number): string {
  return km < 10 ? km.toFixed(1) : String(Math.round(km));
}
