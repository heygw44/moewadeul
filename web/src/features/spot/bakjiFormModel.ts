import { useState } from 'react';
import { ApiError } from '../../api/client';
import type { GroundType, SignalLevel } from '../../api/types';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import type { LatLng } from '../../map/types';
import { GROUND_TYPE_LABELS } from './spotLabels';

// 박지 제보 폼과 수정 폼이 함께 쓰는 값 모양, 검사, 오류 처리다. 화면 조각은 BakjiFormParts에 있다.

export type YesNo = 'YES' | 'NO';
export type Unknown = 'UNKNOWN';
// 수정 화면에서 바닥을 건드리지 않았다는 뜻이다. 장소 상세 응답에 지금 바닥 값이 없어서 미리 골라 둘 수 없다.
export type Keep = 'KEEP';
export type GroundChoice = Keep | Unknown | GroundType;

export type FormField = 'location' | 'name' | 'description' | 'hasWater' | 'hasToilet' | 'signalLevel' | 'groundType';

export type FieldErrors = Partial<Record<FormField, string>>;

export type FormError = { cause: unknown } | null;

export type BakjiFormValues = {
  name: string;
  description: string;
  hasWater: YesNo | null;
  hasToilet: YesNo | null;
  signalLevel: Unknown | SignalLevel;
  groundType: GroundChoice;
};

export const GROUND_OPTIONS: ChoiceOption<GroundChoice>[] = [
  { value: 'UNKNOWN', label: '모름' },
  ...(Object.keys(GROUND_TYPE_LABELS) as GroundType[]).map((value) => ({ value, label: GROUND_TYPE_LABELS[value] })),
];

export const GROUND_OPTIONS_WITH_KEEP: ChoiceOption<GroundChoice>[] = [
  { value: 'KEEP', label: '바꾸지 않음' },
  ...GROUND_OPTIONS,
];

export const PRIMARY_LINK_CLASS =
  'inline-flex min-h-11 items-center justify-center rounded-control border border-ink bg-ink px-4 text-base font-semibold text-white hover:border-ink-muted hover:bg-ink-muted';

// 서버는 좌표를 소수 여섯째 자리까지 저장한다. 지도에서 찍은 값을 그 자리로 맞춰 보낸다.
export function roundCoordinate(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}

// 화면에서 먼저 막는 빈 칸이다. 서버는 식수·화장실을 꼭 받는데, 기본값을 두면 모르고 넘어간 항목이 "없음"으로
// 저장되므로 직접 고르게 한다.
export function requiredFieldErrors(draft: LatLng | null, values: BakjiFormValues): FieldErrors {
  const errors: FieldErrors = {};
  if (draft === null) errors.location = '지도를 눌러 박지 위치를 찍어 주세요.';
  if (values.name.trim() === '') errors.name = '박지 이름을 적어 주세요.';
  if (values.hasWater === null) errors.hasWater = '식수가 있는지 골라 주세요.';
  if (values.hasToilet === null) errors.hasToilet = '화장실이 있는지 골라 주세요.';
  return errors;
}

const SERVER_FIELD_MAP: Record<string, FormField> = {
  lat: 'location',
  lng: 'location',
  name: 'name',
  description: 'description',
  hasWater: 'hasWater',
  hasToilet: 'hasToilet',
  signalLevel: 'signalLevel',
  groundType: 'groundType',
};

// 서버가 지도 영역 밖 좌표를 lat, lng 두 필드로 나눠 알려 주므로, 위치 한 곳에 중복 없이 모아 보여 준다.
function mergeReasons(reasons: string[]): string {
  return [...new Set(reasons)].join(' ');
}

// 보내기에 실패한 오류를 칸별 오류와 폼 위 오류로 나눈다.
export function submitErrorState(error: unknown): { fieldErrors: FieldErrors; formError: FormError } {
  if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
    const reasons: Partial<Record<FormField, string[]>> = {};
    const others: string[] = [];
    for (const { field, reason } of error.fieldErrors) {
      const target = SERVER_FIELD_MAP[field];
      if (target) (reasons[target] ??= []).push(reason);
      else others.push(reason);
    }
    const fieldErrors: FieldErrors = {};
    for (const [field, list] of Object.entries(reasons)) fieldErrors[field as FormField] = mergeReasons(list);
    return { fieldErrors, formError: others.length > 0 ? { cause: others.join(' ') } : null };
  }
  // 목록에 없는 선택 값이나 서비스 지역 밖 좌표처럼 필드 정보 없이 오는 입력 오류는 서버가 준 문구를 그대로 보여 준다.
  if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.serverMessage !== '') {
    return { fieldErrors: {}, formError: { cause: error.serverMessage } };
  }
  return { fieldErrors: {}, formError: { cause: error } };
}

// 위치를 새로 찍으면 이전 위치의 오류는 더 맞지 않으므로 지운다. 렌더 중에 비교해서 한 번에 반영한다.
export function useLocationErrorReset(
  draft: LatLng | null,
  fieldErrors: FieldErrors,
  setFieldErrors: (errors: FieldErrors) => void,
) {
  const [checkedDraft, setCheckedDraft] = useState(draft);
  if (checkedDraft !== draft) {
    setCheckedDraft(draft);
    if (fieldErrors.location) setFieldErrors({ ...fieldErrors, location: undefined });
  }
}
