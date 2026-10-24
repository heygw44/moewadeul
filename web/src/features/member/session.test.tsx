import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from '../../api/client';
import { readMapScreen, rememberMapScreen } from '../spot/mapScreenMemory';
import { SessionProvider, useSession } from './session';

// React가 act 밖의 상태 변경을 경고하지 않도록 테스트 환경임을 알린다.
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

// 로그인이 필요한 화면처럼 세션 상태만 읽어 그린다.
function Probe() {
  const session = useSession();
  return <p data-testid="probe">{session.status}</p>;
}

// 화면 안에서 세션을 다시 확인하는 버튼이다(탈퇴한 뒤 화면이 하는 일과 같다).
function RefreshButton() {
  const session = useSession();
  return (
    <button type="button" onClick={() => void session.refresh().catch(() => null)}>
      세션 확인
    </button>
  );
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

  describe('앞 회원의 지도 화면 기억', () => {
    async function mountWithRefreshButton() {
      await act(async () => {
        root.render(
          <SessionProvider>
            <RefreshButton />
          </SessionProvider>,
        );
      });
      await advance(0);
    }

    const REMEMBERED = { radiusSearch: { center: { lat: 37.5, lng: 127 }, radiusKm: 5, label: '서울' } };

    it('로그인한 채 요청이 401을 받으면 지운다', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn(async (url: string) =>
          url === '/api/me' ? json(200, ME) : json(401, { code: 'AUTHENTICATION_REQUIRED', message: 'x' }),
        ),
      );
      await mountWithRefreshButton();
      rememberMapScreen(REMEMBERED);

      await act(async () => {
        await apiFetch('/api/spots').catch(() => null);
      });

      expect(readMapScreen().radiusSearch).toBeNull();
    });

    it('세션을 다시 확인해 로그인하지 않은 상태가 되면(탈퇴 뒤) 지운다', async () => {
      let signedIn = true;
      vi.stubGlobal(
        'fetch',
        vi.fn(async () => (signedIn ? json(200, ME) : json(401, { code: 'AUTHENTICATION_REQUIRED', message: 'x' }))),
      );
      await mountWithRefreshButton();
      rememberMapScreen(REMEMBERED);
      signedIn = false;

      await act(async () => {
        container.querySelector('button')?.click();
      });
      await advance(0);

      expect(readMapScreen().radiusSearch).toBeNull();
    });
  });
});
