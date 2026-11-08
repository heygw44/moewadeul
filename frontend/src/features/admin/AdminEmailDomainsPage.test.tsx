import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AdminDisposableEmailDomain } from '../../api/types';
import { SessionProvider } from '../../app/session/session';
import { AdminEmailDomainsPage } from './AdminEmailDomainsPage';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const ME = {
  memberId: 1,
  email: 'admin@b.c',
  nickname: '관리자',
  status: 'ACTIVE',
  role: 'ADMIN',
  selfAgeGroup: null,
  selfGender: null,
  identityVerified: true,
  trustLevel: 1,
};

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('관리자 가입 차단 도메인', () => {
  let container: HTMLDivElement;
  let root: Root;
  let domains: AdminDisposableEmailDomain[];
  let posted: unknown[];
  let deleted: string[];
  let addReply: (() => Response) | null;

  const text = (element: Element | null) => element?.textContent ?? '';
  const button = (label: string, scope: ParentNode = container) =>
    [...scope.querySelectorAll('button')].find((element) => element.textContent === label);
  const input = () => container.querySelector('input') as HTMLInputElement;

  async function type(value: string) {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
    await act(async () => {
      setter?.call(input(), value);
      input().dispatchEvent(new Event('input', { bubbles: true }));
    });
  }

  beforeEach(async () => {
    container = document.createElement('div');
    document.body.appendChild(container);
    domains = [{ id: 3, domain: 'old.example', createdAt: '2026-11-01T03:00:00Z' }];
    posted = [];
    deleted = [];
    addReply = null;
    window.history.replaceState(null, '', '/admin/email-domains');
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.open = true;
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.open = false;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        const { pathname } = new URL(url, 'http://localhost');
        if (pathname === '/api/me') return json(200, ME);
        if (pathname === '/api/admin/disposable-email-domains' && init?.method === 'POST') {
          const body = JSON.parse(String(init.body)) as { domain: string };
          posted.push(body);
          if (addReply) return addReply();
          const added = { id: 9, domain: body.domain.toLowerCase(), createdAt: '2026-11-08T03:00:00Z' };
          domains = [added, ...domains];
          return json(201, added);
        }
        if (pathname.startsWith('/api/admin/disposable-email-domains/') && init?.method === 'DELETE') {
          const id = pathname.split('/').pop() ?? '';
          deleted.push(id);
          domains = domains.filter((domain) => String(domain.id) !== id);
          return new Response(null, { status: 204 });
        }
        if (pathname === '/api/admin/disposable-email-domains') return json(200, domains);
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <AdminEmailDomainsPage />
        </SessionProvider>,
      );
    });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  it('관리자 메뉴에 가입 차단 도메인 탭이 있고 지금 보는 탭으로 표시된다', () => {
    const link = [...container.querySelectorAll('a')].find((element) => element.textContent === '가입 차단 도메인');
    expect(link?.getAttribute('href')).toBe('/admin/email-domains');
    expect(link?.getAttribute('aria-current')).toBe('page');
  });

  it('도메인을 추가하면 목록 맨 위에 보이고 입력란이 비워진다', async () => {
    await type(' Throwaway.Example ');
    await act(async () => button('추가')?.click());

    expect(posted).toEqual([{ domain: 'Throwaway.Example' }]);
    const rows = [...container.querySelectorAll('ul[aria-label="추가한 도메인"] li')].map((row) => text(row));
    expect(rows[0]).toContain('throwaway.example');
    expect(rows[1]).toContain('old.example');
    expect(input().value).toBe('');
  });

  it('도메인 형식이 아니면 보내지 않고 입력란 아래에 알린다', async () => {
    await type('user@example.com');
    await act(async () => button('추가')?.click());

    expect(posted).toEqual([]);
    expect(text(container)).toContain('도메인 형식이 아니에요.');
  });

  it('이미 막고 있는 도메인이면 서버가 알려 준 이유를 보인다', async () => {
    addReply = () =>
      json(409, { code: 'MEMBER_DISPOSABLE_DOMAIN_ALREADY_BLOCKED', message: '이미 가입을 막고 있는 도메인이에요.' });
    await type('mailinator.com');
    await act(async () => button('추가')?.click());

    expect(text(container.querySelector('[role="alert"]'))).toContain('이미 가입을 막고 있는 도메인이에요.');
  });

  it('삭제를 확인하면 목록에서 빠진다', async () => {
    await act(async () => button('삭제')?.click());
    await act(async () => button('삭제', container.querySelector('dialog') ?? container)?.click());

    expect(deleted).toEqual(['3']);
    expect(container.querySelector('dialog')).toBeNull();
    expect(text(container)).not.toContain('old.example');
    expect(text(container)).toContain('직접 추가한 도메인이 없어요');
  });
});
