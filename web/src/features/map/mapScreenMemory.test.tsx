import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from '../../api/client';
import { SessionProvider, useSession } from '../../app/session/session';
import { readMapScreen, rememberMapScreen } from './mapScreenMemory';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const ME = {
  memberId: 1,
  email: 'a@b.c',
  nickname: '산사람',
  status: 'ACTIVE',
  role: 'USER',
  selfAgeGroup: null,
  selfGender: null,
  identityVerified: false,
  trustLevel: 1,
};

const REMEMBERED = { radiusSearch: { center: { lat: 37.5, lng: 127 }, radiusKm: 5, label: '서울' } };

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function LogoutButton() {
  const session = useSession();
  return (
    <button type="button" onClick={() => void session.logout()}>
      로그아웃
    </button>
  );
}

describe('지도 화면 기억은 세션이 끊기면 지워진다', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(async () => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url === '/api/me'
          ? json(200, ME)
          : url === '/api/auth/logout'
            ? json(204, null)
            : json(401, { code: 'AUTHENTICATION_REQUIRED', message: 'x' }),
      ),
    );
    await act(async () => {
      root.render(
        <SessionProvider>
          <LogoutButton />
        </SessionProvider>,
      );
    });
    rememberMapScreen(REMEMBERED);
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  it('로그아웃하면 지운다', async () => {
    expect(readMapScreen().radiusSearch).not.toBeNull();

    await act(async () => {
      container.querySelector('button')?.click();
    });

    expect(readMapScreen().radiusSearch).toBeNull();
  });

  it('로그인한 채 요청이 401을 받으면 지운다', async () => {
    await act(async () => {
      await apiFetch('/api/spots').catch(() => null);
    });

    expect(readMapScreen().radiusSearch).toBeNull();
  });
});
