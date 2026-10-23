import type { LatLng } from '../../map/types';
import { NO_BASECAMP_FILTERS } from '../basecamp/basecampFilters';
import type { BasecampFilters } from '../basecamp/basecampFilters';
import type { AiSummary } from './AiInterpretSummary';
import { NO_SPOT_FILTERS } from './spotFilters';
import type { SpotFilters } from './spotFilters';

// 반경 검색의 기준점과 반경이다. label은 목록 머리에 적는 기준점 이름이다.
export type RadiusSearch = { center: LatLng; radiusKm: number; label: string };

export type MapScreenMemory = {
  // 지도가 마지막으로 멈춘 곳의 가운데와 확대 수준이다. 이번 방문에서 지도를 연 적이 없으면 null이다.
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

// 사용자가 커뮤니티나 내 정보에 다녀와도 지도 화면을 보던 곳과 조건 그대로 다시 열려고, 마지막 상태를 화면 밖 메모리에 둔다.
// 브라우저 저장소에는 넣지 않는다. AI 요약에는 사용자가 쓴 문장의 일부가 들어 있어서, 새로 고치거나 탭을 닫으면 함께 사라져야 한다.
let memory: MapScreenMemory = EMPTY;

export function readMapScreen(): MapScreenMemory {
  return memory;
}

export function rememberMapScreen(patch: Partial<MapScreenMemory>): void {
  memory = { ...memory, ...patch };
}

// 로그아웃하거나 세션이 끊기거나 탈퇴하면 지운다. 같은 탭에서 다음에 로그인한 사람에게 앞사람의 조건과 AI 요약이 보이지 않게 하기 위해서다.
export function forgetMapScreen(): void {
  memory = EMPTY;
}
