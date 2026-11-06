import { describe, expect, it } from 'vitest';
import { formatKstDateTimeWithYear } from './datetime';

describe('formatKstDateTimeWithYear', () => {
  it('연도를 한국 시간 기준으로 붙인다', () => {
    expect(formatKstDateTimeWithYear('2026-11-05T05:30:00Z')).toBe('2026년 11월 5일 (목) 14:30');
    expect(formatKstDateTimeWithYear('2026-12-31T16:00:00Z')).toBe('2027년 1월 1일 (금) 01:00');
  });
});
