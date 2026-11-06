import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { listPosts } from '../../api/community';
import { ApiError } from '../../api/client';
import { fetchSpotDetail } from '../../api/spots';
import type { CommunityPostSummary, Page, SpotDetail } from '../../api/types';
import { CommunityListPage } from './CommunityListPage';

vi.mock('../../api/community', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/community')>()),
  listPosts: vi.fn(),
}));

vi.mock('../../api/spots', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/spots')>()),
  fetchSpotDetail: vi.fn(),
}));

vi.mock('../notification/unreadCount', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../notification/unreadCount')>()),
  refreshUnreadCount: vi.fn(),
}));

const session = vi.hoisted(() => ({
  value: { status: 'authenticated', me: { memberId: 1, nickname: '산사람', status: 'ACTIVE' } } as {
    status: string;
    me: { memberId: number; nickname: string; status: string } | null;
  },
}));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => session.value,
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function post(id: number, overrides: Partial<CommunityPostSummary> = {}): CommunityPostSummary {
  return {
    postId: id,
    title: `글 제목${id}`,
    excerpt: `본문 미리보기${id}`,
    author: { memberId: 7, nickname: '능선' },
    likeCount: 3,
    commentCount: 2,
    viewCount: 41,
    imageCount: 0,
    createdAt: '2026-10-01T00:00:00Z',
    ...overrides,
  };
}

function page(items: CommunityPostSummary[], pageNumber: number, hasNext: boolean): Page<CommunityPostSummary> {
  return { content: items, page: pageNumber, size: 20, hasNext } as Page<CommunityPostSummary>;
}

describe('커뮤니티 글 목록 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const searchInput = () => container.querySelector<HTMLInputElement>('input[type="search"]');
  const lastFilter = () => vi.mocked(listPosts).mock.calls.at(-1)?.[0];

  async function type(field: HTMLInputElement | null, value: string) {
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.call(field, value);
      field?.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }

  async function open(url = '/community') {
    window.history.replaceState(null, '', url);
    await act(async () => root.render(<CommunityListPage />));
  }

  beforeEach(() => {
    session.value = { status: 'authenticated', me: { memberId: 1, nickname: '산사람', status: 'ACTIVE' } };
    vi.mocked(listPosts).mockReset();
    vi.mocked(fetchSpotDetail).mockReset();
    vi.mocked(fetchSpotDetail).mockResolvedValue({ name: '대둔산 박지' } as SpotDetail);
    vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
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

  it('불러온 글의 제목, 미리보기, 글쓴이, 반응 수를 보여 주고 누르면 글 상세로 가는 링크다', async () => {
    vi.mocked(listPosts).mockResolvedValue(page([post(1), post(2)], 0, false));

    await open();

    expect(container.textContent).toContain('글 제목1');
    expect(container.textContent).toContain('본문 미리보기1');
    expect(container.textContent).toContain('능선');
    expect(container.textContent).toContain('좋아요 3');
    expect(container.textContent).toContain('댓글 2');
    expect(container.textContent).toContain('조회 41');
    expect(container.querySelector('a[href="/community/2"]')?.textContent).toContain('글 제목2');
    expect(button('더 보기')).toBeUndefined();
  });

  it('글이 하나도 없으면 빈 상태 안내를 보여 준다', async () => {
    vi.mocked(listPosts).mockResolvedValue(page([], 0, false));

    await open();

    expect(container.textContent).toContain('아직 올라온 글이 없어요');
  });

  it('인기글 탭을 누르면 주소가 바뀌고 인기글만 다시 불러온다', async () => {
    vi.mocked(listPosts).mockResolvedValue(page([post(1)], 0, false));
    await open();
    expect(lastFilter()).toMatchObject({ popular: false });

    await act(async () => button('인기글')?.click());

    expect(window.location.search).toBe('?tab=popular');
    expect(lastFilter()).toMatchObject({ popular: true });
    expect(button('인기글')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('처음 불러오지 못하면 오류 문구를 보여 주고 다시 불러오기를 누르면 글이 나온다', async () => {
    vi.mocked(listPosts)
      .mockRejectedValueOnce(
        new ApiError(500, 'INTERNAL_ERROR', { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-9' }),
      )
      .mockResolvedValueOnce(page([post(1)], 0, false));

    await open();

    expect(container.querySelector('[role="alert"]')?.textContent).toContain('글을 불러오지 못했어요');
    expect(container.textContent).toContain('일시적인 문제가 생겼어요');
    expect(container.textContent).toContain('문의 번호 trace-9');
    expect(container.textContent).not.toContain('아직 올라온 글이 없어요');

    await act(async () => button('다시 불러오기')?.click());

    expect(container.textContent).toContain('글 제목1');
    expect(container.textContent).not.toContain('글을 불러오지 못했어요');
  });

  it('더 보기를 누르면 다음 쪽 글이 아래에 붙고 마지막 쪽이면 더 보기가 사라진다', async () => {
    vi.mocked(listPosts).mockImplementation(async (_filter, pageNumber) =>
      pageNumber === 0 ? page([post(1), post(2)], 0, true) : page([post(3)], 1, false),
    );
    await open();
    expect(container.textContent).not.toContain('글 제목3');

    await act(async () => button('더 보기')?.click());

    expect(container.textContent).toMatch(/글 제목1.*글 제목2.*글 제목3/);
    expect(button('더 보기')).toBeUndefined();
  });

  it('더 보기가 실패하면 받아 둔 글은 그대로 두고 오류 문구를 보여 준다', async () => {
    vi.mocked(listPosts).mockImplementation(async (_filter, pageNumber) => {
      if (pageNumber === 0) return page([post(1)], 0, true);
      throw new ApiError(429, 'TOO_MANY_REQUESTS', null);
    });
    await open();

    await act(async () => button('더 보기')?.click());

    expect(container.textContent).toContain('글 제목1');
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('요청이 너무 많아요');
    expect(button('더 보기')).toBeDefined();
  });

  it('검색어를 한 글자만 적으면 안내하고 검색하지 않는다', async () => {
    vi.mocked(listPosts).mockResolvedValue(page([post(1)], 0, false));
    await open();
    const callsBefore = vi.mocked(listPosts).mock.calls.length;

    await type(searchInput(), '텐');
    await act(async () => button('검색')?.click());

    expect(container.textContent).toContain('검색어를 두 글자 이상 적어 주세요.');
    expect(window.location.search).toBe('');
    expect(vi.mocked(listPosts).mock.calls).toHaveLength(callsBefore);
  });

  it('검색어를 두 글자 이상 적어 검색하면 그 검색어로 다시 불러온다', async () => {
    vi.mocked(listPosts).mockResolvedValue(page([post(1)], 0, false));
    await open();

    await type(searchInput(), '텐트');
    await act(async () => button('검색')?.click());

    expect(window.location.search).toBe('?searchType=TITLE_CONTENT&keyword=%ED%85%90%ED%8A%B8');
    expect(lastFilter()).toMatchObject({ keyword: '텐트', searchType: 'TITLE_CONTENT' });
    expect(container.textContent).toContain('최근 글 1만 개 안에서 찾았어요.');
  });

  it('검색 결과가 없으면 안내하고 검색어 지우기를 누르면 전체 글을 다시 불러온다', async () => {
    vi.mocked(listPosts).mockImplementation(async (filter) =>
      filter.keyword ? page([], 0, false) : page([post(1)], 0, false),
    );
    await open('/community?searchType=TITLE&keyword=없는말');
    expect(container.textContent).toContain('검색 결과가 없어요');

    await act(async () => button('검색어 지우기')?.click());

    expect(window.location.search).toBe('');
    expect(lastFilter()?.keyword).toBeUndefined();
    expect(container.textContent).toContain('글 제목1');
  });

  it('장소로 좁혀 본 목록은 장소 이름을 보여 주고 전체 글 보기로 되돌릴 수 있다', async () => {
    vi.mocked(listPosts).mockResolvedValue(page([post(1)], 0, false));

    await open('/community?spotId=12');

    expect(lastFilter()).toMatchObject({ spotId: 12 });
    expect(container.textContent).toContain('대둔산 박지 글');

    await act(async () => button('전체 글 보기')?.click());

    expect(window.location.search).toBe('');
    expect(lastFilter()?.spotId).toBeUndefined();
  });

  it('이메일 인증을 마친 회원에게는 글쓰기 링크가 있다', async () => {
    vi.mocked(listPosts).mockResolvedValue(page([], 0, false));

    await open();

    expect(container.querySelector('a[href="/community/new"]')?.textContent).toBe('글쓰기');
  });

  it('이메일 인증 전 회원의 글쓰기는 잠겨 있고 이유를 알려 준다', async () => {
    session.value = { status: 'authenticated', me: { memberId: 1, nickname: '산사람', status: 'UNVERIFIED' } };
    vi.mocked(listPosts).mockResolvedValue(page([], 0, false));

    await open();

    expect(button('글쓰기')?.disabled).toBe(true);
    expect(container.textContent).toContain('이메일 인증을 마치면 쓸 수 있어요');
    expect(container.querySelector('a[href="/community/new"]')).toBeNull();
  });
});
