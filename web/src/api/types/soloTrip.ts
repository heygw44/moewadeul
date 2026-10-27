import type { SpotType } from './spot';

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
