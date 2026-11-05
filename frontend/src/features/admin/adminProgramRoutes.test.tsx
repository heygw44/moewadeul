import { isValidElement } from 'react';
import { describe, expect, it } from 'vitest';
import NotFoundPage from '../../app/NotFoundPage';
import { findRoute } from '../../app/router';
import { routes } from '../../app/routes';

function rendersNotFound(pathname: string): boolean {
  const route = findRoute(routes, pathname);
  if (!route) throw new Error(`${pathname}에 맞는 라우트가 없다`);
  const patternParts = route.path.split('/').filter(Boolean);
  const pathParts = pathname.split('/').filter(Boolean);
  const params = Object.fromEntries(
    patternParts.flatMap((part, index) => (part.startsWith(':') ? [[part.slice(1), pathParts[index] ?? '']] : [])),
  );
  const element = route.render(params);
  return isValidElement(element) && element.type === NotFoundPage;
}

describe('관리자 행사 라우트', () => {
  it('등록 주소는 행사 번호 라우트가 아니라 정적 라우트로 이어진다', () => {
    expect(findRoute(routes, '/admin/programs/new')?.path).toBe('/admin/programs/new');
  });

  it('목록, 수정, 신청자 주소가 각자의 라우트로 이어진다', () => {
    expect(findRoute(routes, '/admin/programs')?.path).toBe('/admin/programs');
    expect(findRoute(routes, '/admin/programs/7/edit')?.path).toBe('/admin/programs/:programId/edit');
    expect(findRoute(routes, '/admin/programs/7/applications')?.path).toBe('/admin/programs/:programId/applications');
  });

  it('행사 번호가 양의 정수가 아니면 찾을 수 없는 화면을 낸다', () => {
    for (const pathname of [
      '/admin/programs/abc/edit',
      '/admin/programs/0/edit',
      '/admin/programs/-1/applications',
      '/admin/programs/1.5/applications',
    ]) {
      expect(rendersNotFound(pathname), pathname).toBe(true);
    }
  });

  it('올바른 행사 번호면 찾을 수 없는 화면이 아니다', () => {
    for (const pathname of ['/admin/programs/7/edit', '/admin/programs/7/applications']) {
      expect(rendersNotFound(pathname), pathname).toBe(false);
    }
  });
});
