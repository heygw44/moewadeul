import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useLocation } from '../../app/router';
import { useRequireAuth } from './useRequireAuth';

const session = vi.hoisted(() => ({ current: { status: 'loading', me: null } as { status: string; me: unknown } }));

vi.mock('./session', () => ({ useSession: () => session.current }));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function Probe({ returnTo }: { returnTo?: string }) {
  useRequireAuth(returnTo);
  return null;
}

function Route({ returnTo }: { returnTo?: string }) {
  const { pathname } = useLocation();
  return pathname === '/login' ? null : <Probe returnTo={returnTo} />;
}

describe('useRequireAuth', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    container = document.createElement('div');
    root = createRoot(container);
    window.history.replaceState(null, '', '/me/notifications?tab=all');
  });

  afterEach(() => act(() => root.unmount()));

  async function mount(returnTo?: string) {
    await act(async () => root.render(<Route returnTo={returnTo} />));
  }

  it('로그인하지 않았으면 지금 주소를 next로 달아 로그인 화면으로 바꿔 보낸다', async () => {
    const historyLength = window.history.length;
    session.current = { status: 'anonymous', me: null };

    await mount();

    expect(window.location.pathname).toBe('/login');
    expect(new URLSearchParams(window.location.search).get('next')).toBe('/me/notifications?tab=all');
    expect(window.history.length).toBe(historyLength);
  });

  it('돌아올 주소를 따로 주면 그 주소를 next로 쓴다', async () => {
    session.current = { status: 'anonymous', me: null };

    await mount('/me/settings');

    expect(new URLSearchParams(window.location.search).get('next')).toBe('/me/settings');
  });

  it('세션을 확인하는 중이거나 로그인했으면 주소를 그대로 둔다', async () => {
    session.current = { status: 'loading', me: null };
    await mount();
    expect(window.location.pathname).toBe('/me/notifications');

    session.current = { status: 'authenticated', me: {} };
    await mount();
    expect(window.location.pathname).toBe('/me/notifications');
  });
});
