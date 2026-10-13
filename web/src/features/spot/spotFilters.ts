import type { SpotSetting, SpotType } from '../../api/types';
import type { ActiveFilterChip } from '../../components/ActiveFilterChips';
import { SPOT_SETTING_LABELS, SPOT_TYPE_META } from './spotLabels';

// 지도 장소 조회에 붙이는 상세 조건이다. 유형이 비어 있으면 모든 유형을 본다.
export type SpotFilters = {
  types: SpotType[];
  settings: SpotSetting[];
  hasWater: boolean;
  hasToilet: boolean;
  excludeWarning: boolean;
  hideClosed: boolean;
};

export const NO_SPOT_FILTERS: SpotFilters = {
  types: [],
  settings: [],
  hasWater: false,
  hasToilet: false,
  excludeWarning: false,
  hideClosed: false,
};

export const SPOT_TYPE_ORDER: ReadonlyArray<SpotType> = ['CAMPSITE', 'FOREST', 'BAKJI'];

export const SPOT_SETTING_ORDER: ReadonlyArray<SpotSetting> = [
  'MOUNTAIN',
  'FOREST',
  'VALLEY',
  'BEACH',
  'ISLAND',
  'RIVER',
  'LAKE',
  'CITY',
];

// 켜진 조건을 칩으로 바꾼다. 칩을 누르면 그 조건만 풀린 조건으로 onChange를 부른다.
export function spotFilterChips(filters: SpotFilters, onChange: (next: SpotFilters) => void): ActiveFilterChip[] {
  const chips: ActiveFilterChip[] = [];
  if (filters.types.length > 0) {
    const label = SPOT_TYPE_ORDER.filter((type) => filters.types.includes(type))
      .map((type) => SPOT_TYPE_META[type].label)
      .join('·');
    chips.push({ key: 'types', label, onRemove: () => onChange({ ...filters, types: [] }) });
  }
  for (const value of SPOT_SETTING_ORDER.filter((setting) => filters.settings.includes(setting))) {
    chips.push({
      key: `setting-${value}`,
      label: `입지 ${SPOT_SETTING_LABELS[value]}`,
      onRemove: () => onChange({ ...filters, settings: filters.settings.filter((setting) => setting !== value) }),
    });
  }
  if (filters.hasWater) {
    chips.push({ key: 'hasWater', label: '식수 있는 박지', onRemove: () => onChange({ ...filters, hasWater: false }) });
  }
  if (filters.hasToilet) {
    chips.push({
      key: 'hasToilet',
      label: '화장실 있는 박지',
      onRemove: () => onChange({ ...filters, hasToilet: false }),
    });
  }
  if (filters.excludeWarning) {
    chips.push({
      key: 'excludeWarning',
      label: '공원 경계 경고 빼기',
      onRemove: () => onChange({ ...filters, excludeWarning: false }),
    });
  }
  if (filters.hideClosed) {
    chips.push({ key: 'hideClosed', label: '휴장인 곳 빼기', onRemove: () => onChange({ ...filters, hideClosed: false }) });
  }
  return chips;
}
