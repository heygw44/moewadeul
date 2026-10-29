import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from '../../api/client';
import { useDraftCapture, useRestoredDraft } from './formDraft';
import { SessionProvider, useSession } from './session';
import { onSignedOut } from './signedOut';

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

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function Probe() {
  const session = useSession();
  return <p data-testid="probe">{session.status}</p>;
}

describe('SessionProvider', () => {
  let container: HTMLDivElement;
  let root: Root;

  async function mount() {
    await act(async () => {
      root.render(
        <SessionProvider>
          <Probe />
        </SessionProvider>,
      );
    });
  }

  async function advance(ms: number) {
    await act(async () => {
      await vi.advanceTimersByTimeAsync(ms);
    });
  }

  const probe = () => container.querySelector('[data-testid="probe"]')?.textContent ?? null;
  const alertText = () => container.querySelector('[role="alert"]')?.textContent ?? null;
  const retryButton = () => [...container.querySelectorAll('button')].find((b) => b.textContent === '다시 시도');

  beforeEach(() => {
    vi.useFakeTimers();
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('/api/me가 계속 5xx여도 로그인하지 않은 상태로 두지 않고 안내와 다시 시도 버튼을 보여 준다', async () => {
    const fetchMock = vi.fn(async () => json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 't' }));
    vi.stubGlobal('fetch', fetchMock);

    await mount();
    expect(probe()).toBe('loading');
    await advance(3000);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(alertText()).toContain('로그인 상태를 확인하지 못했어요');
    expect(probe()).toBeNull();
    expect(retryButton()).toBeDefined();
  });

  it('응답이 오지 않아 제한 시간을 넘겨도 로딩을 끝내고 안내를 보여 준다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        (_url: string, init?: RequestInit) =>
          new Promise<Response>((_resolve, reject) => {
            init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
          }),
      ),
    );

    await mount();
    await advance(14_999);
    expect(probe()).toBe('loading');
    await advance(1);

    expect(alertText()).toContain('로그인 상태를 확인하지 못했어요');
  });

  it('다시 시도하면 확인 중 표시를 거쳐, 이번에 성공하면 앱 화면과 로그인한 상태로 돌아온다', async () => {
    let healthy = false;
    const fetchMock = vi.fn(async () => (healthy ? json(200, ME) : json(503, { code: 'SERVICE_UNAVAILABLE' })));
    vi.stubGlobal('fetch', fetchMock);
    await mount();
    await advance(3000);
    expect(alertText()).not.toBeNull();

    healthy = true;
    act(() => retryButton()?.click());
    expect(retryButton()?.getAttribute('aria-busy')).toBe('true');
    await advance(0);

    expect(alertText()).toBeNull();
    expect(probe()).toBe('authenticated');
  });

  it('서버가 401로 답하면 안내 없이 로그인하지 않은 상태가 된다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(401, { code: 'UNAUTHORIZED', message: 'x', traceId: 't' })),
    );

    await mount();
    await advance(0);

    expect(alertText()).toBeNull();
    expect(probe()).toBe('anonymous');
  });

  describe('세션이 끊길 때 구독자에게 알린다', () => {
    function Controls() {
      const session = useSession();
      return (
        <>
          <button type="button" data-action="refresh" onClick={() => void session.refresh().catch(() => null)}>
            세션 확인
          </button>
          <button type="button" data-action="logout" onClick={() => void session.logout()}>
            로그아웃
          </button>
        </>
      );
    }

    async function mountControls() {
      await act(async () => {
        root.render(
          <SessionProvider>
            <Controls />
          </SessionProvider>,
        );
      });
      await advance(0);
    }

    async function click(action: 'refresh' | 'logout') {
      await act(async () => {
        container.querySelector<HTMLButtonElement>(`[data-action="${action}"]`)?.click();
      });
      await advance(0);
    }

    it('로그인한 채 요청이 401을 받으면 알린다', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn(async (url: string) =>
          url === '/api/me' ? json(200, ME) : json(401, { code: 'AUTHENTICATION_REQUIRED', message: 'x' }),
        ),
      );
      const listener = vi.fn();
      const unsubscribe = onSignedOut(listener);
      await mountControls();

      await act(async () => {
        await apiFetch('/api/spots').catch(() => null);
      });

      expect(listener).toHaveBeenCalledTimes(1);
      unsubscribe();
    });

    it('세션을 다시 확인해 로그인하지 않은 상태가 되면(탈퇴 뒤) 알린다', async () => {
      let signedIn = true;
      vi.stubGlobal(
        'fetch',
        vi.fn(async () => (signedIn ? json(200, ME) : json(401, { code: 'AUTHENTICATION_REQUIRED', message: 'x' }))),
      );
      const listener = vi.fn();
      const unsubscribe = onSignedOut(listener);
      await mountControls();
      await click('refresh');
      expect(listener).not.toHaveBeenCalled();
      signedIn = false;

      await click('refresh');

      expect(listener).toHaveBeenCalledTimes(1);
      unsubscribe();
    });

    it('로그아웃하면 알리고, 구독을 풀면 더는 알리지 않는다', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn(async (url: string) => (url === '/api/me' ? json(200, ME) : json(204, null))),
      );
      const kept = vi.fn();
      const dropped = vi.fn();
      const unsubscribeKept = onSignedOut(kept);
      onSignedOut(dropped)();
      await mountControls();

      await click('logout');

      expect(kept).toHaveBeenCalledTimes(1);
      expect(dropped).not.toHaveBeenCalled();
      unsubscribeKept();
    });
  });

  describe('임시 저장한 양식', () => {
    const DRAFT_PATH = '/memories/new';

    function Capture() {
      useDraftCapture(DRAFT_PATH, ME.memberId, { title: '쓰던 글' });
      return null;
    }

    function Restored() {
      const draft = useRestoredDraft<{ title: string }>(DRAFT_PATH, ME.memberId);
      return <p data-testid="draft">{draft?.title ?? '없음'}</p>;
    }

    const draftText = () => container.querySelector('[data-testid="draft"]')?.textContent;

    function Logout() {
      const session = useSession();
      return (
        <button type="button" onClick={() => void session.logout()}>
          로그아웃
        </button>
      );
    }

    async function mountCapture() {
      await act(async () => {
        root.render(
          <SessionProvider>
            <Capture />
            <Logout />
          </SessionProvider>,
        );
      });
      await advance(0);
    }

    async function remountRestored() {
      await act(async () => {
        root.render(
          <SessionProvider>
            <Restored />
          </SessionProvider>,
        );
      });
      await advance(0);
    }

    function stubSignedInWith401OnApi() {
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
    }

    it('401로 로그인이 풀리면 쓰던 양식을 남겨 다시 로그인한 같은 회원에게 돌려준다', async () => {
      stubSignedInWith401OnApi();
      await mountCapture();
      await act(async () => {
        await apiFetch('/api/spots').catch(() => null);
      });

      await remountRestored();

      expect(draftText()).toBe('쓰던 글');
    });

    it('401 뒤에 로그아웃하면 남겨 둔 양식을 지운다', async () => {
      stubSignedInWith401OnApi();
      await mountCapture();
      await act(async () => {
        await apiFetch('/api/spots').catch(() => null);
      });
      await act(async () => {
        container.querySelector('button')?.click();
      });
      await advance(0);

      await remountRestored();

      expect(draftText()).toBe('없음');
    });
  });
});
