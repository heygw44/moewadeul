import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SessionProvider } from '../../app/session/session';
import { CommunityComments } from './CommunityComments';
import { CommunityPostDetailPage } from './CommunityPostDetailPage';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const ME = {
  memberId: 1,
  email: 'a@b.c',
  nickname: '관리자',
  status: 'ACTIVE',
  role: 'ADMIN',
  selfAgeGroup: null,
  selfGender: null,
  identityVerified: true,
  trustLevel: 1,
};

const POST = {
  postId: 5,
  title: '정상 글',
  content: '본문',
  author: { memberId: 2, nickname: '능선' },
  likeCount: 0,
  commentCount: 1,
  viewCount: 3,
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
  images: [],
};

const COMMENT = {
  commentId: 10,
  author: { memberId: 2, nickname: '능선' },
  content: '첫 댓글',
  deleted: false,
  createdAt: '2026-10-01T00:00:00Z',
  replies: [],
};

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('공개 글과 댓글의 관리자 숨기기', () => {
  let container: HTMLDivElement;
  let root: Root;
  let role: string;
  let writes: string[];
  let hideReply: () => Response;
  let commentListCalls: number;

  const text = (element: Element | null) => element?.textContent ?? '';
  const button = (label: string, scope: ParentNode = container) =>
    [...scope.querySelectorAll('button')].find((element) => element.textContent === label);
  const dialog = () => container.querySelector('dialog');

  beforeEach(() => {
    container = document.createElement('div');
    document.body.append(container);
    role = 'ADMIN';
    writes = [];
    commentListCalls = 0;
    hideReply = () => json(200, { postId: 5, status: 'HIDDEN' });
    window.history.replaceState(null, '', '/community/5');
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.open = true;
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.open = false;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        const { pathname } = new URL(url, 'http://localhost');
        if (pathname === '/api/me') return json(200, { ...ME, role });
        if (init?.method === 'POST') {
          writes.push(pathname);
          return hideReply();
        }
        if (pathname === '/api/community/posts/5') return json(200, POST);
        if (pathname === '/api/community/posts/5/comments') {
          commentListCalls += 1;
          return json(200, { content: [COMMENT], page: 0, size: 20, hasNext: false });
        }
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  async function renderPost() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <CommunityPostDetailPage postId={5} />
        </SessionProvider>,
      );
    });
  }

  async function renderComments() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <CommunityComments postId={5} count={1} onChanged={() => undefined} />
        </SessionProvider>,
      );
    });
  }

  it('관리자에게만 글의 관리자 숨기기 버튼이 보인다', async () => {
    role = 'USER';
    await renderPost();

    expect(button('관리자 숨기기')).toBeUndefined();
    expect(button('신고')).toBeDefined();
  });

  it('관리자가 글을 숨기면 확인을 거쳐 숨김 API를 부르고 관리자 숨김 목록으로 이동한다', async () => {
    await renderPost();

    await act(async () => button('관리자 숨기기')?.click());
    expect(text(dialog())).toContain('능선님의 글을 숨겨요.');
    expect(writes).toEqual([]);

    await act(async () => button('숨기기', dialog() ?? container)?.click());

    expect(writes).toEqual(['/api/admin/community/posts/5/hide']);
    expect(window.location.pathname + window.location.search).toBe('/admin/community?kind=post&tab=hidden');
  });

  it('글 숨기기가 거절되면 대화상자 안에 이유를 보이고 이동하지 않는다', async () => {
    hideReply = () => json(409, { code: 'COMMUNITY_INVALID_STATE', message: 'x', traceId: 't' });
    await renderPost();

    await act(async () => button('관리자 숨기기')?.click());
    await act(async () => button('숨기기', dialog() ?? container)?.click());

    expect(text(dialog()?.querySelector('[role="alert"]') ?? null)).toContain('글을 숨기지 못했어요');
    expect(window.location.pathname).toBe('/community/5');
  });

  it('관리자에게만 댓글의 관리자 숨기기 버튼이 보인다', async () => {
    role = 'USER';
    await renderComments();

    expect(button('관리자 숨기기')).toBeUndefined();
  });

  it('관리자가 댓글을 숨기면 숨김 API를 부르고 목록을 다시 불러와 복구 위치를 알린다', async () => {
    hideReply = () => json(200, { commentId: 10, status: 'HIDDEN' });
    await renderComments();

    await act(async () => button('관리자 숨기기')?.click());
    expect(text(dialog())).toContain('능선님의 댓글을 숨겨요.');
    await act(async () => button('숨기기', dialog() ?? container)?.click());

    expect(writes).toEqual(['/api/admin/community/comments/10/hide']);
    expect(dialog()).toBeNull();
    expect(commentListCalls).toBe(2);
    expect(text(container.querySelector('[role="status"]'))).toContain('댓글을 숨겼어요.');
    expect(text(container.querySelector('[role="status"]'))).toContain('숨김 탭에서 복구할 수 있어요.');
  });
});
