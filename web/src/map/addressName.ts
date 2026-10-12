import { loadKakaoMaps } from './kakaoLoader';
import type { LatLng } from './types';

// 좌표에 있는 주소를 사람이 읽는 글로 만든다. 도로명 주소가 있으면 도로명 주소를, 없으면 지번 주소를 쓴다.
// 산이나 들처럼 건물이 없는 곳은 도로명 주소가 없어서 "…리 산 1" 같은 지번 주소가 나온다. 바다처럼 주소가 없으면 null이다.
export async function addressAt(position: LatLng): Promise<string | null> {
  await loadKakaoMaps();
  return new Promise((resolve, reject) => {
    new kakao.maps.services.Geocoder().coord2Address(position.lng, position.lat, (result, status) => {
      if (status === kakao.maps.services.Status.ERROR) {
        reject(new Error('주소를 찾지 못했어요.'));
        return;
      }
      const found = status === kakao.maps.services.Status.OK ? result[0] : undefined;
      resolve(found?.road_address?.address_name || found?.address?.address_name || null);
    });
  });
}
