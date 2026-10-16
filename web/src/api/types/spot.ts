import type { BakjiDetail } from './bakji';
import type { SpotReview } from './review';

export type SpotType = 'CAMPSITE' | 'FOREST' | 'BAKJI';

export interface SpotMarker {
  spotId: number;
  type: SpotType;
  name: string;
  lat: number;
  lng: number;
  parkWarning: boolean;
  closedNow: boolean;
}

// 영역 안 장소가 너무 많으면 서버가 칸마다 장소 수와 칸 안 장소들의 평균 좌표를 묶어서 준다.
export interface SpotCluster {
  lat: number;
  lng: number;
  count: number;
}

export interface SpotAreaResponse {
  markers: SpotMarker[];
  clusters: SpotCluster[];
}

// 반경 검색 결과다. distanceKm은 기준 좌표에서 장소까지의 구면 거리이고, 서버가 소수점 둘째 자리까지 반올림해 준다.
export interface NearbySpot extends SpotMarker {
  distanceKm: number;
}

export type SpotSetting = 'MOUNTAIN' | 'FOREST' | 'VALLEY' | 'BEACH' | 'ISLAND' | 'RIVER' | 'LAKE' | 'CITY';

export interface SpotNearbyQuery {
  lat: number;
  lng: number;
  radiusKm: number;
  // 영역 조회와 같은 조건이다. 비우거나 false로 두면 거르지 않는다.
  types?: SpotType[];
  settings?: SpotSetting[];
  hasWater?: boolean;
  hasToilet?: boolean;
  excludeWarning?: boolean;
  hideClosed?: boolean;
  // 쪽 번호는 0부터이고, size는 50까지다.
  page?: number;
  size?: number;
}

// AI가 문장에서 읽어 낸 장소 검색 조건이다. 읽어 내지 못한 조건은 응답에서 빠지므로 모두 선택 속성이다.
// unparsed는 조건으로 바꾸지 못한 말이고, 항상 온다.
export interface AiInterpretResponse {
  region?: string;
  radiusKm?: number;
  types?: SpotType[];
  hasWater?: boolean;
  hasToilet?: boolean;
  excludeWarning?: boolean;
  hideClosed?: boolean;
  settings?: SpotSetting[];
  unparsed: string[];
}

export interface SpotAreaQuery {
  swLat: number;
  swLng: number;
  neLat: number;
  neLng: number;
  // 카카오맵 확대 레벨(1~14)
  zoom: number;
  // 비우면 서버가 모든 유형을 준다.
  types?: SpotType[];
  // 고르면 입지가 하나라도 맞는 야영장만 온다. 박지와 휴양림은 빠진다.
  settings?: SpotSetting[];
  hasWater?: boolean;
  hasToilet?: boolean;
  excludeWarning?: boolean;
  // true면 서버가 오늘 휴장인 야영장·휴양림을 뺀다. 박지는 빼지 않는다.
  hideClosed?: boolean;
}

// 값은 원천 문자열 그대로다. 그래서 수를 숫자로 바꾸거나 쉼표 목록을 나누지 않았고, 원천에 없는 항목은 null이다.
export interface PublicFacilities {
  toiletCount: string | null;
  showerCount: string | null;
  sinkCount: string | null;
  brazier: string | null;
  amenities: string | null;
  amenitiesEtc: string | null;
  nearbyFacilities: string | null;
  nearbyFacilitiesEtc: string | null;
  petPolicy: string | null;
}

export type PublicSpotSource = 'GOCAMPING' | 'FOREST' | 'FOREST_STANDARD';

export type OperatingStatus = 'OPERATING' | 'TEMPORARILY_CLOSED' | 'PERMANENTLY_CLOSED';

export interface PublicDetail {
  source: PublicSpotSource;
  // 휴양림(국립·표준데이터)은 원천에서 분류를 받지 않아서 null이다.
  category: string | null;
  // 원천이 시설 정보를 하나도 주지 않았으면 null이다. 휴양림은 늘 null이다(표준데이터의 시설 정보는 아직 받지 않는다).
  facilities: PublicFacilities | null;
  phone: string | null;
  homepage: string | null;
  // 원천의 정보 기준일(YYYY-MM-DD). 고캠핑은 원천 수정일, 국립 휴양림은 파일 기준일, 표준데이터 휴양림은 행의 데이터기준일자이고 없으면 null이다.
  sourceDate: string | null;
  // 서버가 이 원천의 동기화를 마지막으로 성공한 시각(UTC ISO). 성공한 동기화가 없으면 null이다.
  checkedAt: string | null;
  operatingStatus: OperatingStatus | null;
  closedFrom: string | null;
  closedUntil: string | null;
  closedNow: boolean;
}

// 경고가 아니면(warned가 false) 서버는 warned만 준다. 경고일 때만 나머지 필드가 함께 온다.
export interface ParkWarning {
  warned: boolean;
  areaName?: string;
  source?: string;
  sourceDate?: string;
  notice?: string;
  // 흔적을 남기지 않는 방법 같은, 서버가 정한 안내 문장이다.
  guide?: string;
}

export interface SpotRating {
  // 후기가 하나도 없으면 null이다.
  average: number | null;
  count: number;
}

export interface ExpectedPeople {
  date: string;
  count: number;
}

// 기상청이 값을 주지 않은 항목은 null이다.
export interface ShortTermForecast {
  at: string;
  temperature: number | null;
  precipitationProbability: number | null;
  windSpeed: number | null;
}

// 날짜는 한국 날짜(YYYY-MM-DD)이고, 나머지 값은 기상청이 주지 않았으면 null이다.
export interface MidTermForecast {
  date: string;
  minTemperature: number | null;
  maxTemperature: number | null;
  amSky: string | null;
  pmSky: string | null;
  amPrecipitationProbability: number | null;
  pmPrecipitationProbability: number | null;
}

export interface SunInfo {
  date: string;
  // HH:mm 형식의 한국 시각
  sunrise: string;
  sunset: string;
  civilTwilightEnd: string;
}

export interface Weather {
  source: string;
  shortTerm: ShortTermForecast[];
  midTerm: MidTermForecast[];
  // 천문연 조회만 실패하면 날씨는 주고 sun만 null이다.
  sun: SunInfo | null;
}

export interface SpotDetail {
  spotId: number;
  type: SpotType;
  name: string;
  lat: number;
  lng: number;
  address: string | null;
  // 서버는 유형에 맞는 하나만 채우고 나머지는 null로 준다.
  bakji: BakjiDetail | null;
  publicDetail: PublicDetail | null;
  parkWarning: ParkWarning;
  rating: SpotRating;
  // 작성 시각이 늦은 순서로 최대 3개다.
  recentReviews: SpotReview[];
  expectedPeople: ExpectedPeople[];
  recruitingBasecamps: unknown[];
}

// 장소 날씨는 상세와 따로 받는다(GET /api/spots/{spotId}/weather). 기상청이 실패하면 weather가 null이다.
export interface SpotWeatherResponse {
  weather: Weather | null;
}
