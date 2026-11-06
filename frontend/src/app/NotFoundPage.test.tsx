import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import NotFoundPage from './NotFoundPage';

vi.mock('../api/notifications', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/notifications')>()),
  fetchUnreadCount: vi.fn(async () => ({ count: 0 })),
}));

vi.mock('./session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./session/session')>()),
  useSession: () => ({ status: 'anonymous', me: null, refresh: vi.fn(), logout: vi.fn() }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

describe('없는 주소 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const link = (label: string) => [...container.querySelectorAll('a')].find((element) => element.textContent === label);

  beforeEach(async () => {
    vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    window.history.replaceState(null, '', '/no-such-page');
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    await act(async () => root.render(<NotFoundPage />));
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
  });

  it('페이지를 찾을 수 없다는 제목과 원인 안내가 보인다', () => {
    expect(container.querySelector('h1')?.textContent).toBe('페이지를 찾을 수 없어요');
    expect(container.querySelector('main')?.textContent).toContain('주소가 바뀌었거나 잘못 입력됐어요.');
  });

  it('지도와 첫 화면으로 가는 링크가 있다', () => {
    expect(link('지도로 가기')?.getAttribute('href')).toBe('/map');
    expect(link('첫 화면으로')?.getAttribute('href')).toBe('/');
  });

  it('지도로 가기를 누르면 지도 주소로 이동한다', async () => {
    await act(async () => link('지도로 가기')?.click());

    expect(window.location.pathname).toBe('/map');
  });

  it('첫 화면으로를 누르면 첫 화면 주소로 이동한다', async () => {
    await act(async () => link('첫 화면으로')?.click());

    expect(window.location.pathname).toBe('/');
  });
});
