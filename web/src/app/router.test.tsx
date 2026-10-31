import { act, lazy } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Link, Router } from './router';
import type { RouteDef } from './router';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

describe('Router 화면 전환 포커스', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    vi.stubGlobal('scrollTo', vi.fn());
    window.history.replaceState(null, '', '/');
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
    window.history.replaceState(null, '', '/');
  });

  async function mount(routes: RouteDef[]) {
    await act(async () => root.render(<Router routes={routes} fallback={<p>없는 화면</p>} />));
  }

  async function clickLink(name: string) {
    const link = [...container.querySelectorAll('a')].find((anchor) => anchor.textContent === name);
    await act(async () => link?.click());
  }

  const home: RouteDef = {
    path: '/',
    render: () => <Link to="/b">B로 가기</Link>,
  };

  it('링크로 화면을 옮기면 새 화면의 제목에 포커스가 간다', async () => {
    await mount([home, { path: '/b', render: () => <h1>B 화면</h1> }]);

    await clickLink('B로 가기');

    expect(document.activeElement).toBe(container.querySelector('h1'));
    expect(document.activeElement?.textContent).toBe('B 화면');
  });

  it('처음 화면을 열 때는 포커스를 옮기지 않는다', async () => {
    await mount([{ path: '/', render: () => <h1>A 화면</h1> }]);

    expect(document.activeElement).toBe(document.body);
  });

  it('제목이 없으면 main으로 포커스가 간다', async () => {
    await mount([home, { path: '/b', render: () => <main>본문</main> }]);

    await clickLink('B로 가기');

    expect(document.activeElement).toBe(container.querySelector('main'));
  });

  it('data-route-focus를 단 영역이 제목보다 먼저 포커스를 받는다', async () => {
    await mount([
      home,
      {
        path: '/b',
        render: () => (
          <>
            <h1>지도</h1>
            <section aria-label="장소 상세" data-route-focus>
              상세
            </section>
          </>
        ),
      },
    ]);

    await clickLink('B로 가기');

    expect(document.activeElement).toBe(container.querySelector('section'));
  });

  it('새 화면이 입력 칸에 포커스를 주면 그대로 둔다', async () => {
    await mount([
      home,
      {
        path: '/b',
        render: () => (
          <>
            <h1>로그인</h1>
            <input aria-label="이메일" autoFocus />
          </>
        ),
      },
    ]);

    await clickLink('B로 가기');

    expect(document.activeElement).toBe(container.querySelector('input'));
  });

  it('늦게 불러오는 화면은 그려진 뒤에 포커스를 받는다', async () => {
    let finishLoading: (module: { default: () => React.ReactNode }) => void = () => undefined;
    const Lazy = lazy(() => new Promise<{ default: () => React.ReactNode }>((resolve) => (finishLoading = resolve)));
    await mount([home, { path: '/b', render: () => <Lazy /> }]);

    await clickLink('B로 가기');
    expect(document.activeElement).toBe(document.body);

    await act(async () => finishLoading({ default: () => <h1>느린 화면</h1> }));

    expect(document.activeElement).toBe(container.querySelector('h1'));
  });
});
