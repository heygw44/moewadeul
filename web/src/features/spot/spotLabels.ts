import type {
  BakjiReportReason,
  GroundType,
  PublicFacilities,
  SignalLevel,
  SpotSetting,
  SpotType,
} from '../../api/types';
import type { IconName } from '../../components/iconPaths';

type SpotTypeTone = 'info' | 'accent' | 'bakji';

type SpotTypeMeta = {
  label: string;
  icon: IconName;
  tone: SpotTypeTone;
  tileClass: string;
};

export const SPOT_TYPE_META: Record<SpotType, SpotTypeMeta> = {
  CAMPSITE: { label: '공공 야영장', icon: 'tent', tone: 'info', tileClass: 'bg-info-soft text-info' },
  FOREST: { label: '자연휴양림', icon: 'tree', tone: 'accent', tileClass: 'bg-accent-soft text-accent-deep' },
  BAKJI: { label: '박지', icon: 'backpack', tone: 'bakji', tileClass: 'bg-bakji-soft text-bakji-strong' },
};

export const SIGNAL_LEVEL_LABELS: Record<SignalLevel, string> = {
  NONE: '없음',
  WEAK: '약함',
  GOOD: '좋음',
};

export const FACILITY_LABELS: ReadonlyArray<readonly [keyof PublicFacilities, string]> = [
  ['toiletCount', '화장실 수'],
  ['showerCount', '샤워실 수'],
  ['sinkCount', '개수대 수'],
  ['brazier', '화로대'],
  ['amenities', '부대시설'],
  ['amenitiesEtc', '기타 부대시설'],
  ['nearbyFacilities', '주변 시설'],
  ['nearbyFacilitiesEtc', '기타 주변 시설'],
  ['petPolicy', '반려동물'],
];

const SOURCE_LABELS: Record<string, string> = {
  GOCAMPING: '한국관광공사 고캠핑',
  FOREST: '산림청 국립자연휴양림관리소',
  FOREST_STANDARD: '전국휴양림표준데이터',
  KDPA: '한국보호지역통합DB(KDPA)',
};

export function sourceLabel(code: string): string {
  return SOURCE_LABELS[code] ?? code;
}

export const GROUND_TYPE_LABELS: Record<GroundType, string> = {
  SOIL: '흙',
  GRASS: '풀밭',
  GRAVEL: '자갈',
  SAND: '모래',
  ROCK: '바위',
  DECK: '데크',
};

export const REPORT_REASON_LABELS: Record<BakjiReportReason, string> = {
  ILLEGAL_AREA: '야영이 금지된 곳',
  CLOSED: '더는 야영할 수 없는 곳',
  FALSE_INFO: '정보가 사실과 다른 곳',
};

export const SPOT_SETTING_LABELS: Record<SpotSetting, string> = {
  MOUNTAIN: '산',
  FOREST: '숲',
  VALLEY: '계곡',
  BEACH: '해변',
  ISLAND: '섬',
  RIVER: '강',
  LAKE: '호수',
  CITY: '도심',
};
