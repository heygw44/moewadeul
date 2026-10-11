import type { SpotType } from '../../api/types';
import type { ActiveFilterChip } from '../../components/ActiveFilterChips';
import { SPOT_TYPE_META } from '../spot/spotLabels';
import { SPOT_TYPE_ORDER } from '../spot/spotFilters';

// 베이스캠프 검색에 붙이는 조건이다. 날짜의 빈 문자열과 빈 목록은 "고르지 않음"이다.
// maxTrustLevel이 1이면 합류 조건이 본인확인(단계 1)까지만 요구하는 베이스캠프만 찾는다.
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

// 베이스캠프는 1~3박으로만 열 수 있다.
export const NIGHT_OPTIONS: ReadonlyArray<number> = [1, 2, 3];

export function isDateRangeReversed(filters: Pick<BasecampFilters, 'fromDate' | 'toDate'>): boolean {
  return filters.fromDate !== '' && filters.toDate !== '' && filters.fromDate > filters.toDate;
}

// 칩에는 짧게 "10.20"처럼 월.일만 적는다.
function shortDate(date: string): string {
  const match = /^\d{4}-(\d{2})-(\d{2})$/.exec(date);
  return match ? `${Number(match[1])}.${Number(match[2])}` : date;
}

// 켜진 조건을 칩으로 바꾼다. 칩을 누르면 그 조건만 풀린 조건으로 onChange를 부른다.
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
    chips.push({ key: 'hasVacancy', label: '빈자리 있음', onRemove: () => onChange({ ...filters, hasVacancy: false }) });
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
      label: '본인확인만 요구',
      onRemove: () => onChange({ ...filters, maxTrustLevel: null }),
    });
  }
  if (filters.noAgeLimit) {
    chips.push({ key: 'noAgeLimit', label: '연령 제한 없음', onRemove: () => onChange({ ...filters, noAgeLimit: false }) });
  }
  if (filters.noSameGenderOnly) {
    chips.push({
      key: 'noSameGenderOnly',
      label: '동성만 아님',
      onRemove: () => onChange({ ...filters, noSameGenderOnly: false }),
    });
  }
  return chips;
}
