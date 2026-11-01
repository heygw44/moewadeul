import { describe, expect, it } from 'vitest';
import { parseHttpsUrl } from './url';

describe('parseHttpsUrl', () => {
  it('https 주소만 URL로 돌려준다', () => {
    expect(parseHttpsUrl('https://open.kakao.com/o/abc')?.href).toBe('https://open.kakao.com/o/abc');
  });

  it('http, 스크립트 주소, 주소가 아닌 글은 null이다', () => {
    expect(parseHttpsUrl('http://example.com')).toBeNull();
    expect(parseHttpsUrl('javascript:alert(1)')).toBeNull();
    expect(parseHttpsUrl('카톡 아이디 moewadeul')).toBeNull();
  });
});
