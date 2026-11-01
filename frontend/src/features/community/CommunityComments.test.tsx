import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CommunityComments } from './CommunityComments';

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => ({ status: 'authenticated', me: { memberId: 1, nickname: '산사람', status: 'ACTIVE' } }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const COMMENT = {
  commentId: 10,
  author: { memberId: 2, nickname: '능선' },
  content: '첫 댓글',
  deleted: false,
  createdAt: '2026-10-01T00:00:00Z',
  replies: [],
};

describe('댓글 화면: 답글 저장이 실패했을 때', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);

  async function typeReply(value: string) {
    const field = container.querySelector<HTMLTextAreaElement>('form[aria-label="답글"] textarea');
    if (!field) throw new Error('답글 입력이 없다');
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')?.set?.call(field, value);
      field.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }

  beforeEach(async () => {
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === 'POST') {
          return json(400, { code: 'INVALID_INPUT', message: '답글을 달 수 없는 댓글이에요.', traceId: 't' });
        }
        if (url.startsWith('/api/community/posts/1/comments')) {
          return json(200, { content: [COMMENT], page: 0, size: 20, hasNext: false });
        }
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
    await act(async () => root.render(<CommunityComments postId={1} count={1} onChanged={() => undefined} />));
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  it('필드 오류 없는 INVALID_INPUT이면 서버가 보낸 문구를 보여 준다', async () => {
    await act(async () => button('답글')?.click());
    await typeReply('답글이에요');

    await act(async () => button('답글 남기기')?.click());

    const alert = container.querySelector('form[aria-label="답글"] [role="alert"]');
    expect(alert?.textContent).toContain('답글을 달 수 없는 댓글이에요.');
    expect(alert?.textContent).not.toContain('표시된 칸을 수정해 주세요');
  });
});
