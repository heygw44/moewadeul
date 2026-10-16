import { loadKakaoMaps } from './kakaoLoader';
import { nameRegions } from './regionLabel';
import type { RegionLabel } from './regionLabel';
import type { Bounds, LatLng } from './types';

// 모서리에서 화면 폭·높이의 이만큼 안쪽으로 들인 점을 조회한다. 모서리 바로 위는 화면에 조금만 걸친 지역이라 이름에 넣지 않는다.
const CORNER_INSET = 0.25;

// 지금 화면이 어느 지역인지 이름을 짓는다. 장소 개수를 화면 영역 전체에서 세므로, 이름도 가운데 한 점이 아니라 화면 안 다섯 점으로 짓는다.
// 카카오 API는 좌표 하나의 행정구역만 돌려줘서 가운데와 네 모서리 안쪽 점을 함께 조회한다.
// 바다처럼 행정구역이 없거나 조회에 실패한 점은 빼고, 남은 점이 없으면 null이다.
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
      // 사람들이 흔히 부르는 동 이름은 행정동이라 행정동 결과를 먼저 쓴다.
      const regions = status === kakao.maps.services.Status.OK ? result : [];
      const region = regions.find((item) => item.region_type === 'H') ?? regions[0];
      resolve(region && region.region_1depth_name ? region : null);
    });
  });
}

// 좌표가 속한 읍·면·동 이름을 "시도 시군구 읍면동"으로 만든다. 행정구역이 없으면 null이고, 조회가 실패하면 거절한다.
export async function regionNameAt(position: LatLng): Promise<string | null> {
  await loadKakaoMaps();
  const region = await findRegion(position);
  if (region === null) return null;
  const name = [region.region_1depth_name, region.region_2depth_name, region.region_3depth_name]
    .filter(Boolean)
    .join(' ');
  return name || null;
}
