import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AdminCommunityComment, AdminCommunityPost, AdminCommunityStatus } from '../../api/types';
import { SessionProvider } from '../../app/session/session';
import { AdminCommunityPage } from './AdminCommunityPage';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const ME = {
  memberId: 1,
  email: 'admin@b.c',
  nickname: '관리자',
  status: 'ACTIVE',
  role: 'ADMIN',
  selfAgeGroup: null,
  selfGender: null,
  identityVerified: true,
  trustLevel: 1,
};

const STATE_CHANGED_MESSAGE = '상태가 바뀌어 지금은 할 수 없어요. 화면을 새로고침해 주세요.';

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function page<T>(content: T[], overrides: { page?: number; hasNext?: boolean } = {}) {
  return { content, page: overrides.page ?? 0, size: 20, hasNext: overrides.hasNext ?? false };
}

function post(overrides: Partial<AdminCommunityPost> = {}): AdminCommunityPost {
  return {
    postId: 1,
    title: '여기서 장사해도 돼요',
    content: '첫 줄\n둘째 줄',
    images: [],
    author: { memberId: 7, nickname: '작성자A' },
    status: 'PENDING_REVIEW',
    reportCount: 5,
    reasonCounts: { SPAM: 3, MONEY_SCAM: 2 },
    recentReports: [{ reason: 'ABUSE', content: '욕이 섞여 있어요', createdAt: '2026-11-01T03:00:00Z' }],
    statusChangedAt: '2026-11-01T03:00:00Z',
    ...overrides,
  };
}

function comment(overrides: Partial<AdminCommunityComment> = {}): AdminCommunityComment {
  return {
    commentId: 30,
    postId: 12,
    content: '연락처 남겨 주세요',
    author: { memberId: 8, nickname: '댓글러' },
    status: 'PENDING_REVIEW',
    reportCount: 5,
    reasonCounts: { PRIVACY: 5 },
    recentReports: [],
    statusChangedAt: '2026-11-01T03:00:00Z',
    ...overrides,
  };
}

interface Call {
  method: string;
  path: string;
  status: string | null;
  page: string | null;
}

describe('관리자 커뮤니티 검토', () => {
  let container: HTMLDivElement;
  let root: Root;
  let calls: Call[];
  let memberRole: string;
  let posts: Record<AdminCommunityStatus, () => Response>;
  let comments: Record<AdminCommunityStatus, () => Response>;
  let moderate: (path: string) => Response;

  const text = (element: Element | null) => element?.textContent ?? '';
  const button = (label: string, scope: ParentNode = container) =>
    [...scope.querySelectorAll('button')].find((element) => element.textContent === label);
  const cards = () => container.querySelectorAll('ul[aria-label="글 목록"] > li, ul[aria-label="댓글 목록"] > li');
  const lists = (method: string, path: string) => calls.filter((call) => call.method === method && call.path === path);

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    calls = [];
    memberRole = 'ADMIN';
    posts = { PENDING_REVIEW: () => json(200, page([post()])), HIDDEN: () => json(200, page([])) };
    comments = { PENDING_REVIEW: () => json(200, page([comment()])), HIDDEN: () => json(200, page([])) };
    moderate = () => json(200, { postId: 1, status: 'ACTIVE' });
    window.history.replaceState(null, '', '/admin/community');
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.open = true;
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.open = false;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        const parsed = new URL(url, 'http://localhost');
        const method = init?.method ?? 'GET';
        const status = parsed.searchParams.get('status');
        calls.push({ method, path: parsed.pathname, status, page: parsed.searchParams.get('page') });
        if (parsed.pathname === '/api/me') return json(200, { ...ME, role: memberRole });
        if (method === 'POST') return moderate(parsed.pathname);
        if (status !== 'PENDING_REVIEW' && status !== 'HIDDEN')
          return json(400, { code: 'INVALID_INPUT', message: 'x' });
        if (parsed.pathname === '/api/admin/community/posts') return posts[status]();
        if (parsed.pathname === '/api/admin/community/comments') return comments[status]();
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  async function render() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <AdminCommunityPage />
        </SessionProvider>,
      );
    });
  }

  it('글 목록에 제목, 작성자, 본문, 신고 수와 사유별 수, 최근 신고가 보인다', async () => {
    await render();

    const card = cards()[0] ?? null;
    expect(text(card)).toContain('여기서 장사해도 돼요');
    expect(text(card)).toContain('작성자A');
    expect(text(card)).toContain('검토 대기');
    expect(card?.querySelector('p.whitespace-pre-line')?.textContent).toBe('첫 줄\n둘째 줄');
    expect(text(card)).toContain('5건 (스팸·광고 3, 금전 요구·사기 2)');
    expect(text(card)).toContain('욕설·혐오');
    expect(text(card)).toContain('욕이 섞여 있어요');
    expect(lists('GET', '/api/admin/community/posts')[0]?.status).toBe('PENDING_REVIEW');
  });

  it('댓글로 바꾸면 댓글 API를 부르고 달린 글 번호를 보여 준다', async () => {
    await render();

    await act(async () => button('댓글')?.click());

    expect(lists('GET', '/api/admin/community/comments')).toHaveLength(1);
    expect(text(container)).toContain('댓글러님의 댓글');
    expect(text(container)).toContain('연락처 남겨 주세요');
    expect(text(container)).toContain('글 번호 12');
    expect(text(container)).toContain('개인정보 노출 5');
  });

  it('글에 붙은 사진을 작은 그림으로 보여 주고 네 장을 넘으면 몇 장인지 알린다', async () => {
    const images = [1, 2, 3, 4, 5].map((imageId) => ({ imageId, url: `https://img.test/${imageId}.jpg` }));
    posts.PENDING_REVIEW = () => json(200, page([post({ images })]));
    await render();

    const card = cards()[0] ?? null;
    const shown = [...(card?.querySelectorAll('img') ?? [])];
    expect(shown.map((image) => image.getAttribute('src'))).toEqual(images.slice(0, 4).map((image) => image.url));
    expect(card?.querySelector('ul[aria-label="사진 5장"]')).not.toBeNull();
    expect(text(card)).toContain('사진 5장 중 4장만 보여요.');
  });

  it('사진이 없는 글에는 사진 칸이 없다', async () => {
    await render();

    expect(cards()[0]?.querySelector('img')).toBeNull();
  });

  it('댓글 카드에서 달린 글로 가는 링크를 준다', async () => {
    await render();

    await act(async () => button('댓글')?.click());

    const link = [...container.querySelectorAll('a')].find((element) => element.textContent === '글 보기');
    expect(link?.getAttribute('href')).toBe('/community/12');
  });

  it('주소에 kind와 tab이 있으면 그 목록으로 시작한다', async () => {
    window.history.replaceState(null, '', '/admin/community?kind=comment&tab=hidden');
    comments.HIDDEN = () => json(200, page([comment({ status: 'HIDDEN' })]));

    await render();

    expect(lists('GET', '/api/admin/community/comments').map((call) => call.status)).toEqual(['HIDDEN']);
    expect(lists('GET', '/api/admin/community/posts')).toHaveLength(0);
    expect(text(container)).toContain('댓글러님의 댓글');
  });

  it('탭에 따라 status 쿼리가 바뀌고, 숨김 탭에는 복구만 있다', async () => {
    posts.HIDDEN = () => json(200, page([post({ status: 'HIDDEN' })]));
    await render();
    expect(button('숨기기')).toBeDefined();

    await act(async () => button('숨김')?.click());

    expect(lists('GET', '/api/admin/community/posts').map((call) => call.status)).toEqual(['PENDING_REVIEW', 'HIDDEN']);
    expect(button('숨기기')).toBeUndefined();
    expect(button('복구')).toBeDefined();
    expect(text(container)).toContain('복구하면 이전 신고는 세지 않아요. 새 신고가 5건 쌓여야 다시 검토 대기가 돼요.');
  });

  it('숨기기는 확인 대화상자를 거쳐야 요청이 나가고, 취소하면 나가지 않는다', async () => {
    await render();

    await act(async () => button('숨기기')?.click());
    const dialog = container.querySelector('dialog');
    expect(text(dialog)).toContain(
      '작성자A님의 글을 숨겨요. 숨긴 글은 다른 회원에게 보이지 않아요. 숨김 탭에서 복구할 수 있어요.',
    );
    expect(lists('POST', '/api/admin/community/posts/1/hide')).toHaveLength(0);

    await act(async () => button('취소', dialog ?? container)?.click());
    expect(container.querySelector('dialog')).toBeNull();
    expect(lists('POST', '/api/admin/community/posts/1/hide')).toHaveLength(0);

    await act(async () => button('숨기기')?.click());
    posts.PENDING_REVIEW = () => json(200, page([]));
    await act(async () => button('숨기기', container.querySelector('dialog') ?? container)?.click());

    expect(lists('POST', '/api/admin/community/posts/1/hide')).toHaveLength(1);
    expect(lists('GET', '/api/admin/community/posts')).toHaveLength(2);
    expect(text(container)).toContain('검토를 기다리는 글이 없어요');
  });

  it('댓글 숨기기는 댓글 숨김 API로 보내고 확인 문구에 댓글이라고 쓴다', async () => {
    await render();
    await act(async () => button('댓글')?.click());

    await act(async () => button('숨기기')?.click());
    expect(text(container.querySelector('dialog'))).toContain('댓글러님의 댓글을 숨겨요. 숨긴 댓글은');
    await act(async () => button('숨기기', container.querySelector('dialog') ?? container)?.click());

    expect(lists('POST', '/api/admin/community/comments/30/hide')).toHaveLength(1);
  });

  it('복구는 확인 없이 바로 요청하고, 성공하면 목록을 다시 불러온다', async () => {
    await render();
    posts.PENDING_REVIEW = () => json(200, page([]));

    await act(async () => button('복구')?.click());

    expect(lists('POST', '/api/admin/community/posts/1/restore')).toHaveLength(1);
    expect(container.querySelector('dialog')).toBeNull();
    expect(lists('GET', '/api/admin/community/posts')).toHaveLength(2);
    expect(cards()).toHaveLength(0);
  });

  it('숨김은 성공했는데 목록을 다시 불러오지 못해도 대화상자가 닫히고 그 카드가 빠진다', async () => {
    await render();
    posts.PENDING_REVIEW = () => json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-reload' });

    await act(async () => button('숨기기')?.click());
    await act(async () => button('숨기기', container.querySelector('dialog') ?? container)?.click());

    expect(lists('POST', '/api/admin/community/posts/1/hide')).toHaveLength(1);
    expect(container.querySelector('dialog')).toBeNull();
    expect(cards()).toHaveLength(0);
    expect(text(container.querySelector('[role="alert"]'))).toContain('글을 불러오지 못했어요');
  });

  it('이미 처리된 항목(409)은 카드 안에 오류를 보이고 목록을 다시 불러온다', async () => {
    moderate = () => json(409, { code: 'COMMUNITY_INVALID_STATE', message: 'x' });
    await render();

    await act(async () => button('복구')?.click());

    const card = cards()[0] ?? null;
    expect(text(card?.querySelector('[role="alert"]') ?? null)).toContain(STATE_CHANGED_MESSAGE);
    expect(lists('GET', '/api/admin/community/posts')).toHaveLength(2);
    expect(button('복구')?.disabled).toBe(false);
  });

  it('작성자가 지운 글(404)은 목록을 다시 불러와 사라져도 오류 안내가 남는다', async () => {
    moderate = () => json(404, { code: 'NOT_FOUND', message: 'x' });
    await render();
    posts.PENDING_REVIEW = () => json(200, page([]));

    await act(async () => button('복구')?.click());

    expect(cards()).toHaveLength(0);
    expect(text(container.querySelector('[role="alert"]'))).toContain('찾는 내용이 없거나 삭제됐어요');
  });

  it('서버 오류(500)는 목록을 다시 불러오지 않고 카드에 오류를 보인다', async () => {
    moderate = () => json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-action' });
    await render();

    await act(async () => button('복구')?.click());

    expect(text(cards()[0]?.querySelector('[role="alert"]') ?? null)).toContain('상태를 바꾸지 못했어요');
    expect(text(cards()[0] ?? null)).toContain('trace-action');
    expect(lists('GET', '/api/admin/community/posts')).toHaveLength(1);
  });

  it.each([
    ['글', 'PENDING_REVIEW', '검토를 기다리는 글이 없어요', '신고가 쌓인 글이 여기에 모여요.'],
    ['글', 'HIDDEN', '숨긴 글이 없어요', '숨긴 글이 여기에 모여요.'],
    ['댓글', 'PENDING_REVIEW', '검토를 기다리는 댓글이 없어요', '신고가 쌓인 댓글이 여기에 모여요.'],
    ['댓글', 'HIDDEN', '숨긴 댓글이 없어요', '숨긴 댓글이 여기에 모여요.'],
  ] as const)('%s %s 탭이 비면 안내 문구를 보인다', async (kind, status, title, description) => {
    posts.PENDING_REVIEW = () => json(200, page([]));
    comments.PENDING_REVIEW = () => json(200, page([]));
    await render();
    if (kind === '댓글') await act(async () => button('댓글')?.click());
    if (status === 'HIDDEN') await act(async () => button('숨김')?.click());

    expect(text(container)).toContain(title);
    expect(text(container)).toContain(description);
  });

  it('불러오기에 실패하면 오류와 다시 불러오기가 보이고, 누르면 목록이 뜬다', async () => {
    let failing = true;
    posts.PENDING_REVIEW = () =>
      failing ? json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-500' }) : json(200, page([post()]));
    await render();

    expect(text(container.querySelector('[role="alert"]'))).toContain('글을 불러오지 못했어요');
    expect(text(container)).toContain('trace-500');
    expect(cards()).toHaveLength(0);

    failing = false;
    await act(async () => button('다시 불러오기')?.click());

    expect(text(container)).toContain('여기서 장사해도 돼요');
    expect(button('다시 불러오기')).toBeUndefined();
  });

  it('더 보기를 누르면 다음 쪽을 이어 붙인다', async () => {
    posts.PENDING_REVIEW = () => json(200, page([post()], { hasNext: true }));
    await render();
    posts.PENDING_REVIEW = () => json(200, page([post({ postId: 2, title: '둘째 글' })], { page: 1 }));

    await act(async () => button('더 보기')?.click());

    expect(lists('GET', '/api/admin/community/posts').map((call) => call.page)).toEqual(['0', '1']);
    expect(cards()).toHaveLength(2);
    expect(text(container)).toContain('둘째 글');
    expect(button('더 보기')).toBeUndefined();
  });

  it('관리자가 아니면 목록을 부르지 않고 안내만 보인다', async () => {
    memberRole = 'USER';
    await render();

    expect(text(container)).toContain('관리자만 볼 수 있어요');
    expect(lists('GET', '/api/admin/community/posts')).toHaveLength(0);
  });
});
