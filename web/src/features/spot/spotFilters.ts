import type { AiInterpretResponse, SpotSetting, SpotType } from '../../api/types';
import type { ActiveFilterChip } from '../../components/ActiveFilterChips';
import { SPOT_SETTING_LABELS, SPOT_TYPE_META, SPOT_TYPE_ORDER } from '../../lib/spotLabels';

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
    chips.push({
      key: 'hideClosed',
      label: '휴장인 곳 빼기',
      onRemove: () => onChange({ ...filters, hideClosed: false }),
    });
  }
  return chips;
}
