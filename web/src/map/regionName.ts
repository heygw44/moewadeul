import { loadKakaoMaps } from './kakaoLoader';
import type { LatLng } from './types';

// 많이 확대하면 동네까지, 시 하나가 들어오는 축척이면 시·도와 시·군·구까지 적는다.
// 그보다 넓으면 화면에 여러 시·군이 걸쳐서 가운데 시·도만 "일대"로 적는다.
const TOWN_MAX_LEVEL = 7;
const CITY_MAX_LEVEL = 10;

// 좌표가 속한 행정구역 이름을 지도 확대 수준에 맞춰 만든다. 바다처럼 행정구역이 없으면 null이다.
export async function regionNameAt(position: LatLng, level: number): Promise<string | null> {
  await loadKakaoMaps();
  const region = await findRegion(position);
  if (region === null) return null;

  const province = region.region_1depth_name;
  const city = region.region_2depth_name;
  const town = region.region_3depth_name;
  // 세종시처럼 시·군·구가 없는 곳은 시·도 이름이 그 자리를 대신한다.
  if (level <= TOWN_MAX_LEVEL) return joinNames(city || province, town);
  if (level <= CITY_MAX_LEVEL) return joinNames(province, city);
  return `${province} 일대`;
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

function joinNames(...names: string[]): string {
  return names.filter((name) => name !== '').join(' ');
}
