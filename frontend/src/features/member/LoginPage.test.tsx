import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadPage } from '../../app/router';
import { LoginPage } from './LoginPage';
import { SessionProvider } from '../../app/session/session';

vi.mock('../../app/router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/router')>()),
  loadPage: vi.fn(),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const ME = {
  memberId: 1,
  email: 'hiker@gmail.com',
  nickname: '새벽능선',
  status: 'ACTIVE',
  role: 'USER',
  selfAgeGroup: null,
};

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('로그인 성공 뒤 회원 정보를 다시 읽는 단계', () => {
  let container: HTMLDivElement;
  let root: Root;
  let meAfterLogin: () => Response;
  let loginReply: (() => Response) | null;

  async function fill(element: HTMLInputElement | HTMLSelectElement, value: string, eventType: 'input' | 'change') {
    const prototype = element instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
    await act(async () => {
      Object.getOwnPropertyDescriptor(prototype, 'value')?.set?.call(element, value);
      element.dispatchEvent(new Event(eventType, { bubbles: true }));
    });
  }

  async function logIn() {
    await fill(container.querySelector<HTMLInputElement>('[name="email"]')!, 'hiker', 'input');
    await fill(container.querySelector('select')!, 'gmail.com', 'change');
    await fill(container.querySelector<HTMLInputElement>('[name="password"]')!, 'Valid-pass-1!', 'input');
    const button = [...container.querySelectorAll('button')].find((element) => element.textContent === '로그인');
    await act(async () => button!.click());
  }

  beforeEach(async () => {
    window.history.replaceState(null, '', '/login');
    document.cookie = 'XSRF-TOKEN=token';
    vi.mocked(loadPage).mockClear();
    loginReply = null;
    let loggedIn = false;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url === '/api/auth/login') {
          if (loginReply) return loginReply();
          loggedIn = true;
          return json(200, { memberId: 1, nickname: '새벽능선', status: 'ACTIVE', role: 'USER' });
        }
        if (url === '/api/me') return loggedIn ? meAfterLogin() : json(401, { code: 'AUTHENTICATION_REQUIRED' });
        return json(404, { code: 'NOT_FOUND', message: '없음' });
      }),
    );
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <LoginPage />
        </SessionProvider>,
      );
    });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('회원 정보를 읽으면 화면만 바꿔 지도로 간다', async () => {
    meAfterLogin = () => json(200, ME);

    await logIn();

    expect(window.location.pathname).toBe('/map');
    expect(loadPage).not.toHaveBeenCalled();
  });

  it('로그인은 됐는데 회원 정보를 읽지 못하면 실패 문구 없이 지도 페이지를 새로 연다', async () => {
    meAfterLogin = () => json(500, { code: 'INTERNAL_ERROR', message: '서버 오류' });

    await logIn();

    expect(container.querySelector('[role="alert"]')).toBeNull();
    expect(loadPage).toHaveBeenCalledWith('/map');
  });

  it('이용이 정지된 계정이면 풀리는 때와 이의를 받을 운영자 메일을 함께 보인다', async () => {
    loginReply = () =>
      json(403, {
        code: 'MEMBER_SUSPENDED',
        message: '이용이 정지된 계정이에요. 정지가 풀린 뒤 다시 로그인해 주세요.',
        suspendedUntil: '2026-11-15T03:00:00Z',
      });

    await logIn();

    const alert = container.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('까지 이용이 제한돼요.');
    expect(alert?.textContent).toContain('제재에 이의가 있으면 moewadeul@gmail.com으로 알려 주세요.');
    expect(alert?.querySelector('a')?.getAttribute('href')).toBe('mailto:moewadeul@gmail.com');
  });
});
