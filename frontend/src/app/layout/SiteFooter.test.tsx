import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SessionProvider } from '../session/session';
import { SiteFooter } from './SiteFooter';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

describe('푸터의 법정 고지', () => {
  let container: HTMLDivElement;
  let root: Root;

  const link = (label: string) =>
    [...container.querySelectorAll('a')].find((element) => element.textContent === label) ?? null;

  beforeEach(async () => {
    container = document.createElement('div');
    document.body.appendChild(container);
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED' }), { status: 401 })),
    );
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <SiteFooter />
        </SessionProvider>,
      );
    });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  it('개인정보 처리방침과 이용약관 링크가 있다', () => {
    expect(link('개인정보 처리방침')?.getAttribute('href')).toBe('/privacy');
    expect(link('이용약관')?.getAttribute('href')).toBe('/terms');
  });

  it('개인정보 처리방침 링크는 다른 링크보다 굵고 진하게 보인다', () => {
    const privacy = link('개인정보 처리방침');
    const terms = link('이용약관');

    expect(privacy?.className).toContain('font-bold');
    expect(privacy?.className).toContain('text-ink');
    expect(privacy?.className).not.toContain('text-ink-muted');
    expect(terms?.className).not.toContain('font-bold');
  });

  it('문의 메일 링크가 있다', () => {
    expect(link('moewadeul@gmail.com')?.getAttribute('href')).toBe('mailto:moewadeul@gmail.com');
  });

  it('운영자 정보와 기존 고지를 유지한다', () => {
    expect(container.textContent).toContain('개인이 운영하는 비영리 포트폴리오 서비스');
    expect(container.textContent).toContain('데이터 ·');
    expect(container.textContent).toContain('본인확인과 결제는 시연용이라 실제로 처리되지 않아요');
    expect(link('GitHub')?.getAttribute('href')).toBe('https://github.com/heygw44/moewadeul');
  });
});
