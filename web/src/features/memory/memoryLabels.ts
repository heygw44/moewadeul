import type { ChoiceOption } from '../../components/ChoiceGroup';
import type { SelectOption } from '../../components/SelectField';
import type { MemoryStatus, MemoryVisibility } from '../../api/types';

// 서버 규칙과 같은 값이다.
export const MEMORY_CONTENT_MAX = 2000;
export const MEMORY_MAX_IMAGES = 10;
export const MEMORY_MAX_NIGHTS = 30;

export const VISIBILITY_OPTIONS: ReadonlyArray<ChoiceOption<MemoryVisibility>> = [
  { value: 'PRIVATE', label: '나만 보기' },
  { value: 'PUBLIC', label: '모두에게 공개' },
];

export const VISIBILITY_LABEL: Record<MemoryVisibility, string> = {
  PRIVATE: '나만 보기',
  PUBLIC: '공개',
};

// 작성자에게만 보여 주는 상태 이름이다. 정상(ACTIVE)이면 따로 적지 않는다.
export const MEMORY_STATUS_LABEL: Record<Exclude<MemoryStatus, 'ACTIVE'>, string> = {
  PENDING_REVIEW: '검토 중',
  HIDDEN: '숨김',
};

export function nightsLabel(nights: number): string {
  return nights === 0 ? '당일' : `${nights}박`;
}

export const NIGHTS_OPTIONS: ReadonlyArray<SelectOption> = Array.from(
  { length: MEMORY_MAX_NIGHTS + 1 },
  (_, nights) => ({
    value: String(nights),
    label: nightsLabel(nights),
  }),
);

// 주소의 좌표가 숫자가 아니거나 범위를 벗어나면 null이다. 서버도 같은 범위를 검사한다.
export function parseLatLng(lat: string | null, lng: string | null): { lat: number; lng: number } | null {
  if (lat === null || lng === null || lat.trim() === '' || lng.trim() === '') return null;
  const latValue = Number(lat);
  const lngValue = Number(lng);
  if (!Number.isFinite(latValue) || !Number.isFinite(lngValue)) return null;
  if (Math.abs(latValue) > 90 || Math.abs(lngValue) > 180) return null;
  return { lat: latValue, lng: lngValue };
}

export function parsePositiveId(raw: string | null): number | null {
  if (raw === null || !/^\d+$/.test(raw)) return null;
  const value = Number(raw);
  return Number.isSafeInteger(value) && value > 0 ? value : null;
}

// 제보 폼과 같은 정밀도로 줄인다.
export function roundCoordinate(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}

export function newMemoryPath(position: { lat: number; lng: number }, spotId?: number): string {
  const query = new URLSearchParams({
    lat: String(roundCoordinate(position.lat)),
    lng: String(roundCoordinate(position.lng)),
  });
  if (spotId !== undefined) query.set('spotId', String(spotId));
  return `/memories/new?${query.toString()}`;
}
