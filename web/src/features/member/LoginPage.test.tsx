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
    let loggedIn = false;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url === '/api/auth/login') {
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
});
