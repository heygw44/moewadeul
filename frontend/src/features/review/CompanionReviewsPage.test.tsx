import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import {
  createCompanionReview,
  fetchPendingCompanionReviews,
  fetchReceivedCompanionReviews,
} from '../../api/companionReviews';
import type { Page, PendingCompanionReview, ReceivedCompanionReview } from '../../api/types';
import { CompanionReviewsPage } from './CompanionReviewsPage';

vi.mock('../../api/companionReviews', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/companionReviews')>()),
  createCompanionReview: vi.fn(),
  fetchPendingCompanionReviews: vi.fn(),
  fetchReceivedCompanionReviews: vi.fn(),
}));

const session = vi.hoisted(() => ({ trustLevel: 1 }));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => ({
    status: 'authenticated',
    me: { memberId: 1, nickname: '산사람', trustLevel: session.trustLevel },
  }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const DAY_MS = 24 * 60 * 60 * 1000;

function pending(basecampId: number, overrides: Partial<PendingCompanionReview> = {}): PendingCompanionReview {
  return {
    basecampId,
    basecampTitle: `캠프${basecampId}`,
    completedAt: '2026-10-20T09:00:00+09:00',
    deadline: new Date(Date.now() + 3 * DAY_MS).toISOString(),
    targets: [
      { memberId: 21, nickname: '별바라기' },
      { memberId: 22, nickname: '달빛' },
    ],
    ...overrides,
  };
}

function revealed(reviewId: number, overrides: Record<string, unknown> = {}): ReceivedCompanionReview {
  return {
    reviewId,
    basecampId: 40 + reviewId,
    basecampTitle: `받은캠프${reviewId}`,
    reviewer: { memberId: 30 + reviewId, nickname: `후기자${reviewId}` },
    rejoinWanted: true,
    tags: ['ON_TIME', 'CONSIDERATE'],
    comment: '다음에도 같이 가요',
    createdAt: '2026-10-25T09:00:00+09:00',
    revealed: true,
    ...overrides,
  } as ReceivedCompanionReview;
}

function received(items: ReceivedCompanionReview[], pageNumber = 0, hasNext = false): Page<ReceivedCompanionReview> {
  return { content: items, page: pageNumber, size: 10, hasNext };
}

describe('동행 후기 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const field = (label: string) =>
    [...container.querySelectorAll('label')].find((element) => element.textContent === label)?.querySelector('input');
  const comment = () => container.querySelector<HTMLTextAreaElement>('textarea[name="comment"]');
  const render = () => act(async () => root.render(<CompanionReviewsPage />));

  async function write(text: string) {
    const area = comment();
    const setValue = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')?.set;
    await act(async () => {
      setValue?.call(area, text);
      area?.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }

  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function showModal() {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function close() {
      this.removeAttribute('open');
    };
  });

  beforeEach(() => {
    session.trustLevel = 1;
    window.history.replaceState(null, '', '/me/companion-reviews');
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    vi.mocked(fetchPendingCompanionReviews).mockReset();
    vi.mocked(fetchReceivedCompanionReviews).mockReset();
    vi.mocked(createCompanionReview).mockReset();
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  describe('쓸 후기', () => {
    it('본인확인을 마치지 않은 회원에게는 목록을 요청하지 않고 본인확인 안내만 보인다', async () => {
      session.trustLevel = 0;

      await render();

      expect(fetchPendingCompanionReviews).not.toHaveBeenCalled();
      expect(container.textContent).toContain('본인확인을 마치면 동행 후기를 쓸 수 있어요.');
      expect(container.querySelector('a[href="/identity-verification"]')?.textContent).toBe('본인확인하기');
    });

    it('후기를 쓸 베이스캠프와 함께 간 멤버, 남은 기한이 보이고 기한이 지난 것은 지났다고 보인다', async () => {
      vi.mocked(fetchPendingCompanionReviews).mockResolvedValue([
        pending(1),
        pending(2, { deadline: '2020-01-01T00:00:00Z', targets: [{ memberId: 23, nickname: '구름' }] }),
      ]);

      await render();

      expect(container.textContent).toContain('캠프1');
      expect(container.textContent).toContain('D-3');
      expect(container.querySelector('a[href="/members/21"]')?.textContent).toBe('별바라기');
      expect(container.querySelector('a[href="/members/22"]')?.textContent).toBe('달빛');
      expect(container.textContent).toContain('캠프2');
      expect(container.textContent).toContain('기한이 지났어요');
      expect(container.querySelector('a[href="/members/23"]')?.textContent).toBe('구름');
    });

    it('쓸 후기가 없으면 안내 문구가 나온다', async () => {
      vi.mocked(fetchPendingCompanionReviews).mockResolvedValue([]);

      await render();

      expect(container.textContent).toContain('쓸 후기가 없어요');
    });

    it('불러오지 못하면 오류 문구가 나오고, 다시 불러오기를 누르면 목록이 나온다', async () => {
      vi.mocked(fetchPendingCompanionReviews)
        .mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', null))
        .mockResolvedValueOnce([pending(1)]);

      await render();

      expect(container.textContent).toContain('쓸 후기를 불러오지 못했어요');
      expect(container.textContent).not.toContain('쓸 후기가 없어요');

      await act(async () => button('다시 불러오기')?.click());

      expect(container.textContent).toContain('캠프1');
      expect(container.textContent).not.toContain('쓸 후기를 불러오지 못했어요');
    });

    it('다시 동행하고 싶은지 고르기 전에는 후기 남기기가 잠겨 있고, 고른 뒤 남기면 서버로 가고 목록에서 빠진다', async () => {
      vi.mocked(fetchPendingCompanionReviews)
        .mockResolvedValueOnce([pending(1)])
        .mockResolvedValueOnce([]);
      vi.mocked(createCompanionReview).mockResolvedValue({ reviewId: 5 });
      await render();

      await act(async () => button('후기 쓰기')?.click());

      expect(container.textContent).toContain('별바라기님에게 남길 동행 후기');
      expect(button('후기 남기기')?.disabled).toBe(true);
      expect(container.textContent).toContain('다시 동행하고 싶은지 골라 주세요.');

      await act(async () => field('예')?.click());
      await act(async () => field('시간 약속')?.click());
      await write('  즐거웠어요  ');

      expect(button('후기 남기기')?.disabled).toBe(false);

      await act(async () => button('후기 남기기')?.click());

      expect(createCompanionReview).toHaveBeenCalledWith(1, {
        revieweeId: 21,
        rejoinWanted: true,
        tags: ['ON_TIME'],
        comment: '즐거웠어요',
      });
      expect(container.querySelector('dialog')).toBeNull();
      expect(container.querySelector('[role="status"]')?.textContent).toBe('별바라기님에게 동행 후기를 남겼어요.');
      expect(container.textContent).toContain('쓸 후기가 없어요');
    });

    it('한마디에 대한 서버 검증 오류는 입력란 아래에, 그 밖의 실패는 알림으로 보이고 쓰던 내용은 남는다', async () => {
      vi.mocked(fetchPendingCompanionReviews).mockResolvedValue([pending(1)]);
      vi.mocked(createCompanionReview)
        .mockRejectedValueOnce(
          new ApiError(400, 'INVALID_INPUT', {
            code: 'INVALID_INPUT',
            message: '',
            fieldErrors: [{ field: 'comment', reason: '욕설은 쓸 수 없어요' }],
          }),
        )
        .mockRejectedValueOnce(new ApiError(409, 'COMPANION_REVIEW_DUPLICATED', null));
      await render();
      await act(async () => button('후기 쓰기')?.click());
      await act(async () => field('아니요')?.click());
      await write('별로였어요');

      await act(async () => button('후기 남기기')?.click());

      expect(container.textContent).toContain('욕설은 쓸 수 없어요');
      expect(container.textContent).not.toContain('후기를 남기지 못했어요');
      expect(comment()?.value).toBe('별로였어요');

      await act(async () => button('후기 남기기')?.click());

      expect(container.textContent).toContain('후기를 남기지 못했어요');
      expect(container.textContent).toContain('이 멤버에게는 이미 동행 후기를 썼어요');
      expect(container.querySelector('dialog')).not.toBeNull();
      expect(comment()?.value).toBe('별로였어요');
    });
  });

  describe('받은 후기', () => {
    beforeEach(() => {
      window.history.replaceState(null, '', '/me/companion-reviews?tab=received');
    });

    it('상대가 남긴 후기는 작성자, 다시 동행 의사, 태그, 한마디가 보이고 아직 내가 쓰지 않은 후기는 잠겨 있다', async () => {
      vi.mocked(fetchReceivedCompanionReviews).mockResolvedValue(
        received([
          revealed(1),
          revealed(2, { rejoinWanted: false, tags: [], comment: null }),
          { basecampId: 77, revealed: false },
        ]),
      );

      await render();

      expect(container.textContent).toContain('받은캠프1');
      expect(container.querySelector('a[href="/members/31"]')?.textContent).toBe('후기자1');
      expect(container.textContent).toContain('다시 동행하고 싶어요');
      expect(container.textContent).toContain('시간 약속 · 배려');
      expect(container.textContent).toContain('다음에도 같이 가요');
      expect(container.textContent).toContain('다시 동행하고 싶지는 않아요');
      expect(container.textContent).toContain('내 후기를 남기면 상대가 쓴 후기를 볼 수 있어요.');
      expect(container.querySelector('a[href="/basecamps/77"]')?.textContent).toBe('베이스캠프 보기');
      expect(fetchPendingCompanionReviews).not.toHaveBeenCalled();
    });

    it('받은 후기가 없으면 안내 문구가 나온다', async () => {
      vi.mocked(fetchReceivedCompanionReviews).mockResolvedValue(received([]));

      await render();

      expect(container.textContent).toContain('받은 후기가 없어요');
    });

    it('불러오지 못하면 오류 문구가 나오고, 다시 불러오기를 누르면 후기가 나온다', async () => {
      vi.mocked(fetchReceivedCompanionReviews)
        .mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', null))
        .mockResolvedValueOnce(received([revealed(1)]));

      await render();

      expect(container.textContent).toContain('받은 후기를 불러오지 못했어요');

      await act(async () => button('다시 불러오기')?.click());

      expect(container.textContent).toContain('받은캠프1');
      expect(container.textContent).not.toContain('받은 후기를 불러오지 못했어요');
    });

    it('더 보기를 누르면 다음 쪽 후기가 아래에 붙고 더 보기 버튼이 사라진다', async () => {
      vi.mocked(fetchReceivedCompanionReviews).mockImplementation(async (pageNumber) =>
        pageNumber === 0 ? received([revealed(1)], 0, true) : received([revealed(2)], 1, false),
      );
      await render();

      await act(async () => button('더 보기')?.click());

      expect(container.textContent).toMatch(/받은캠프1.*받은캠프2/);
      expect(button('더 보기')).toBeUndefined();
    });

    it('후기 신고를 누르면 그 후기를 쓴 사람을 신고하는 창이 열린다', async () => {
      vi.mocked(fetchReceivedCompanionReviews).mockResolvedValue(received([revealed(1)]));
      await render();

      await act(async () => button('후기 신고')?.click());

      expect(container.querySelector('dialog')?.textContent).toContain('후기자1님이 내게 쓴 동행 후기를 신고해요.');
    });
  });

  it('탭을 누르면 쓸 후기와 받은 후기가 바뀌고 주소에 탭이 남는다', async () => {
    vi.mocked(fetchPendingCompanionReviews).mockResolvedValue([pending(1)]);
    vi.mocked(fetchReceivedCompanionReviews).mockResolvedValue(received([revealed(1)]));
    await render();
    expect(container.textContent).toContain('캠프1');

    await act(async () => button('받은 후기')?.click());

    expect(window.location.search).toBe('?tab=received');
    expect(container.textContent).toContain('받은캠프1');
    expect(container.textContent).not.toContain('별바라기');

    await act(async () => button('쓸 후기')?.click());

    expect(window.location.search).toBe('');
    expect(container.textContent).toContain('별바라기');
  });
});
