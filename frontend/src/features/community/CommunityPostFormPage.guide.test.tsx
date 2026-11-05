import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SessionProvider } from '../../app/session/session';
import { notifySignedOut } from '../../app/session/signedOut';
import { CommunityPostFormPage } from './CommunityPostFormPage';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

describe('글쓰기 화면의 커뮤니티 안내', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('[F-29][CM-10] 동행은 베이스캠프에서 구하고 금전 요구는 신고하라는 안내가 보인다', async () => {
    notifySignedOut();
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url === '/api/me'
          ? new Response(JSON.stringify({ memberId: 1, nickname: '산사람', status: 'ACTIVE', role: 'USER' }), {
              status: 200,
              headers: { 'Content-Type': 'application/json' },
            })
          : new Response('{}', { status: 404, headers: { 'Content-Type': 'application/json' } }),
      ),
    );
    window.history.replaceState(null, '', '/community/new');
    const container = document.createElement('div');
    document.body.appendChild(container);
    const root = createRoot(container);

    await act(async () => {
      root.render(
        <SessionProvider>
          <CommunityPostFormPage mode="create" />
        </SessionProvider>,
      );
    });

    expect(container.textContent).toContain('베이스캠프에서 구해요.');
    expect(container.textContent).toContain('금전을 요구하는 글은 신고해 주세요.');

    await act(async () => root.unmount());
    container.remove();
  });
});
