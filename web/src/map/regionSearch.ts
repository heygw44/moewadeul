import { loadKakaoMaps } from './kakaoLoader';
import type { LatLng } from './types';

// 지역 이름으로 찾은 후보다. level은 그 후보가 한 화면에 들어오는 지도 확대 수준이다.
export interface RegionCandidate {
  name: string;
  // 이름만으로 구분하기 어려울 때 덧붙이는 주소다. 이름과 같으면 비운다.
  detail?: string;
  position: LatLng;
  level: number;
  // 번지나 건물 번호까지 있는 주소라서, 지도를 옮긴 뒤 그 지점에 핀을 둔다.
  pinned: boolean;
}

export class RegionSearchError extends Error {
  constructor() {
    super('지역을 찾지 못했어요. 잠시 뒤 다시 검색해 주세요.');
    this.name = 'RegionSearchError';
  }
}

const MAX_CANDIDATES = 5;

// 넓은 지역일수록 주변 박지까지 보이게 넓게 보여 준다. 읍·면·동은 목록 머리에 동 이름이 나오는 수준(7)까지 확대하고,
// 번지·건물 번호가 있는 주소와 키워드 장소는 그 지점 주변 골목이 보이게 가장 가깝게 보여 준다.
const PROVINCE_LEVEL = 11;
const CITY_LEVEL = 10;
const TOWN_LEVEL = 7;
const POINT_LEVEL = 3;

// "원주 주변"처럼 끝에 붙은 말은 지역 이름이 아니라서 떼고 찾는다.
const AROUND_SUFFIX = /\s*(주변|주위|근처)$/;

export function normalizeRegionQuery(query: string): string {
  return query.trim().replace(AROUND_SUFFIX, '').trim();
}

// 먼저 주소 검색으로 행정구역과 도로명·지번 주소를 찾고, 없으면 산·호수 같은 이름을 키워드 장소 검색으로 찾는다.
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
        const results = status === kakao.maps.services.Status.OK ? result : [];
        resolve(results.slice(0, MAX_CANDIDATES).map(toAddressCandidate));
      },
      { size: MAX_CANDIDATES },
    );
  });
}

// 행정구역은 깊이로 확대 수준을 정한다. 번지·건물 번호가 있으면 그 지점을 가리키는 주소라서 가장 가깝게 보여 주고 핀을 둔다.
function toAddressCandidate(item: kakao.maps.services.AddressResult): RegionCandidate {
  const jibun = item.address;
  const road = item.road_address;
  const position = toPosition(item.x, item.y);

  if (jibun?.main_address_no || road?.main_building_no) {
    // 사용자가 입력한 쪽 주소를 이름으로 쓰고, 다른 쪽 주소를 보조 줄에 적는다.
    const byRoad = item.address_type === 'ROAD_ADDR';
    const name = (byRoad ? road?.address_name : jibun?.address_name) || item.address_name;
    const other = byRoad ? jibun?.address_name : road?.address_name;
    return {
      name,
      detail: other && other !== name ? `${byRoad ? '지번' : '도로명'} ${other}` : undefined,
      position,
      level: POINT_LEVEL,
      pinned: true,
    };
  }

  // 번호 없이 도로 이름만 찾으면 도로가 길게 이어져 있어서, 지점 대신 동네 수준으로 보여 준다.
  const region = jibun ?? road;
  let level = PROVINCE_LEVEL;
  if (item.address_type === 'ROAD' || region?.region_3depth_name) level = TOWN_LEVEL;
  else if (region?.region_2depth_name) level = CITY_LEVEL;
  return { name: item.address_name, position, level, pinned: false };
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
              level: POINT_LEVEL,
              pinned: false,
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
