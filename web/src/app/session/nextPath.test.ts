import { describe, expect, it } from 'vitest';
import { safeInternalPath, safeNextPath } from './nextPath';

const query = (next: string) => `?next=${encodeURIComponent(next)}`;

describe('safeNextPath', () => {
  it('같은 사이트 경로는 경로·쿼리·조각을 그대로 돌려준다', () => {
    expect(safeNextPath(query('/spots/1?x=1#a'))).toBe('/spots/1?x=1#a');
    expect(safeNextPath(query('/map'))).toBe('/map');
  });

  it('next가 없거나 비어 있으면 null을 돌려준다', () => {
    expect(safeNextPath('')).toBeNull();
    expect(safeNextPath('?next=')).toBeNull();
  });

  it.each([
    ['탭', '?next=/%09/evil.com'],
    ['줄바꿈', '?next=/%0a/evil.com'],
    ['캐리지 리턴', '?next=/%0d/evil.com'],
    ['NUL', '?next=/%00/evil.com'],
    ['DEL', '?next=/%7f/evil.com'],
  ])('%s 문자가 섞인 경로는 null을 돌려준다', (_name, search) => {
    expect(safeNextPath(search)).toBeNull();
  });

  it.each([
    '//evil.com',
    '/\\evil.com',
    '/foo\\bar',
    'https://evil.com',
    'javascript:alert(1)',
    'evil.com',
    '/..//evil.com',
  ])('다른 사이트로 갈 수 있는 값 %s는 null을 돌려준다', (next) => {
    expect(safeNextPath(query(next))).toBeNull();
  });
});

describe('safeInternalPath', () => {
  it('같은 사이트 경로는 그대로 돌려준다', () => {
    expect(safeInternalPath('/basecamps/3?tab=applications')).toBe('/basecamps/3?tab=applications');
  });

  it.each(['/\t/evil.com', '/\n/evil.com', '//evil.com', '/\\evil.com', '/..//evil.com', 'https://evil.com'])(
    '다른 사이트로 갈 수 있는 값 %j는 null을 돌려준다',
    (link) => {
      expect(safeInternalPath(link)).toBeNull();
    },
  );
});
