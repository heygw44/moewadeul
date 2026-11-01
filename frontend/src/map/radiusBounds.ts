import type { Bounds, LatLng } from './types';

const KM_PER_DEGREE = 111;

export function radiusBounds(center: LatLng, radiusKm: number): Bounds {
  const latDelta = radiusKm / KM_PER_DEGREE;
  const lngDelta = radiusKm / (KM_PER_DEGREE * Math.max(Math.cos((center.lat * Math.PI) / 180), 0.01));
  return {
    sw: { lat: center.lat - latDelta, lng: center.lng - lngDelta },
    ne: { lat: center.lat + latDelta, lng: center.lng + lngDelta },
  };
}
