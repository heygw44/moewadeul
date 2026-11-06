import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { deletePost, getPost, likePost, listComments, reportPost, unlikePost } from '../../api/community';
import type { CommunityPostDetail } from '../../api/types';
import { CommunityPostDetailPage } from './CommunityPostDetailPage';

vi.mock('../../api/community', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/community')>()),
  getPost: vi.fn(),
  likePost: vi.fn(),
  unlikePost: vi.fn(),
  deletePost: vi.fn(),
  reportPost: vi.fn(),
  listComments: vi.fn(),
}));

vi.mock('../notification/unreadCount', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../notification/unreadCount')>()),
  refreshUnreadCount: vi.fn(),
}));

type TestSession = {
  status: string;
  me: { memberId: number; nickname: string; status: string } | null;
};

const session = vi.hoisted(() => ({ value: null as unknown as TestSession }));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => session.value,
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const AUTHOR_ID = 7;
const POST_ID = 5;

function signedIn(memberId: number, status = 'ACTIVE'): TestSession {
  return { status: 'authenticated', me: { memberId, nickname: '회원', status } };
}

function detail(overrides: Partial<CommunityPostDetail> = {}): CommunityPostDetail {
  return {
    postId: POST_ID,
    title: '대둔산 박지 후기',
    content: '바람이 거셌지만 전망이 좋았어요.',
    author: { memberId: AUTHOR_ID, nickname: '능선' },
    spot: { spotId: 12, name: '대둔산 박지' },
    images: [
      { imageId: 1, url: 'https://img.test/1.jpg' },
      { imageId: 2, url: 'https://img.test/2.jpg' },
    ],
    likeCount: 4,
    likedByMe: false,
    commentCount: 0,
    viewCount: 33,
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    ...overrides,
  };
}

describe('커뮤니티 글 상세 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string, scope: ParentNode = container) =>
    [...scope.querySelectorAll('button')].find((element) => element.textContent?.trim() === label);
  const likeButton = () =>
    [...container.querySelectorAll('button')].find((element) => element.textContent?.startsWith('좋아요'));
  const dialog = () => container.querySelector('dialog');

  async function open() {
    await act(async () => root.render(<CommunityPostDetailPage postId={POST_ID} />));
  }

  beforeEach(() => {
    session.value = signedIn(1);
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.open = true;
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.open = false;
    });
    vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    vi.mocked(getPost).mockReset().mockResolvedValue(detail());
    vi.mocked(likePost).mockReset();
    vi.mocked(unlikePost).mockReset();
    vi.mocked(deletePost).mockReset();
    vi.mocked(reportPost).mockReset();
    vi.mocked(listComments)
      .mockReset()
      .mockResolvedValue({ content: [], page: 0, size: 20, hasNext: false } as never);
    window.history.replaceState(null, '', `/community/${POST_ID}`);
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    window.history.replaceState(null, '', '/');
    vi.restoreAllMocks();
  });

  it('글의 제목, 본문, 글쓴이, 장소, 사진을 보여 준다', async () => {
    await open();

    expect(container.querySelector('h1')?.textContent).toBe('대둔산 박지 후기');
    expect(container.textContent).toContain('바람이 거셌지만 전망이 좋았어요.');
    expect(container.querySelector('a[href="/members/7"]')?.textContent).toBe('능선');
    expect(container.querySelector('a[href="/spots/12"]')?.textContent).toContain('대둔산 박지');
    expect(container.textContent).toContain('조회 33');
    expect(container.textContent).not.toContain('수정됨');
    const images = [...container.querySelectorAll('img')].map((image) => image.getAttribute('alt'));
    expect(images).toEqual(['글 사진 2장 중 1번째', '글 사진 2장 중 2번째']);
    expect(container.querySelector('a[href="/community"]')).not.toBeNull();
  });

  it('고친 적이 있는 글에는 수정됨 표시가 붙는다', async () => {
    vi.mocked(getPost).mockResolvedValue(detail({ updatedAt: '2026-10-02T00:00:00Z' }));

    await open();

    expect(container.textContent).toContain('수정됨');
  });

  it('없거나 삭제된 글이면 찾을 수 없다는 안내를 보여 준다', async () => {
    vi.mocked(getPost).mockRejectedValue(new ApiError(404, 'NOT_FOUND', null));

    await open();

    expect(container.querySelector('h1')?.textContent).toBe('글을 찾을 수 없어요');
    expect(container.textContent).toContain('삭제됐거나 볼 수 없는 글이에요');
    expect(button('다시 불러오기')).toBeUndefined();
  });

  it('불러오지 못하면 오류 문구를 보여 주고 다시 불러오기를 누르면 글이 나온다', async () => {
    vi.mocked(getPost).mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', null));

    await open();

    expect(container.textContent).toContain('글을 불러오지 못했어요');
    expect(container.textContent).toContain('일시적인 문제가 생겼어요');
    expect(container.textContent).not.toContain('바람이 거셌지만');

    await act(async () => button('다시 불러오기')?.click());

    expect(container.textContent).toContain('바람이 거셌지만 전망이 좋았어요.');
    expect(container.textContent).not.toContain('글을 불러오지 못했어요');
  });

  describe('좋아요', () => {
    it('좋아요를 누르면 서버가 돌려준 개수로 바뀌고 눌린 상태가 된다', async () => {
      vi.mocked(likePost).mockResolvedValue({ liked: true, likeCount: 5 });
      await open();
      expect(likeButton()?.getAttribute('aria-pressed')).toBe('false');

      await act(async () => likeButton()?.click());

      expect(likePost).toHaveBeenCalledWith(POST_ID);
      expect(likeButton()?.textContent).toContain('5');
      expect(likeButton()?.getAttribute('aria-pressed')).toBe('true');
    });

    it('이미 좋아요한 글에서 누르면 좋아요가 취소된다', async () => {
      vi.mocked(getPost).mockResolvedValue(detail({ likedByMe: true, likeCount: 5 }));
      vi.mocked(unlikePost).mockResolvedValue({ liked: false, likeCount: 4 });
      await open();

      await act(async () => likeButton()?.click());

      expect(unlikePost).toHaveBeenCalledWith(POST_ID);
      expect(likePost).not.toHaveBeenCalled();
      expect(likeButton()?.textContent).toContain('4');
      expect(likeButton()?.getAttribute('aria-pressed')).toBe('false');
    });

    it('좋아요가 실패하면 오류 문구를 보여 주고 개수는 그대로다', async () => {
      vi.mocked(likePost).mockRejectedValue(new ApiError(429, 'TOO_MANY_REQUESTS', null));
      await open();

      await act(async () => likeButton()?.click());

      expect(container.querySelector('[role="alert"]')?.textContent).toContain('요청이 너무 많아요');
      expect(likeButton()?.textContent).toContain('4');
    });

    it('로그인하지 않은 방문자의 좋아요는 로그인 화면으로 가는 링크이고 신고는 보이지 않는다', async () => {
      session.value = { status: 'anonymous', me: null };

      await open();

      const link = [...container.querySelectorAll('a')].find((anchor) => anchor.textContent?.startsWith('좋아요'));
      expect(link?.getAttribute('href')).toBe(`/login?next=${encodeURIComponent(`/community/${POST_ID}`)}`);
      expect(button('신고')).toBeUndefined();
      expect(button('삭제')).toBeUndefined();
    });

    it('이메일 인증 전 회원은 좋아요와 신고가 잠겨 있다', async () => {
      session.value = signedIn(1, 'UNVERIFIED');

      await open();

      expect(likeButton()?.disabled).toBe(true);
      expect(button('신고')?.disabled).toBe(true);
    });
  });

  describe('내가 쓴 글', () => {
    beforeEach(() => {
      session.value = signedIn(AUTHOR_ID);
    });

    it('수정 링크와 삭제 버튼이 있고 신고는 없다', async () => {
      await open();

      expect(container.querySelector(`a[href="/community/${POST_ID}/edit"]`)?.textContent).toBe('수정');
      expect(button('삭제')).toBeDefined();
      expect(button('신고')).toBeUndefined();
    });

    it('삭제를 확인하면 글을 지우고 커뮤니티 목록으로 이동한다', async () => {
      vi.mocked(deletePost).mockResolvedValue(undefined);
      await open();

      await act(async () => button('삭제')?.click());
      expect(dialog()?.textContent).toContain('글을 삭제할까요?');
      expect(deletePost).not.toHaveBeenCalled();

      await act(async () => button('삭제', dialog()!)?.click());

      expect(deletePost).toHaveBeenCalledWith(POST_ID);
      expect(window.location.pathname).toBe('/community');
    });

    it('삭제 창에서 취소하면 글은 그대로 남는다', async () => {
      await open();
      await act(async () => button('삭제')?.click());

      await act(async () => button('취소', dialog()!)?.click());

      expect(dialog()).toBeNull();
      expect(deletePost).not.toHaveBeenCalled();
      expect(window.location.pathname).toBe(`/community/${POST_ID}`);
    });

    it('삭제가 실패하면 창 안에 오류를 보여 주고 이동하지 않는다', async () => {
      vi.mocked(deletePost).mockRejectedValue(new ApiError(403, 'ACCESS_DENIED', null));
      await open();
      await act(async () => button('삭제')?.click());

      await act(async () => button('삭제', dialog()!)?.click());

      expect(dialog()?.querySelector('[role="alert"]')?.textContent).toContain('삭제하지 못했어요');
      expect(window.location.pathname).toBe(`/community/${POST_ID}`);
    });
  });

  describe('남이 쓴 글 신고', () => {
    it('신고 사유를 고르기 전에는 신고하기가 잠겨 있고, 고르고 보내면 접수 안내가 나온다', async () => {
      vi.mocked(reportPost).mockResolvedValue(undefined);
      await open();
      expect(container.querySelector(`a[href="/community/${POST_ID}/edit"]`)).toBeNull();

      await act(async () => button('신고')?.click());
      expect(button('신고하기', dialog()!)?.disabled).toBe(true);

      const reason = dialog()?.querySelector<HTMLInputElement>('input[value="SPAM"]');
      await act(async () => reason?.click());
      await act(async () => button('신고하기', dialog()!)?.click());

      expect(reportPost).toHaveBeenCalledWith(POST_ID, { reason: 'SPAM', content: undefined });
      expect(dialog()?.textContent).toContain('신고를 접수했어요');
    });

    it('이미 신고한 글이면 이미 신고했다고 알려 준다', async () => {
      vi.mocked(reportPost).mockRejectedValue(new ApiError(409, 'COMMUNITY_ALREADY_REPORTED', null));
      await open();
      await act(async () => button('신고')?.click());
      await act(async () => dialog()?.querySelector<HTMLInputElement>('input[value="SPAM"]')?.click());

      await act(async () => button('신고하기', dialog()!)?.click());

      expect(dialog()?.querySelector('[role="alert"]')?.textContent).toContain('이미 신고했어요');
    });
  });
});
