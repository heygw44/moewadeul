import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SaveNoticeTray } from '../saveNotice/SaveNoticeTray';
import { SessionProvider, useSession } from './session';

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
const MINUTE = 60_000;

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function Probe() {
  return <p data-testid="probe">{useSession().status}</p>;
}

describe('세션 유지', () => {
  let container: HTMLDivElement;
  let root: Root;
  let fetchMock: ReturnType<typeof vi.fn>;
  let visibility: DocumentVisibilityState;

  const advance = (ms: number) => act(async () => void (await vi.advanceTimersByTimeAsync(ms)));
  const touch = () => act(async () => void window.dispatchEvent(new Event('pointerdown')));
  const setVisibility = (next: DocumentVisibilityState) =>
    act(async () => {
      visibility = next;
      document.dispatchEvent(new Event('visibilitychange'));
    });
  const probe = () => container.querySelector('[data-testid="probe"]')?.textContent;

  beforeEach(async () => {
    vi.useFakeTimers();
    visibility = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility);
    fetchMock = vi.fn(async () => json(200, ME));
    vi.stubGlobal('fetch', fetchMock);
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <Probe />
          <SaveNoticeTray />
        </SessionProvider>,
      );
    });
    fetchMock.mockClear();
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('활성 탭에서 조작이 있으면 10분마다 한 번 /api/me를 부른다', async () => {
    // given
    await touch();

    // when
    await advance(10 * MINUTE);
    await touch();
    await advance(10 * MINUTE);

    // then
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('조작이 없는 30분 동안에는 부르지 않는다', async () => {
    // when
    await advance(30 * MINUTE);

    // then
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('숨은 탭에서는 부르지 않고, 다시 보이면 10분이 지났을 때 한 번 부른다', async () => {
    // given
    await touch();
    await setVisibility('hidden');

    // when
    await advance(30 * MINUTE);
    expect(fetchMock).not.toHaveBeenCalled();
    await setVisibility('visible');

    // then
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('401이면 비로그인으로 바꾸고 로그인 만료 안내를 한 번 보인다', async () => {
    // given
    fetchMock.mockImplementation(async () => json(401, { code: 'UNAUTHORIZED', message: 'x', traceId: 't' }));
    await touch();

    // when
    await advance(10 * MINUTE);
    await advance(10 * MINUTE);

    // then
    expect(probe()).toBe('anonymous');
    expect(container.textContent?.split('로그인이 만료되었습니다.').length).toBe(2);
  });
});
