import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { fetchMemberProfile } from '../../api/members';
import { fetchMemberMemories } from '../../api/memories';
import type { MemberMemorySummary, MemberProfile } from '../../api/types';
import { MemberMemoriesPage } from './MemberMemoriesPage';

vi.mock('../../api/members', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/members')>()),
  fetchMemberProfile: vi.fn(),
}));

vi.mock('../../api/memories', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/memories')>()),
  fetchMemberMemories: vi.fn(),
}));

vi.mock('./MemoryMap', () => ({ MemoryMap: () => null }));

const session = vi.hoisted(() => ({ status: 'authenticated' as 'authenticated' | 'anonymous' | 'loading' }));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => ({
    status: session.status,
    me: session.status === 'authenticated' ? { memberId: 1, nickname: '산사람' } : null,
  }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function memory(id: number, overrides: Partial<MemberMemorySummary> = {}): MemberMemorySummary {
  return {
    memoryId: id,
    spot: { spotId: 7, name: `장소${id}` },
    approxLat: 37.7,
    approxLng: 128.7,
    visitedDate: '2026-10-03',
    nights: 1,
    imageCount: 2,
    likeCount: 0,
    createdAt: '2026-10-04T00:00:00Z',
    ...overrides,
  };
}

describe('다른 회원의 기록 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const render = (memberId = 5) => act(async () => root.render(<MemberMemoriesPage memberId={memberId} />));

  beforeEach(() => {
    session.status = 'authenticated';
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    vi.mocked(fetchMemberMemories).mockReset();
    vi.mocked(fetchMemberProfile).mockReset();
    vi.mocked(fetchMemberProfile).mockResolvedValue({ nickname: '별바라기' } as MemberProfile);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('회원의 닉네임이 제목에 나오고, 공개한 기록이 누르면 기록 상세로 가는 링크로 보인다', async () => {
    vi.mocked(fetchMemberMemories).mockResolvedValue([memory(11), memory(12, { nights: 0, spot: undefined })]);

    await render();

    expect(container.textContent).toContain('별바라기님의 기록');
    expect(document.title).toBe('별바라기님의 기록 · 뫼와들');
    expect(container.textContent).toContain('정확한 위치 대신 약 1km 범위의 대략적인 위치만 보여요.');
    const first = container.querySelector('a[href="/memories/11"]');
    expect(first?.textContent).toContain('장소11, 10월 3일 (토), 1박, 사진 2장');
    expect(container.querySelector('a[href="/memories/12"]')?.textContent).toContain('다녀온 곳, 10월 3일 (토), 당일');
    expect(container.querySelector('a[href="/members/5"]')?.textContent).toBe('프로필');
    expect(fetchMemberMemories).toHaveBeenCalledWith(5, expect.anything());
  });

  it('닉네임을 불러오지 못해도 기록은 보이고 제목은 회원의 기록이다', async () => {
    vi.mocked(fetchMemberProfile).mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR', null));
    vi.mocked(fetchMemberMemories).mockResolvedValue([memory(11)]);

    await render();

    expect(container.textContent).toContain('회원의 기록');
    expect(container.querySelector('a[href="/memories/11"]')).not.toBeNull();
  });

  it('공개한 기록이 없으면 안내 문구가 나온다', async () => {
    vi.mocked(fetchMemberMemories).mockResolvedValue([]);

    await render();

    expect(container.textContent).toContain('공개한 기록이 아직 없어요');
    expect(container.textContent).not.toContain('정확한 위치 대신');
  });

  it('불러오지 못하면 오류 문구가 나오고, 다시 불러오기를 누르면 기록이 나온다', async () => {
    vi.mocked(fetchMemberMemories)
      .mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', null))
      .mockResolvedValueOnce([memory(11)]);

    await render();

    expect(container.textContent).toContain('기록을 불러오지 못했어요');
    expect(container.textContent).not.toContain('공개한 기록이 아직 없어요');

    await act(async () => button('다시 불러오기')?.click());

    expect(container.querySelector('a[href="/memories/11"]')).not.toBeNull();
    expect(container.textContent).not.toContain('기록을 불러오지 못했어요');
  });

  it('없는 회원이면 다시 불러오기 대신 회원을 찾을 수 없다고 알린다', async () => {
    vi.mocked(fetchMemberMemories).mockRejectedValue(new ApiError(404, 'NOT_FOUND', null));

    await render();

    expect(container.textContent).toContain('회원을 찾을 수 없어요');
    expect(container.textContent).toContain('탈퇴했거나 주소가 잘못됐어요');
    expect(button('다시 불러오기')).toBeUndefined();
  });

  it('로그인 상태를 확인하는 동안에도 기록을 바로 요청하고, 비로그인으로 끝나도 다시 요청하지 않는다', async () => {
    session.status = 'loading';
    vi.mocked(fetchMemberMemories).mockResolvedValue([memory(11)]);

    await render();

    expect(fetchMemberMemories).toHaveBeenCalledTimes(1);
    expect(container.querySelector('a[href="/memories/11"]')).not.toBeNull();

    session.status = 'anonymous';
    await render();

    expect(fetchMemberMemories).toHaveBeenCalledTimes(1);
    expect(container.querySelector('a[href="/memories/11"]')).not.toBeNull();
  });

  it('로그인한 사람이 바뀌면 그 사람 기준으로 기록을 다시 요청한다', async () => {
    session.status = 'anonymous';
    vi.mocked(fetchMemberMemories).mockResolvedValue([memory(11)]);
    await render();
    expect(fetchMemberMemories).toHaveBeenCalledTimes(1);

    session.status = 'authenticated';
    await render();

    expect(fetchMemberMemories).toHaveBeenCalledTimes(2);
  });

  it('다른 회원의 화면으로 바뀌면 앞 회원의 기록은 사라지고 새 회원의 기록이 보인다', async () => {
    vi.mocked(fetchMemberMemories).mockImplementation(async (memberId) => [memory(memberId === 5 ? 11 : 21)]);
    await render(5);
    expect(container.querySelector('a[href="/memories/11"]')).not.toBeNull();

    await render(6);

    expect(container.querySelector('a[href="/memories/11"]')).toBeNull();
    expect(container.querySelector('a[href="/memories/21"]')).not.toBeNull();
    expect(container.querySelector('a[href="/members/6"]')).not.toBeNull();
  });
});
