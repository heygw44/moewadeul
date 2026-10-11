import { loadKakaoMaps } from './kakaoLoader';
import type { LatLng } from './types';

// 지역 이름으로 찾은 후보다. level은 그 후보가 한 화면에 들어오는 지도 확대 수준이다.
export interface RegionCandidate {
  name: string;
  // 이름만으로 구분하기 어려울 때 덧붙이는 주소다. 이름과 같으면 비운다.
  detail?: string;
  position: LatLng;
  level: number;
}

export class RegionSearchError extends Error {
  constructor() {
    super('지역을 찾지 못했어요. 잠시 뒤 다시 검색해 주세요.');
    this.name = 'RegionSearchError';
  }
}

const MAX_CANDIDATES = 5;

// 시·군·구처럼 넓은 지역은 주변 박지까지 보이게 넓게, 읍·면·동과 장소는 그보다 좁게 보여 준다.
const CITY_LEVEL = 10;
const TOWN_LEVEL = 8;
const PLACE_LEVEL = 7;

// "원주 주변"처럼 끝에 붙은 말은 지역 이름이 아니라서 떼고 찾는다.
const AROUND_SUFFIX = /\s*(주변|주위|근처)$/;

export function normalizeRegionQuery(query: string): string {
  return query.trim().replace(AROUND_SUFFIX, '').trim();
}

// 먼저 주소 검색으로 행정구역을 찾고, 없으면 산·호수 같은 이름을 키워드 장소 검색으로 찾는다.
export async function searchRegions(query: string): Promise<RegionCandidate[]> {
  const keyword = normalizeRegionQuery(query);
  if (keyword === '') return [];
  await loadKakaoMaps();

  const regions = await searchAddresses(keyword);
  if (regions.length > 0) return regions;
  return searchPlaces(keyword);
}

function searchAddresses(keyword: string): Promise<RegionCandidate[]> {
  return new Promise((resolve, reject) => {
    new kakao.maps.services.Geocoder().addressSearch(
      keyword,
      (result, status) => {
        if (status === kakao.maps.services.Status.ERROR) {
          reject(new RegionSearchError());
          return;
        }
        // 도로명·지번 주소는 건물 하나를 가리키므로 지역 후보에서 뺀다.
        const regions = status === kakao.maps.services.Status.OK ? result.filter((item) => item.address_type === 'REGION') : [];
        resolve(
          regions.slice(0, MAX_CANDIDATES).map((item) => ({
            name: item.address_name,
            position: toPosition(item.x, item.y),
            level: item.address?.region_3depth_name ? TOWN_LEVEL : CITY_LEVEL,
          })),
        );
      },
      { size: MAX_CANDIDATES * 2 },
    );
  });
}

function searchPlaces(keyword: string): Promise<RegionCandidate[]> {
  return new Promise((resolve, reject) => {
    new kakao.maps.services.Places().keywordSearch(
      keyword,
      (result, status) => {
        if (status === kakao.maps.services.Status.ERROR) {
          reject(new RegionSearchError());
          return;
        }
        const places = status === kakao.maps.services.Status.OK ? result : [];
        resolve(
          places.slice(0, MAX_CANDIDATES).map((item) => {
            const address = item.road_address_name || item.address_name;
            return {
              name: item.place_name,
              detail: address && address !== item.place_name ? address : undefined,
              position: toPosition(item.x, item.y),
              level: PLACE_LEVEL,
            };
          }),
        );
      },
      { size: MAX_CANDIDATES },
    );
  });
}

function toPosition(x: string, y: string): LatLng {
  return { lat: Number(y), lng: Number(x) };
}
