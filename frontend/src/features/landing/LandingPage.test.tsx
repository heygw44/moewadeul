import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LandingPage } from './LandingPage';

const announcement = vi.hoisted(() => ({
  program: null as null | { programId: number; title: string; remainingSeats: number },
}));

vi.mock('../program/useOpenProgramAnnouncement', () => ({
  useOpenProgramAnnouncement: () => announcement.program,
}));

vi.mock('../../api/notifications', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/notifications')>()),
  fetchUnreadCount: vi.fn(async () => ({ count: 0 })),
}));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => ({ status: 'anonymous', me: null, refresh: vi.fn(), logout: vi.fn() }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

describe('첫 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const link = (label: string) =>
    [...container.querySelectorAll('a')].find((element) => element.textContent?.trim() === label);

  async function open(url = '/') {
    window.history.replaceState(null, '', url);
    await act(async () => root.render(<LandingPage />));
  }

  beforeEach(() => {
    announcement.program = null;
    vi.stubGlobal('matchMedia', (media: string) => ({
      media,
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));
    vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('서비스를 한 줄로 알리는 제목과 지도 열기 버튼이 보이고 문서 제목이 바뀐다', async () => {
    await open();

    const title = container.querySelector('h1')?.textContent ?? '';
    expect(title).toContain('잘 곳과 함께 갈 사람을');
    expect(title).toContain('한 번에 찾아요');
    expect(link('지도 열기')?.getAttribute('href')).toBe('/map');
    expect(document.title).toBe('뫼와들 · 잘 곳과 함께 갈 사람을 한 번에 찾아요');
  });

  it('주요 서비스로 가는 링크가 모두 있다', async () => {
    await open();

    const services = container.querySelector('section[aria-labelledby="services-title"]')!;
    const hrefs = [...services.querySelectorAll('a')].map((element) => element.getAttribute('href'));

    expect(hrefs).toEqual(['/map', '/basecamps', '/programs', '/memories', '/community']);
  });

  it('로그인하지 않은 방문자에게는 로그인과 회원가입 링크가 보인다', async () => {
    await open();

    expect(link('로그인')?.getAttribute('href')).toBe('/login?next=%2F');
    expect(link('회원가입')?.getAttribute('href')).toBe('/signup?next=%2F');
  });

  it('열린 공식 행사가 있으면 상단에 제목과 남은 자리가 보이고, 없으면 안내 막대가 없다', async () => {
    await open();
    expect(container.textContent).not.toContain('신청을 받고 있어요');

    announcement.program = { programId: 7, title: '가을 능선 걷기', remainingSeats: 3 };
    await open();

    const bar = [...container.querySelectorAll('a')].find((element) => element.textContent?.includes('가을 능선 걷기'));
    expect(bar?.textContent).toContain('신청을 받고 있어요');
    expect(bar?.textContent).toContain('남은 자리 3');
    expect(bar?.getAttribute('href')).toBe('/programs/7');
  });

  it('지역을 적고 지도에서 보기를 누르면 그 지역을 검색어로 달아 지도로 이동한다', async () => {
    await open();
    const region = container.querySelector<HTMLInputElement>('input[autocomplete="off"]')!;

    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.call(region, '설악산 대청봉');
      region.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await act(async () =>
      [...container.querySelectorAll('button')].find((b) => b.textContent === '지도에서 보기')?.click(),
    );

    expect(window.location.pathname).toBe('/map');
    expect(new URLSearchParams(window.location.search).get('q')).toBe('설악산 대청봉');
  });

  it('지역을 비우고 지도에서 보기를 누르면 검색어 없이 지도로 이동한다', async () => {
    await open();
    const region = container.querySelector<HTMLInputElement>('input[autocomplete="off"]')!;

    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.call(region, '   ');
      region.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await act(async () =>
      [...container.querySelectorAll('button')].find((b) => b.textContent === '지도에서 보기')?.click(),
    );

    expect(window.location.pathname).toBe('/map');
    expect(window.location.search).toBe('');
  });
});
