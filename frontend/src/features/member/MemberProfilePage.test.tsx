import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { fetchMemberCompanionReviews } from '../../api/companionReviews';
import { fetchMemberProfile } from '../../api/members';
import type { MemberProfile, Page, PublicCompanionReview } from '../../api/types';
import { MemberProfilePage } from './MemberProfilePage';

const viewer = vi.hoisted(() => ({ status: 'ACTIVE' as 'ACTIVE' | 'UNVERIFIED', sessionLoading: false }));

vi.mock('../../api/members', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/members')>()),
  fetchMemberProfile: vi.fn(),
}));

vi.mock('../../api/companionReviews', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/companionReviews')>()),
  fetchMemberCompanionReviews: vi.fn(),
}));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () =>
    viewer.sessionLoading
      ? { status: 'loading', me: null, logout: vi.fn() }
      : {
          status: 'authenticated',
          me: {
            memberId: 1,
            email: 'a@b.c',
            nickname: '산사람',
            status: viewer.status,
            role: 'USER',
            identityVerified: false,
            trustLevel: 0,
          },
          logout: vi.fn(),
        },
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const PROFILE: MemberProfile = {
  memberId: 7,
  nickname: '새벽능선',
  trustLevel: 1,
  completedCompanions: 2,
  companionReviewSummary: { rejoinRate: 100, topTags: ['ON_TIME'] },
};

function reviewPage(content: PublicCompanionReview[], page = 0, hasNext = false): Page<PublicCompanionReview> {
  return { content, page, size: 10, hasNext };
}

const FIRST: PublicCompanionReview = {
  tags: ['ON_TIME', 'CONSIDERATE'],
  comment: '약속 시간에 먼저 와 있었어요.',
  createdAt: '2026-10-20T03:00:00Z',
};
const SECOND: PublicCompanionReview = {
  tags: [],
  comment: '짐을 같이 들어 줬어요.',
  createdAt: '2026-10-10T03:00:00Z',
};

describe('회원 프로필: 받은 후기', () => {
  let container: HTMLDivElement;
  let root: Root;

  const text = () => container.textContent ?? '';
  const render = () => act(async () => root.render(<MemberProfilePage memberId={7} />));
  const button = (label: string) => [...container.querySelectorAll('button')].find((b) => b.textContent === label);

  beforeEach(() => {
    viewer.status = 'ACTIVE';
    viewer.sessionLoading = false;
    vi.mocked(fetchMemberProfile).mockResolvedValue(PROFILE);
    vi.mocked(fetchMemberCompanionReviews).mockReset();
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('받은 후기의 태그, 한마디, 작성 시각을 보여 준다', async () => {
    vi.mocked(fetchMemberCompanionReviews).mockResolvedValue(reviewPage([FIRST, SECOND]));

    await render();

    expect([...container.querySelectorAll('h2')].map((h) => h.textContent)).toContain('받은 후기');
    expect(text()).toContain('시간 약속 · 배려');
    expect(text()).toContain('약속 시간에 먼저 와 있었어요.');
    expect(text()).toContain('짐을 같이 들어 줬어요.');
    expect(text()).toContain('2026');
    expect(button('더 보기')).toBeUndefined();
  });

  it('후기가 없으면 빈 상태를 보여 준다', async () => {
    vi.mocked(fetchMemberCompanionReviews).mockResolvedValue(reviewPage([]));

    await render();

    expect(text()).toContain('아직 받은 후기가 없어요');
  });

  it('다음 쪽이 있으면 더 보기로 이어서 불러온다', async () => {
    vi.mocked(fetchMemberCompanionReviews)
      .mockResolvedValueOnce(reviewPage([FIRST], 0, true))
      .mockResolvedValueOnce(reviewPage([SECOND], 1, false));

    await render();
    expect(text()).not.toContain('짐을 같이 들어 줬어요.');

    await act(async () => button('더 보기')?.click());

    expect(text()).toContain('약속 시간에 먼저 와 있었어요.');
    expect(text()).toContain('짐을 같이 들어 줬어요.');
    expect(button('더 보기')).toBeUndefined();
    expect(fetchMemberCompanionReviews).toHaveBeenLastCalledWith(7, 1, undefined);
  });

  it('이메일 인증 전 회원에게는 후기를 부르지 않고 인증 안내만 보여 준다', async () => {
    viewer.status = 'UNVERIFIED';

    await render();

    expect(fetchMemberCompanionReviews).not.toHaveBeenCalled();
    expect(text()).toContain('이메일 인증을 마치면 이 회원이 받은 후기를 읽을 수 있어요.');
    expect(text()).not.toContain('불러오지 못했어요');
    expect(text()).toContain('완료한 동행');
  });

  it('세션을 확인하는 동안에는 후기를 부르지 않는다', async () => {
    viewer.sessionLoading = true;

    await render();

    expect(fetchMemberCompanionReviews).not.toHaveBeenCalled();
    expect(text()).not.toContain('불러오지 못했어요');
    expect(text()).toContain('완료한 동행');
  });

  it('로그인하지 않은 방문자에게는 후기를 부르지 않고 로그인 안내만 보여 준다', async () => {
    vi.mocked(fetchMemberProfile).mockResolvedValue({ memberId: 7, nickname: '새벽능선' });

    await render();

    expect(fetchMemberCompanionReviews).not.toHaveBeenCalled();
    expect(text()).toContain('로그인하면 이 회원의 신뢰 단계와 동행 기록을 볼 수 있어요.');
    expect(text()).not.toContain('불러오지 못했어요');
  });

  it('후기를 보려는 사이 회원이 탈퇴했으면 오류 없이 빈 상태로 둔다', async () => {
    vi.mocked(fetchMemberCompanionReviews).mockRejectedValue(new ApiError(404, 'NOT_FOUND', null));

    await render();

    expect(text()).toContain('아직 받은 후기가 없어요');
    expect(text()).not.toContain('불러오지 못했어요');
  });

  it('후기를 불러오지 못하면 안내하고 다시 불러올 수 있다', async () => {
    vi.mocked(fetchMemberCompanionReviews)
      .mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', null))
      .mockResolvedValueOnce(reviewPage([FIRST]));

    await render();
    expect(text()).toContain('받은 후기를 불러오지 못했어요');

    await act(async () => button('다시 불러오기')?.click());

    expect(text()).toContain('약속 시간에 먼저 와 있었어요.');
  });
});
