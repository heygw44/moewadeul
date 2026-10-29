import { onSignedOut } from '../../app/session/signedOut';
import type { LatLng } from '../../map/types';
import { NO_BASECAMP_FILTERS } from '../basecamp/basecampFilters';
import type { BasecampFilters } from '../basecamp/basecampFilters';
import type { AiSummary } from './AiInterpretSummary';
import { NO_SPOT_FILTERS } from '../spot/spotFilters';
import type { SpotFilters } from '../spot/spotFilters';

export type RadiusSearch = { center: LatLng; radiusKm: number; label: string };

export type MapScreenMemory = {
  view: { center: LatLng; level: number } | null;
  spotFilters: SpotFilters;
  basecampFilters: BasecampFilters;
  radiusSearch: RadiusSearch | null;
  aiSummary: AiSummary | null;
};

const EMPTY: MapScreenMemory = {
  view: null,
  spotFilters: NO_SPOT_FILTERS,
  basecampFilters: NO_BASECAMP_FILTERS,
  radiusSearch: null,
  aiSummary: null,
};

let memory: MapScreenMemory = EMPTY;

export function readMapScreen(): MapScreenMemory {
  return memory;
}

export function rememberMapScreen(patch: Partial<MapScreenMemory>): void {
  memory = { ...memory, ...patch };
}

export function forgetMapScreen(): void {
  memory = EMPTY;
}

onSignedOut(forgetMapScreen);
