import type { AiInterpretResponse, SpotSetting, SpotType } from '../../api/types';
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

// AI가 읽어 낸 조건으로 장소 조건을 통째로 바꾼다. 응답에 없는 조건은 끈 상태로 두어, 전에 켜 둔 조건이 남지 않게 한다.
// 입지는 공공 야영장에만 있는 정보라서, 유형을 골랐는데 야영장이 없으면 입지를 조건에서 빼고 따로 돌려준다.
// 그대로 합치면 결과가 늘 0건이 되기 때문이다.
export function filtersFromInterpretation(result: AiInterpretResponse): {
  filters: SpotFilters;
  droppedSettings: SpotSetting[];
} {
  const types = result.types ?? [];
  const settings = result.settings ?? [];
  const settingsApply = types.length === 0 || types.includes('CAMPSITE');
  return {
    filters: {
      types,
      settings: settingsApply ? settings : [],
      hasWater: result.hasWater === true,
      hasToilet: result.hasToilet === true,
      excludeWarning: result.excludeWarning === true,
      hideClosed: result.hideClosed === true,
    },
    droppedSettings: settingsApply ? [] : settings,
  };
}

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
