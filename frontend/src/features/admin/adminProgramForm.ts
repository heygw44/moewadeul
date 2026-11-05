import { ApiError } from '../../api/client';
import type { AdminProgramCreateRequest, AdminProgramReviseRequest, ProgramDetail } from '../../api/types';

export type ProgramFormValues = {
  title: string;
  description: string;
  locationText: string;
  spotId: string;
  applyOpenAt: string;
  applyCloseAt: string;
  startAt: string;
  endAt: string;
  capacity: string;
  fee: string;
  paymentDeadlineMinutes: string;
  overnight: boolean;
};

export type ProgramFormErrors = Partial<Record<keyof ProgramFormValues, string>>;

export type ProgramFormMode = 'create' | 'edit';

export const LIST_PATH = '/admin/programs';

const TEXT_FIELDS = ['title', 'description', 'locationText'] as const;
const NUMBER_FIELDS = ['capacity', 'fee', 'paymentDeadlineMinutes'] as const;
const TIME_FIELDS = ['applyOpenAt', 'applyCloseAt', 'startAt', 'endAt'] as const;

type TextField = (typeof TEXT_FIELDS)[number];
type NumberField = (typeof NUMBER_FIELDS)[number];
type TimeField = (typeof TIME_FIELDS)[number];

export const TEXT_MAX_LENGTH: Record<TextField, number> = { title: 100, description: 5000, locationText: 255 };

const TEXT_MESSAGES: Record<TextField, { blank: string; tooLong: string }> = {
  title: { blank: '제목을 입력해 주세요.', tooLong: '제목은 100자 이하로 적어 주세요.' },
  description: { blank: '설명을 입력해 주세요.', tooLong: '설명은 5000자 이하로 적어 주세요.' },
  locationText: { blank: '장소 설명을 입력해 주세요.', tooLong: '장소 설명은 255자 이하로 적어 주세요.' },
};

const NUMBER_RULES: Record<NumberField, { min: number; max: number; blank: string; outOfRange: string }> = {
  capacity: {
    min: 1,
    max: 1000,
    blank: '정원을 적어 주세요.',
    outOfRange: '정원은 1~1000명 사이의 숫자로 적어 주세요.',
  },
  fee: {
    min: 0,
    max: 1_000_000,
    blank: '참가비를 적어 주세요. 무료면 0을 적어 주세요.',
    outOfRange: '참가비는 0~1,000,000원 사이의 숫자로 적어 주세요.',
  },
  paymentDeadlineMinutes: {
    min: 1,
    max: 1440,
    blank: '결제 기한을 적어 주세요.',
    outOfRange: '결제 기한은 1~1440분 사이의 숫자로 적어 주세요.',
  },
};

const TIME_LABELS: Record<TimeField, string> = {
  applyOpenAt: '신청 시작',
  applyCloseAt: '신청 마감',
  startAt: '행사 시작',
  endAt: '행사 종료',
};

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const KST_INPUT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/;

export function utcIsoToKstInput(iso: string): string {
  const ms = Date.parse(iso);
  if (Number.isNaN(ms)) return '';
  return new Date(ms + KST_OFFSET_MS).toISOString().slice(0, 16);
}

export function kstInputToUtcIso(input: string): string | null {
  if (!KST_INPUT.test(input)) return null;
  const ms = Date.parse(`${input}:00+09:00`);
  if (Number.isNaN(ms)) return null;
  const iso = new Date(ms).toISOString();
  return utcIsoToKstInput(iso) === input ? `${iso.slice(0, 19)}Z` : null;
}

export function emptyProgramForm(): ProgramFormValues {
  return {
    title: '',
    description: '',
    locationText: '',
    spotId: '',
    applyOpenAt: '',
    applyCloseAt: '',
    startAt: '',
    endAt: '',
    capacity: '',
    fee: '',
    paymentDeadlineMinutes: '',
    overnight: false,
  };
}

export function formValuesOf(program: ProgramDetail): ProgramFormValues {
  return {
    title: program.title,
    description: program.description,
    locationText: program.locationText,
    spotId: program.spotId === null ? '' : String(program.spotId),
    applyOpenAt: utcIsoToKstInput(program.applyOpenAt),
    applyCloseAt: utcIsoToKstInput(program.applyCloseAt),
    startAt: utcIsoToKstInput(program.startAt),
    endAt: utcIsoToKstInput(program.endAt),
    capacity: String(program.capacity),
    fee: String(program.fee),
    paymentDeadlineMinutes: String(program.paymentDeadlineMinutes),
    overnight: program.overnight,
  };
}

function readInteger(text: string): number | null {
  const trimmed = text.trim();
  if (!/^\d+$/.test(trimmed)) return null;
  const value = Number(trimmed);
  return Number.isSafeInteger(value) ? value : null;
}

function textError(field: TextField, text: string): string | undefined {
  const trimmed = text.trim();
  if (trimmed === '') return TEXT_MESSAGES[field].blank;
  return trimmed.length > TEXT_MAX_LENGTH[field] ? TEXT_MESSAGES[field].tooLong : undefined;
}

function spotIdError(text: string): string | undefined {
  if (text.trim() === '') return undefined;
  const value = readInteger(text);
  return value === null || value < 1 ? '지도 장소 번호는 숫자로 적어 주세요.' : undefined;
}

function numberError(field: NumberField, text: string, mode: ProgramFormMode): string | undefined {
  const rule = NUMBER_RULES[field];
  if (text.trim() === '') {
    return field === 'paymentDeadlineMinutes' && mode === 'create' ? undefined : rule.blank;
  }
  const value = readInteger(text);
  return value === null || value < rule.min || value > rule.max ? rule.outOfRange : undefined;
}

function scheduleErrors(values: ProgramFormValues, now: Date, mode: ProgramFormMode): ProgramFormErrors {
  const errors: ProgramFormErrors = {};
  const instants: Partial<Record<TimeField, number>> = {};
  for (const field of TIME_FIELDS) {
    const iso = kstInputToUtcIso(values[field]);
    if (iso === null) errors[field] = `${TIME_LABELS[field]} 시각을 입력해 주세요.`;
    else instants[field] = Date.parse(iso);
  }
  const { applyOpenAt, applyCloseAt, startAt, endAt } = instants;
  if (applyOpenAt !== undefined && applyCloseAt !== undefined && applyCloseAt <= applyOpenAt) {
    errors.applyCloseAt = '신청 마감은 신청 시작보다 늦어야 해요.';
  } else if (applyCloseAt !== undefined && startAt !== undefined && applyCloseAt > startAt) {
    errors.applyCloseAt = '신청 마감은 행사 시작보다 늦을 수 없어요.';
  }
  if (startAt !== undefined && endAt !== undefined && endAt <= startAt) {
    errors.endAt = '행사 종료는 행사 시작보다 늦어야 해요.';
  }
  if (mode === 'create' && startAt !== undefined && startAt <= now.getTime()) {
    errors.startAt = '행사 시작은 지금보다 늦어야 해요.';
  }
  return errors;
}

export function validate(values: ProgramFormValues, now: Date, mode: ProgramFormMode): ProgramFormErrors {
  const errors: ProgramFormErrors = {};
  for (const field of TEXT_FIELDS) {
    const message = textError(field, values[field]);
    if (message) errors[field] = message;
  }
  const spotMessage = spotIdError(values.spotId);
  if (spotMessage) errors.spotId = spotMessage;
  for (const field of NUMBER_FIELDS) {
    const message = numberError(field, values[field], mode);
    if (message) errors[field] = message;
  }
  return { ...errors, ...scheduleErrors(values, now, mode) };
}

type ParsedProgram = {
  title: string;
  description: string;
  locationText: string;
  spotId: number | null;
  applyOpenAt: string;
  applyCloseAt: string;
  startAt: string;
  endAt: string;
  capacity: number;
  fee: number;
  paymentDeadlineMinutes: number | null;
  overnight: boolean;
};

function requireUtcIso(input: string): string {
  const iso = kstInputToUtcIso(input);
  if (iso === null) throw new Error(`검증을 통과하지 못한 시각이에요: ${input}`);
  return iso;
}

// validate를 통과한 값에만 쓴다.
function parseValues(values: ProgramFormValues): ParsedProgram {
  return {
    title: values.title.trim(),
    description: values.description.trim(),
    locationText: values.locationText.trim(),
    spotId: values.spotId.trim() === '' ? null : Number(values.spotId),
    applyOpenAt: requireUtcIso(values.applyOpenAt),
    applyCloseAt: requireUtcIso(values.applyCloseAt),
    startAt: requireUtcIso(values.startAt),
    endAt: requireUtcIso(values.endAt),
    capacity: Number(values.capacity),
    fee: Number(values.fee),
    paymentDeadlineMinutes: values.paymentDeadlineMinutes.trim() === '' ? null : Number(values.paymentDeadlineMinutes),
    overnight: values.overnight,
  };
}

export function toCreateRequest(values: ProgramFormValues): AdminProgramCreateRequest {
  const { spotId, paymentDeadlineMinutes, ...rest } = parseValues(values);
  return {
    ...rest,
    ...(spotId === null ? {} : { spotId }),
    ...(paymentDeadlineMinutes === null ? {} : { paymentDeadlineMinutes }),
  };
}

export function toRevision(original: ProgramFormValues, values: ProgramFormValues): AdminProgramReviseRequest {
  const before = parseValues(original);
  const after = parseValues(values);
  const revision: AdminProgramReviseRequest = {};
  if (after.title !== before.title) revision.title = after.title;
  if (after.description !== before.description) revision.description = after.description;
  if (after.locationText !== before.locationText) revision.locationText = after.locationText;
  if (after.spotId !== before.spotId) revision.spotId = after.spotId;
  if (after.applyOpenAt !== before.applyOpenAt) revision.applyOpenAt = after.applyOpenAt;
  if (after.applyCloseAt !== before.applyCloseAt) revision.applyCloseAt = after.applyCloseAt;
  if (after.startAt !== before.startAt) revision.startAt = after.startAt;
  if (after.endAt !== before.endAt) revision.endAt = after.endAt;
  if (after.capacity !== before.capacity) revision.capacity = after.capacity;
  if (after.fee !== before.fee) revision.fee = after.fee;
  if (after.paymentDeadlineMinutes !== null && after.paymentDeadlineMinutes !== before.paymentDeadlineMinutes) {
    revision.paymentDeadlineMinutes = after.paymentDeadlineMinutes;
  }
  if (after.overnight !== before.overnight) revision.overnight = after.overnight;
  return revision;
}

const FORM_FIELDS: ReadonlySet<string> = new Set<keyof ProgramFormValues>([
  ...TEXT_FIELDS,
  ...NUMBER_FIELDS,
  ...TIME_FIELDS,
  'spotId',
  'overnight',
]);

function isFormField(name: string): name is keyof ProgramFormValues {
  return FORM_FIELDS.has(name);
}

const FIELD_PREFIXED_MESSAGE = /^(\w+): (.+)$/s;

function serverIssues(error: ApiError): Array<{ field: string; reason: string }> {
  if (error.fieldErrors.length > 0) {
    return error.fieldErrors.map(({ field, reason }) => ({ field: field.split('.').pop() ?? field, reason }));
  }
  const prefixed = error.code === 'INVALID_INPUT' ? FIELD_PREFIXED_MESSAGE.exec(error.message) : null;
  return prefixed?.[1] && prefixed[2] ? [{ field: prefixed[1], reason: prefixed[2] }] : [];
}

export const UNCONFIRMED_TITLE = '처리 결과를 확인하지 못했어요';

export function isResultUnknown(error: unknown): boolean {
  return error instanceof ApiError && error.status === 0;
}

export function serverErrorsToFields(error: ApiError): { fields: ProgramFormErrors; rest: string[] } {
  const fields: ProgramFormErrors = {};
  const rest: string[] = [];
  if (error.code === 'PROGRAM_CAPACITY_DECREASE') {
    fields.capacity = error.message;
    return { fields, rest };
  }
  const issues = serverIssues(error);
  if (issues.length === 0) return { fields, rest: [error.message] };
  for (const { field, reason } of issues) {
    if (isFormField(field)) fields[field] = reason;
    else rest.push(reason);
  }
  return { fields, rest };
}

export type SavedKind = 'created' | 'revised';

export const SAVED_NOTICES: Record<SavedKind, string> = {
  created: '행사를 등록했어요.',
  revised: '행사를 수정했어요.',
};

export function savedListPath(kind: SavedKind): string {
  return `${LIST_PATH}?saved=${kind}`;
}

export function savedKindFrom(search: string): SavedKind | null {
  const value = new URLSearchParams(search).get('saved');
  return value === 'created' || value === 'revised' ? value : null;
}
