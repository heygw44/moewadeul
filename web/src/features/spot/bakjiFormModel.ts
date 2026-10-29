import { useState } from 'react';
import { ApiError } from '../../api/client';
import type { GroundType, SignalLevel } from '../../api/types';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import type { LatLng } from '../../map/types';
import { GROUND_TYPE_LABELS } from '../../lib/spotLabels';

export type YesNo = 'YES' | 'NO';
export type Unknown = 'UNKNOWN';
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

function mergeReasons(reasons: string[]): string {
  return [...new Set(reasons)].join(' ');
}

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
  if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.serverMessage !== '') {
    return { fieldErrors: {}, formError: { cause: error.serverMessage } };
  }
  return { fieldErrors: {}, formError: { cause: error } };
}

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
