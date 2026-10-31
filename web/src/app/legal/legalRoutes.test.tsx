import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SessionProvider } from '../session/session';
import NotFoundPage from '../NotFoundPage';
import { Router } from '../router';
import { routes } from '../routes';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

describe('법정 고지 페이지', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED' }), { status: 401 })),
    );
    window.scrollTo = vi.fn();
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  async function visit(pathname: string) {
    window.history.replaceState(null, '', pathname);
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <Router routes={routes} fallback={<NotFoundPage />} />
        </SessionProvider>,
      );
    });
    // 지연 로딩한 화면은 act 밖에서 커밋되고 제목 effect는 다음 태스크에 돈다. 짧게 폴링해야 그 틈에 제목을 읽는 단언이 매번 드러난다.
    await vi.waitFor(() => expect(container.querySelector('h1')).not.toBeNull(), { interval: 1 });
  }

  it('/privacy는 개인정보 처리방침을 보여 준다', async () => {
    await visit('/privacy');

    expect(container.querySelector('h1')?.textContent).toBe('개인정보 처리방침');
    expect(container.textContent).toContain('2026년 10월 29일');
    expect(container.textContent).toContain('개인정보 보호책임자');
    expect(container.textContent).toContain('moewadeul@gmail.com');
    await vi.waitFor(() => expect(document.title).toBe('개인정보 처리방침 · 뫼와들'));
  });

  it('/privacy의 목차 링크가 각 조항을 가리킨다', async () => {
    await visit('/privacy');

    const tocLinks = [...container.querySelectorAll('nav a')].filter((a) => a.getAttribute('href')?.startsWith('#'));
    expect(tocLinks.length).toBeGreaterThan(5);
    for (const a of tocLinks) {
      const id = a.getAttribute('href')!.slice(1);
      expect(container.querySelector(`section#${id}`)).not.toBeNull();
    }
  });

  it('/terms는 이용약관을 보여 준다', async () => {
    await visit('/terms');

    expect(container.querySelector('h1')?.textContent).toBe('이용약관');
    expect(container.textContent).toContain('2026년 10월 29일');
    await vi.waitFor(() => expect(document.title).toBe('이용약관 · 뫼와들'));
  });
});
