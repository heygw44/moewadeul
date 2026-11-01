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

export interface SpotCluster {
  lat: number;
  lng: number;
  count: number;
}

export interface SpotAreaResponse {
  markers: SpotMarker[];
  clusters: SpotCluster[];
}

export interface NearbySpot extends SpotMarker {
  distanceKm: number;
}

export type SpotSetting = 'MOUNTAIN' | 'FOREST' | 'VALLEY' | 'BEACH' | 'ISLAND' | 'RIVER' | 'LAKE' | 'CITY';

export interface SpotNearbyQuery {
  lat: number;
  lng: number;
  radiusKm: number;
  types?: SpotType[];
  settings?: SpotSetting[];
  hasWater?: boolean;
  hasToilet?: boolean;
  excludeWarning?: boolean;
  hideClosed?: boolean;
  page?: number;
  size?: number;
}

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
  zoom: number;
  types?: SpotType[];
  settings?: SpotSetting[];
  hasWater?: boolean;
  hasToilet?: boolean;
  excludeWarning?: boolean;
  hideClosed?: boolean;
}

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
  category: string | null;
  facilities: PublicFacilities | null;
  phone: string | null;
  homepage: string | null;
  sourceDate: string | null;
  checkedAt: string | null;
  operatingStatus: OperatingStatus | null;
  closedFrom: string | null;
  closedUntil: string | null;
  closedNow: boolean;
}

export interface ParkWarning {
  warned: boolean;
  areaName?: string;
  source?: string;
  sourceDate?: string;
  notice?: string;
  guide?: string;
}

export interface SpotRating {
  average: number | null;
  count: number;
}

export interface ExpectedPeople {
  date: string;
  count: number;
}

export interface ShortTermForecast {
  at: string;
  temperature: number | null;
  precipitationProbability: number | null;
  windSpeed: number | null;
}

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
  sunrise: string;
  sunset: string;
  civilTwilightEnd: string;
}

export interface Weather {
  source: string;
  shortTerm: ShortTermForecast[];
  midTerm: MidTermForecast[];
  sun: SunInfo | null;
}

export interface SpotDetail {
  spotId: number;
  type: SpotType;
  name: string;
  lat: number;
  lng: number;
  address: string | null;
  bakji: BakjiDetail | null;
  publicDetail: PublicDetail | null;
  parkWarning: ParkWarning;
  rating: SpotRating;
  recentReviews: SpotReview[];
  expectedPeople: ExpectedPeople[];
  recruitingBasecamps: unknown[];
}

export interface SpotWeatherResponse {
  weather: Weather | null;
}
