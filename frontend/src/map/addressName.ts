import { loadKakaoMaps } from './kakaoLoader';
import type { LatLng } from './types';

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
