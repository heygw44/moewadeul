import { loadKakaoMaps } from './kakaoLoader';
import { nameRegions } from './regionLabel';
import type { RegionLabel } from './regionLabel';
import type { Bounds, LatLng } from './types';

const CORNER_INSET = 0.25;

// 카카오 API는 좌표 하나의 행정구역만 돌려줘서 가운데와 네 모서리 안쪽 점을 함께 조회한다.
export async function regionLabelInView(bounds: Bounds, center: LatLng, level: number): Promise<RegionLabel | null> {
  await loadKakaoMaps();
  const { sw, ne } = bounds;
  const latInset = (ne.lat - sw.lat) * CORNER_INSET;
  const lngInset = (ne.lng - sw.lng) * CORNER_INSET;
  const points: LatLng[] = [
    center,
    { lat: ne.lat - latInset, lng: sw.lng + lngInset },
    { lat: ne.lat - latInset, lng: ne.lng - lngInset },
    { lat: sw.lat + latInset, lng: sw.lng + lngInset },
    { lat: sw.lat + latInset, lng: ne.lng - lngInset },
  ];
  const results = await Promise.allSettled(points.map(findRegion));
  const regions = results.flatMap((result) =>
    result.status === 'fulfilled' && result.value !== null ? [result.value] : [],
  );
  return nameRegions(regions, level, points.length);
}

function findRegion(position: LatLng): Promise<kakao.maps.services.RegionCodeResult | null> {
  return new Promise((resolve, reject) => {
    new kakao.maps.services.Geocoder().coord2RegionCode(position.lng, position.lat, (result, status) => {
      if (status === kakao.maps.services.Status.ERROR) {
        reject(new Error('행정구역을 찾지 못했어요.'));
        return;
      }
      const regions = status === kakao.maps.services.Status.OK ? result : [];
      const region = regions.find((item) => item.region_type === 'H') ?? regions[0];
      resolve(region && region.region_1depth_name ? region : null);
    });
  });
}

export async function regionNameAt(position: LatLng): Promise<string | null> {
  await loadKakaoMaps();
  const region = await findRegion(position);
  if (region === null) return null;
  const name = [region.region_1depth_name, region.region_2depth_name, region.region_3depth_name]
    .filter(Boolean)
    .join(' ');
  return name || null;
}
