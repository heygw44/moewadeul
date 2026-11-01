import { loadKakaoMaps } from './kakaoLoader';
import type { LatLng } from './types';

export interface RegionCandidate {
  name: string;
  detail?: string;
  position: LatLng;
  level: number;
  pinned: boolean;
}

export class RegionSearchError extends Error {
  constructor() {
    super('지역을 검색하지 못했어요. 잠시 뒤 다시 검색해 주세요.');
    this.name = 'RegionSearchError';
  }
}

const MAX_CANDIDATES = 5;

const PROVINCE_LEVEL = 11;
const CITY_LEVEL = 10;
const TOWN_LEVEL = 7;
const POINT_LEVEL = 3;

const AROUND_SUFFIX = /\s*(주변|주위|근처)$/;

export function normalizeRegionQuery(query: string): string {
  return query.trim().replace(AROUND_SUFFIX, '').trim();
}

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

function toAddressCandidate(item: kakao.maps.services.AddressResult): RegionCandidate {
  const jibun = item.address;
  const road = item.road_address;
  const position = toPosition(item.x, item.y);

  if (jibun?.main_address_no || road?.main_building_no) {
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
