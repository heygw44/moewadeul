import type { SpotType } from '../../api/types';
import type { ActiveFilterChip } from '../../components/ActiveFilterChips';
import { SPOT_TYPE_META, SPOT_TYPE_ORDER } from '../../lib/spotLabels';

export type BasecampFilters = {
  fromDate: string;
  toDate: string;
  hasVacancy: boolean;
  types: SpotType[];
  nights: number[];
  maxTrustLevel: number | null;
  noAgeLimit: boolean;
  noSameGenderOnly: boolean;
};

export const NO_BASECAMP_FILTERS: BasecampFilters = {
  fromDate: '',
  toDate: '',
  hasVacancy: false,
  types: [],
  nights: [],
  maxTrustLevel: null,
  noAgeLimit: false,
  noSameGenderOnly: false,
};

export const NIGHT_OPTIONS: ReadonlyArray<number> = [1, 2, 3];

export function isDateRangeReversed(filters: Pick<BasecampFilters, 'fromDate' | 'toDate'>): boolean {
  return filters.fromDate !== '' && filters.toDate !== '' && filters.fromDate > filters.toDate;
}

function shortDate(date: string): string {
  const match = /^\d{4}-(\d{2})-(\d{2})$/.exec(date);
  return match ? `${Number(match[1])}.${Number(match[2])}` : date;
}

export function basecampFilterChips(
  filters: BasecampFilters,
  onChange: (next: BasecampFilters) => void,
): ActiveFilterChip[] {
  const chips: ActiveFilterChip[] = [];
  if (filters.fromDate !== '' || filters.toDate !== '') {
    const label = `출발 ${shortDate(filters.fromDate)}~${shortDate(filters.toDate)}`;
    chips.push({ key: 'dates', label, onRemove: () => onChange({ ...filters, fromDate: '', toDate: '' }) });
  }
  if (filters.hasVacancy) {
    chips.push({
      key: 'hasVacancy',
      label: '빈자리 있음',
      onRemove: () => onChange({ ...filters, hasVacancy: false }),
    });
  }
  if (filters.types.length > 0) {
    const label = SPOT_TYPE_ORDER.filter((type) => filters.types.includes(type))
      .map((type) => SPOT_TYPE_META[type].label)
      .join('·');
    chips.push({ key: 'types', label, onRemove: () => onChange({ ...filters, types: [] }) });
  }
  if (filters.nights.length > 0) {
    const label = [...filters.nights]
      .sort((a, b) => a - b)
      .map((night) => `${night}박`)
      .join('·');
    chips.push({ key: 'nights', label, onRemove: () => onChange({ ...filters, nights: [] }) });
  }
  if (filters.maxTrustLevel !== null) {
    chips.push({
      key: 'maxTrustLevel',
      label: '본인확인만으로 합류',
      onRemove: () => onChange({ ...filters, maxTrustLevel: null }),
    });
  }
  if (filters.noAgeLimit) {
    chips.push({
      key: 'noAgeLimit',
      label: '연령 제한 없음',
      onRemove: () => onChange({ ...filters, noAgeLimit: false }),
    });
  }
  if (filters.noSameGenderOnly) {
    chips.push({
      key: 'noSameGenderOnly',
      label: '성별 제한 없음',
      onRemove: () => onChange({ ...filters, noSameGenderOnly: false }),
    });
  }
  return chips;
}
