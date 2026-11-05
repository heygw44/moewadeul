import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { deleteMemory, getMemory, likeMemory, reportMemory, unlikeMemory } from '../../api/memories';
import type { MemoryDetail } from '../../api/types';
import { MemoryDetailPage } from './MemoryDetailPage';

vi.mock('../../api/memories', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/memories')>()),
  getMemory: vi.fn(),
  deleteMemory: vi.fn(),
  likeMemory: vi.fn(),
  unlikeMemory: vi.fn(),
  reportMemory: vi.fn(),
}));

vi.mock('../../map/addressName', () => ({ addressAt: vi.fn(async () => '대전 서구 월평로 1') }));
vi.mock('../../map/regionName', () => ({ regionNameAt: vi.fn(async () => '대전 서구') }));

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
const MEMORY_ID = 3;

function signedIn(memberId: number, status = 'ACTIVE'): TestSession {
  return { status: 'authenticated', me: { memberId, nickname: '회원', status } };
}

function memory(overrides: Partial<MemoryDetail> = {}): MemoryDetail {
  return {
    memoryId: MEMORY_ID,
    author: { memberId: AUTHOR_ID, nickname: '능선' },
    spot: { spotId: 12, name: '대둔산 박지' },
    approxLat: 36.35,
    approxLng: 127.38,
    visitedDate: '2026-10-03',
    nights: 1,
    content: '별이 정말 많았어요.',
    visibility: 'PUBLIC',
    status: 'ACTIVE',
    images: [
      { imageId: 1, url: 'https://img.test/1.jpg' },
      { imageId: 2, url: 'https://img.test/2.jpg' },
    ],
    likeCount: 4,
    likedByMe: false,
    createdAt: '2026-10-04T00:00:00Z',
    updatedAt: '2026-10-04T00:00:00Z',
    ...overrides,
  };
}

describe('기록 상세 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string, scope: ParentNode = container) =>
    [...scope.querySelectorAll('button')].find((element) => element.textContent?.trim() === label);
  const likeButton = () =>
    [...container.querySelectorAll('button')].find((element) => element.textContent?.startsWith('좋아요'));
  const dialog = () => container.querySelector('dialog');

  async function open() {
    await act(async () => root.render(<MemoryDetailPage memoryId={MEMORY_ID} />));
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
    vi.mocked(getMemory).mockReset().mockResolvedValue(memory());
    vi.mocked(deleteMemory).mockReset();
    vi.mocked(likeMemory).mockReset();
    vi.mocked(unlikeMemory).mockReset();
    vi.mocked(reportMemory).mockReset();
    window.history.replaceState(null, '', `/memories/${MEMORY_ID}`);
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

  it('다른 사람의 공개 기록은 장소 이름, 글쓴이, 방문일, 대략 위치, 글, 사진을 보여 준다', async () => {
    await open();

    expect(container.querySelector('h1')?.textContent).toBe('대둔산 박지');
    expect(container.querySelector('a[href="/members/7"]')?.textContent).toBe('능선');
    expect(container.textContent).toContain('10월 3일 (토)');
    expect(container.textContent).toContain('1박');
    expect(container.textContent).toContain('공개');
    expect(container.querySelector('a[href="/spots/12"]')?.textContent).toContain('장소 보기');
    expect(container.textContent).toContain('다녀온 곳 (대략 위치)');
    expect(container.textContent).toContain('대전 서구');
    expect(container.textContent).toContain('별이 정말 많았어요.');
    const photos = [...container.querySelectorAll('img')].map((image) => image.getAttribute('alt'));
    expect(photos).toEqual(['능선님의 기록 사진 2장 중 1번째', '능선님의 기록 사진 2장 중 2번째']);
    expect(container.querySelector('a[href="/memories"]')?.textContent).toContain('피드');
  });

  it('장소가 없는 기록은 방문일로 제목을 만들고 글과 사진이 없으면 그렇게 알려 준다', async () => {
    vi.mocked(getMemory).mockResolvedValue(memory({ spot: undefined, content: undefined, images: [] }));

    await open();

    expect(container.querySelector('h1')?.textContent).toBe('10월 3일 (토) 기록');
    expect(container.textContent).toContain('사진도 글도 없는 기록이에요.');
    expect(container.querySelector('a[href="/spots/12"]')).toBeNull();
  });

  it('없거나 볼 수 없는 기록이면 찾을 수 없다는 안내를 보여 준다', async () => {
    vi.mocked(getMemory).mockRejectedValue(new ApiError(404, 'NOT_FOUND', null));

    await open();

    expect(container.querySelector('h1')?.textContent).toBe('기록을 찾을 수 없어요');
    expect(container.textContent).toContain('삭제됐거나 볼 수 없는 기록이에요');
    expect(container.textContent).toContain('나만 보기 기록은 남긴 사람만 열 수 있어요.');
  });

  it('불러오지 못하면 오류 문구를 보여 주고 다시 불러오기를 누르면 기록이 나온다', async () => {
    vi.mocked(getMemory).mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', null));

    await open();

    expect(container.textContent).toContain('기록을 불러오지 못했어요');
    expect(container.textContent).toContain('일시적인 문제가 생겼어요');

    await act(async () => button('다시 불러오기')?.click());

    expect(container.textContent).toContain('별이 정말 많았어요.');
    expect(container.textContent).not.toContain('기록을 불러오지 못했어요');
  });

  describe('좋아요와 검토 중 표시', () => {
    it('좋아요를 누르면 서버가 돌려준 개수로 바뀌고 눌린 상태가 된다', async () => {
      vi.mocked(likeMemory).mockResolvedValue({ liked: true, likeCount: 5 });
      await open();

      await act(async () => likeButton()?.click());

      expect(likeMemory).toHaveBeenCalledWith(MEMORY_ID);
      expect(likeButton()?.textContent).toContain('5');
      expect(likeButton()?.getAttribute('aria-pressed')).toBe('true');
    });

    it('이미 좋아요한 기록에서 누르면 좋아요가 취소된다', async () => {
      vi.mocked(getMemory).mockResolvedValue(memory({ likedByMe: true, likeCount: 5 }));
      vi.mocked(unlikeMemory).mockResolvedValue({ liked: false, likeCount: 4 });
      await open();

      await act(async () => likeButton()?.click());

      expect(unlikeMemory).toHaveBeenCalledWith(MEMORY_ID);
      expect(likeButton()?.textContent).toContain('4');
      expect(likeButton()?.getAttribute('aria-pressed')).toBe('false');
    });

    it('좋아요가 실패하면 오류 문구를 보여 준다', async () => {
      vi.mocked(likeMemory).mockRejectedValue(new ApiError(429, 'TOO_MANY_REQUESTS', null));
      await open();

      await act(async () => likeButton()?.click());

      expect(container.querySelector('[role="alert"]')?.textContent).toContain('요청이 너무 많아요');
      expect(likeButton()?.textContent).toContain('4');
    });

    it('로그인하지 않은 방문자의 좋아요는 로그인 화면으로 가는 링크이고 신고는 보이지 않는다', async () => {
      session.value = { status: 'anonymous', me: null };

      await open();

      const link = [...container.querySelectorAll('a')].find((anchor) => anchor.textContent?.startsWith('좋아요'));
      expect(link?.getAttribute('href')).toBe(`/login?next=${encodeURIComponent(`/memories/${MEMORY_ID}`)}`);
      expect(button('신고')).toBeUndefined();
    });

    it('검토 중이거나 숨겨진 기록에는 안내가 뜨고 좋아요는 없다', async () => {
      session.value = signedIn(AUTHOR_ID);
      vi.mocked(getMemory).mockResolvedValue(memory({ status: 'PENDING_REVIEW' }));

      await open();

      expect(container.textContent).toContain('신고가 쌓여 검토 중인 기록이에요');
      expect(container.textContent).toContain('검토 중');
      expect(likeButton()).toBeUndefined();

      vi.mocked(getMemory).mockResolvedValue(memory({ status: 'HIDDEN' }));
      await act(async () => root.render(<MemoryDetailPage key="hidden" memoryId={MEMORY_ID} />));

      expect(container.textContent).toContain('관리자가 숨긴 기록이에요');
      expect(likeButton()).toBeUndefined();
    });
  });

  describe('내가 남긴 기록', () => {
    beforeEach(() => {
      session.value = signedIn(AUTHOR_ID);
      vi.mocked(getMemory).mockResolvedValue(
        memory({ visibility: 'PRIVATE', lat: 36.3512, lng: 127.3801, approxLat: undefined, approxLng: undefined }),
      );
    });

    it('정확한 위치와 나만 보기 표시, 수정 링크, 삭제 버튼이 있고 돌아가기는 내 기록 지도다', async () => {
      await open();

      expect(container.textContent).toContain('다녀온 곳 (정확한 위치, 나만 봐요)');
      expect(container.textContent).toContain('대전 서구 월평로 1');
      expect(container.textContent).toContain('나만 보기');
      expect(container.querySelector(`a[href="/memories/${MEMORY_ID}/edit"]`)?.textContent).toBe('수정');
      expect(button('삭제')).toBeDefined();
      expect(button('신고')).toBeUndefined();
      expect(likeButton()).toBeUndefined();
      expect(container.querySelector('a[href="/me/memories"]')?.textContent).toContain('내 기록 지도');
    });

    it('삭제를 확인하면 기록을 지우고 내 기록 지도로 이동한다', async () => {
      vi.mocked(deleteMemory).mockResolvedValue(undefined);
      await open();

      await act(async () => button('삭제')?.click());
      expect(dialog()?.textContent).toContain('기록을 삭제할까요?');
      expect(deleteMemory).not.toHaveBeenCalled();

      await act(async () => button('삭제', dialog()!)?.click());

      expect(deleteMemory).toHaveBeenCalledWith(MEMORY_ID);
      expect(window.location.pathname).toBe('/me/memories');
    });

    it('삭제 창에서 취소하면 기록은 그대로 남는다', async () => {
      await open();
      await act(async () => button('삭제')?.click());

      await act(async () => button('취소', dialog()!)?.click());

      expect(dialog()).toBeNull();
      expect(deleteMemory).not.toHaveBeenCalled();
      expect(window.location.pathname).toBe(`/memories/${MEMORY_ID}`);
    });

    it('삭제가 실패하면 창 안에 오류를 보여 주고 이동하지 않는다', async () => {
      vi.mocked(deleteMemory).mockRejectedValue(new ApiError(403, 'ACCESS_DENIED', null));
      await open();
      await act(async () => button('삭제')?.click());

      await act(async () => button('삭제', dialog()!)?.click());

      expect(dialog()?.querySelector('[role="alert"]')?.textContent).toContain('삭제하지 못했어요');
      expect(window.location.pathname).toBe(`/memories/${MEMORY_ID}`);
    });
  });

  describe('다른 사람의 공개 기록 신고', () => {
    it('신고 사유를 고르기 전에는 신고하기가 잠겨 있고, 고르고 보내면 접수 안내가 나온다', async () => {
      vi.mocked(reportMemory).mockResolvedValue(undefined);
      await open();
      expect(container.querySelector(`a[href="/memories/${MEMORY_ID}/edit"]`)).toBeNull();

      await act(async () => button('신고')?.click());
      expect(button('신고하기', dialog()!)?.disabled).toBe(true);

      await act(async () => dialog()?.querySelector<HTMLInputElement>('input[value="PRIVACY"]')?.click());
      await act(async () => button('신고하기', dialog()!)?.click());

      expect(reportMemory).toHaveBeenCalledWith(MEMORY_ID, { reason: 'PRIVACY', content: undefined });
      expect(dialog()?.textContent).toContain('신고를 접수했어요');
    });

    it('이메일 인증 전 회원은 신고와 좋아요가 잠겨 있다', async () => {
      session.value = signedIn(1, 'UNVERIFIED');

      await open();

      expect(button('신고')?.disabled).toBe(true);
      expect(likeButton()?.disabled).toBe(true);
    });
  });
});
