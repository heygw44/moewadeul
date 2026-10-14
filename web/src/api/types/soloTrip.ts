import type { SpotType } from './spot';

// 혼자 가는 개인 일정. 날짜는 한국 날짜(YYYY-MM-DD)다.
export interface SoloTripCreateRequest {
  spotId: number;
  startDate: string;
  endDate: string;
}

export interface SoloTrip {
  soloTripId: number;
  spot: { spotId: number; name: string; type: SpotType };
  startDate: string;
  endDate: string;
}
