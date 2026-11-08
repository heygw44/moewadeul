import type { AdminAuditTargetType } from '../../api/types';
import { addDaysToLocalDate } from '../../lib/datetime';
import { AUDIT_TARGET_TYPES } from './adminAuditLabels';

export type AuditFilterForm = {
  targetType: string;
  adminId: string;
  startDate: string;
  endDate: string;
};

export type AuditQuery = {
  targetType: AdminAuditTargetType | undefined;
  adminId: number | undefined;
  from: string | undefined;
  to: string | undefined;
};

export type AuditFilterErrors = { adminId?: string; endDate?: string };

export type AuditFilterResult = { ok: true; query: AuditQuery } | { ok: false; errors: AuditFilterErrors };

export const EMPTY_AUDIT_FORM: AuditFilterForm = { targetType: '', adminId: '', startDate: '', endDate: '' };

export const EMPTY_AUDIT_QUERY: AuditQuery = {
  targetType: undefined,
  adminId: undefined,
  from: undefined,
  to: undefined,
};

export function hasAuditCondition(query: AuditQuery): boolean {
  return Object.values(query).some((value) => value !== undefined);
}

export function hasAuditFormInput(form: AuditFilterForm): boolean {
  return Object.values(form).some((value) => value.trim() !== '');
}

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

// 브라우저 시간대가 아니라 한국 시간(+09:00) 기준으로 그날 0시를 UTC 시각 문자열로 바꾼다.
export function kstDayStartIso(date: string): string | null {
  if (!DATE_PATTERN.test(date)) return null;
  const instant = new Date(`${date}T00:00:00+09:00`);
  return Number.isNaN(instant.getTime()) ? null : instant.toISOString();
}

// 서버의 to는 "미만"이라서 종료일을 포함하려면 다음 날 0시를 보낸다.
export function kstNextDayStartIso(date: string): string | null {
  return DATE_PATTERN.test(date) ? kstDayStartIso(addDaysToLocalDate(date, 1)) : null;
}

function parseTargetType(value: string): AdminAuditTargetType | undefined {
  return AUDIT_TARGET_TYPES.find((type) => type === value);
}

function parseAdminId(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

export function toAuditQuery(form: AuditFilterForm): AuditFilterResult {
  const errors: AuditFilterErrors = {};

  const adminIdText = form.adminId.trim();
  let adminId: number | undefined;
  if (adminIdText !== '') {
    const parsed = parseAdminId(adminIdText);
    if (parsed === null) errors.adminId = '관리자 번호는 1 이상의 숫자로 적어 주세요.';
    else adminId = parsed;
  }

  const from = form.startDate === '' ? undefined : (kstDayStartIso(form.startDate) ?? undefined);
  const to = form.endDate === '' ? undefined : (kstNextDayStartIso(form.endDate) ?? undefined);
  if (form.startDate !== '' && form.endDate !== '' && form.endDate < form.startDate) {
    errors.endDate = '종료일은 시작일과 같거나 뒤여야 해요.';
  }

  if (errors.adminId !== undefined || errors.endDate !== undefined) return { ok: false, errors };
  return { ok: true, query: { targetType: parseTargetType(form.targetType), adminId, from, to } };
}
