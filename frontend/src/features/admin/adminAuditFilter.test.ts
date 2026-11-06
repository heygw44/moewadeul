import { describe, expect, it } from 'vitest';
import { EMPTY_AUDIT_FORM, kstDayStartIso, kstNextDayStartIso, toAuditQuery } from './adminAuditFilter';

describe('감사 기록 조건 변환', () => {
  it('시작일은 한국 시간 0시를 UTC로 바꾼다', () => {
    expect(kstDayStartIso('2026-11-05')).toBe('2026-11-04T15:00:00.000Z');
  });

  it('종료일은 다음 날 한국 시간 0시를 UTC로 바꾼다', () => {
    expect(kstNextDayStartIso('2026-11-05')).toBe('2026-11-05T15:00:00.000Z');
  });

  it('종료일이 달이나 해의 끝이어도 다음 날로 넘어간다', () => {
    expect(kstNextDayStartIso('2026-11-30')).toBe('2026-11-30T15:00:00.000Z');
    expect(kstNextDayStartIso('2026-12-31')).toBe('2026-12-31T15:00:00.000Z');
  });

  it('날짜 모양이 아니거나 없는 날짜이면 null이다', () => {
    expect(kstDayStartIso('')).toBeNull();
    expect(kstDayStartIso('2026-13-01')).toBeNull();
    expect(kstNextDayStartIso('11/05/2026')).toBeNull();
  });

  it('빈 조건은 아무 값도 담지 않는다', () => {
    expect(toAuditQuery(EMPTY_AUDIT_FORM)).toEqual({
      ok: true,
      query: { targetType: undefined, adminId: undefined, from: undefined, to: undefined },
    });
  });

  it('모든 조건을 서버가 받는 값으로 바꾼다', () => {
    expect(
      toAuditQuery({ targetType: 'MEMBER_REPORT', adminId: ' 7 ', startDate: '2026-11-01', endDate: '2026-11-05' }),
    ).toEqual({
      ok: true,
      query: {
        targetType: 'MEMBER_REPORT',
        adminId: 7,
        from: '2026-10-31T15:00:00.000Z',
        to: '2026-11-05T15:00:00.000Z',
      },
    });
  });

  it('시작일과 종료일이 같은 날이면 그날 하루를 뜻한다', () => {
    const result = toAuditQuery({ ...EMPTY_AUDIT_FORM, startDate: '2026-11-05', endDate: '2026-11-05' });
    expect(result).toMatchObject({
      ok: true,
      query: { from: '2026-11-04T15:00:00.000Z', to: '2026-11-05T15:00:00.000Z' },
    });
  });

  it('종료일이 시작일보다 앞서면 오류다', () => {
    expect(toAuditQuery({ ...EMPTY_AUDIT_FORM, startDate: '2026-11-05', endDate: '2026-11-04' })).toEqual({
      ok: false,
      errors: { endDate: '종료일은 시작일과 같거나 뒤여야 해요.' },
    });
  });

  it.each(['0', '-3', '1.5', 'abc', '99999999999999999999'])('관리자 번호 %s는 오류다', (adminId) => {
    expect(toAuditQuery({ ...EMPTY_AUDIT_FORM, adminId })).toEqual({
      ok: false,
      errors: { adminId: '관리자 번호는 1 이상의 숫자로 적어 주세요.' },
    });
  });

  it('모르는 대상 종류는 조건에서 뺀다', () => {
    expect(toAuditQuery({ ...EMPTY_AUDIT_FORM, targetType: 'NOPE' })).toMatchObject({
      ok: true,
      query: { targetType: undefined },
    });
  });
});
