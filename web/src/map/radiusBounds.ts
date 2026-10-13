import type { Bounds, LatLng } from './types';

// 위도 1도의 길이(km)다. 경도 1도는 위도에 따라 이 값에 cos(위도)를 곱한 길이가 된다.
const KM_PER_DEGREE = 111;

// 중심에서 반경만큼 떨어진 사각형이다. 반경 원이 모두 들어간다.
export function radiusBounds(center: LatLng, radiusKm: number): Bounds {
  const latDelta = radiusKm / KM_PER_DEGREE;
  // 극 근처에서 cos이 0에 가까워져 경도 폭이 한없이 커지지 않게 아래로 막는다.
  const lngDelta = radiusKm / (KM_PER_DEGREE * Math.max(Math.cos((center.lat * Math.PI) / 180), 0.01));
  return {
    sw: { lat: center.lat - latDelta, lng: center.lng - lngDelta },
    ne: { lat: center.lat + latDelta, lng: center.lng + lngDelta },
  };
}
