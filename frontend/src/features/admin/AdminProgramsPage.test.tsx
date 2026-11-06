import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ProgramSummary } from '../../api/types';
import { SessionProvider } from '../../app/session/session';
import { AdminProgramsPage } from './AdminProgramsPage';

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

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function program(overrides: Partial<ProgramSummary> = {}): ProgramSummary {
  return {
    programId: 7,
    title: '가을 백패킹 데이',
    locationText: '북한산 백운대 입구',
    spotId: null,
    startAt: '2026-11-21T01:00:00Z',
    endAt: '2026-11-22T03:00:00Z',
    applyOpenAt: '2026-11-10T01:00:00Z',
    applyCloseAt: '2026-11-20T09:00:00Z',
    capacity: 200,
    remainingSeats: 150,
    fee: 30000,
    overnight: true,
    status: 'OPEN',
    ...overrides,
  };
}

type Call = { method: string; url: string; body: unknown };

describe('관리자 행사 관리 목록', () => {
  let container: HTMLDivElement;
  let root: Root;
  let me: typeof ME;
  let calls: Call[];
  let listed: ProgramSummary[];
  let listStatus: number;
  let hasNext: boolean;
  let secondPage: ProgramSummary[];
  let detailReply: () => Promise<Response>;
  let cancelResponse: () => Promise<Response>;

  const text = (element: Element | null) => element?.textContent ?? '';
  const button = (label: string, scope: ParentNode = container) =>
    [...scope.querySelectorAll('button')].find((element) => element.textContent === label);
  const link = (label: string) => [...container.querySelectorAll('a')].find((element) => element.textContent === label);
  const dialog = () => {
    const found = container.querySelector('dialog');
    if (!found) throw new Error('대화상자가 없다');
    return found;
  };
  const programListUrls = () => calls.filter((call) => call.url.startsWith('/api/programs?')).map((call) => call.url);
  const detailUrls = () => calls.filter((call) => call.url === '/api/programs/7').map((call) => call.url);
  const posts = () => calls.filter((call) => call.method === 'POST');

  async function mount() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <AdminProgramsPage />
        </SessionProvider>,
      );
    });
  }

  async function click(element: Element | undefined) {
    await act(async () => element instanceof HTMLElement && element.click());
  }

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    me = ME;
    calls = [];
    listed = [program()];
    listStatus = 200;
    hasNext = false;
    secondPage = [];
    detailReply = async () => json(200, { ...program(), status: 'CANCELED' });
    cancelResponse = async () => json(200, { programId: 7, status: 'CANCELED', canceledApplicationCount: 3 });
    window.history.replaceState(null, '', '/admin/programs');
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.open = true;
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.open = false;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/me') return json(200, me);
        const method = init?.method ?? 'GET';
        calls.push({ method, url, body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined });
        if (url.startsWith('/api/programs?')) {
          if (listStatus !== 200) return json(listStatus, { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-1' });
          const second = url.endsWith('page=1');
          return json(200, {
            content: second ? secondPage : listed,
            page: second ? 1 : 0,
            size: 20,
            hasNext: second ? false : hasNext,
          });
        }
        if (url === '/api/programs/7' && method === 'GET') return detailReply();
        if (url === '/api/admin/programs/7/cancel' && method === 'POST') return cancelResponse();
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  it('행사마다 제목, 상태, 신청 기간, 행사 일정, 남은 자리, 참가비를 보여 준다', async () => {
    await mount();

    const card = container.querySelector('ul[aria-label="행사 목록"] li');
    expect(text(card)).toContain('가을 백패킹 데이');
    expect(text(card)).toContain('신청 받는 중');
    expect(text(card)).toContain('숙박');
    expect(text(card)).toContain('11월 10일 (화) 10:00 ~ 11월 20일 (금) 18:00');
    expect(text(card)).toContain('11월 21일 (토) 10:00 ~ 11월 22일 (일) 12:00');
    expect(text(card)).toContain('150/200');
    expect(text(card)).toContain('30,000원');
    expect(programListUrls()).toEqual(['/api/programs?page=0']);
  });

  it('참가비가 0이면 무료로 보인다', async () => {
    listed = [program({ fee: 0 })];

    await mount();

    expect(text(container.querySelector('ul[aria-label="행사 목록"]'))).toContain('무료');
  });

  it('등록, 신청자 보기, 수정 링크가 알맞은 주소로 이어지고 취소한 행사는 안 나온다고 알려 준다', async () => {
    await mount();

    expect(link('행사 등록')?.getAttribute('href')).toBe('/admin/programs/new');
    expect(link('신청자 보기')?.getAttribute('href')).toBe('/admin/programs/7/applications');
    expect(link('수정')?.getAttribute('href')).toBe('/admin/programs/7/edit');
    expect(text(container)).toContain('취소한 행사는 이 목록에 나오지 않아요.');
  });

  it('상태 구분을 바꾸면 status 쿼리로 다시 불러온다', async () => {
    await mount();

    await click(button('신청 받는 중'));
    await click(button('마감'));
    await click(button('전체'));

    expect(programListUrls()).toEqual([
      '/api/programs?page=0',
      '/api/programs?status=OPEN&page=0',
      '/api/programs?status=CLOSED&page=0',
      '/api/programs?page=0',
    ]);
  });

  it('상태 구분마다 다른 빈 상태 문구를 보여 준다', async () => {
    listed = [];
    await mount();
    expect(text(container)).toContain('등록한 행사가 없어요');

    await click(button('신청 예정'));
    expect(text(container)).toContain('신청 예정인 행사가 없어요');

    await click(button('신청 받는 중'));
    expect(text(container)).toContain('신청을 받는 행사가 없어요');

    await click(button('마감'));
    expect(text(container)).toContain('마감한 행사가 없어요');
  });

  it('더 보기를 누르면 다음 쪽을 이어 붙인다', async () => {
    hasNext = true;
    await mount();

    await click(button('더 보기'));

    expect(programListUrls()).toEqual(['/api/programs?page=0', '/api/programs?page=1']);
  });

  it('불러오지 못하면 오류와 다시 불러오기를 보여 주고, 누르면 다시 불러온다', async () => {
    listStatus = 500;
    await mount();

    expect(text(container.querySelector('[role="alert"]'))).toContain('행사를 불러오지 못했어요');
    expect(text(container.querySelector('[role="alert"]'))).toContain('trace-1');

    listStatus = 200;
    await click(button('다시 불러오기'));

    expect(text(container)).toContain('가을 백패킹 데이');
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });

  it('끝난 행사에는 취소 버튼 대신 취소할 수 없다는 안내를 보여 준다', async () => {
    listed = [
      program({ programId: 7, title: '지난 행사', startAt: '2020-01-10T01:00:00Z', endAt: '2020-01-11T03:00:00Z' }),
      program({ programId: 8, title: '다가오는 행사', endAt: '2099-01-11T03:00:00Z' }),
    ];

    await mount();

    const [past, upcoming] = [...container.querySelectorAll('ul[aria-label="행사 목록"] li')];
    expect(button('행사 취소', past)).toBeUndefined();
    expect(text(past ?? null)).toContain('끝난 행사는 취소할 수 없어요.');
    expect(button('행사 취소', upcoming)).toBeDefined();
  });

  describe('행사 취소', () => {
    async function openCancelDialog() {
      await mount();
      await click(button('행사 취소'));
    }

    it('취소 버튼을 누르면 영향을 쉬운 문장으로 알리는 확인 대화상자가 열리고 요청은 아직 나가지 않는다', async () => {
      await openCancelDialog();

      expect(text(dialog().querySelector('h2'))).toBe('행사를 취소할까요?');
      expect(text(dialog())).toContain('가을 백패킹 데이');
      expect(text(dialog())).toContain('이 행사에 들어온 신청은 모두 취소돼요.');
      expect(text(dialog())).toContain('결제한 신청은 환불되고, 신청한 회원에게 알림이 가요.');
      expect(text(dialog())).toContain('취소한 행사는 되돌릴 수 없어요.');
      expect(posts()).toEqual([]);
    });

    it('대화상자에서 처음 포커스를 받을 수 있는 것은 취소 확정 버튼이 아니라 닫기 버튼이다', async () => {
      await openCancelDialog();

      const focusable = dialog().querySelector('button');
      expect(focusable?.getAttribute('aria-label')).toBe('닫기');
      expect(text(button('아니요, 그대로 둘게요', dialog()) ?? null)).toBe('아니요, 그대로 둘게요');
    });

    it('아니요를 누르면 대화상자가 닫히고 취소 요청은 나가지 않는다', async () => {
      await openCancelDialog();

      await click(button('아니요, 그대로 둘게요', dialog()));

      expect(container.querySelector('dialog')).toBeNull();
      expect(posts()).toEqual([]);
      expect(text(container)).toContain('가을 백패킹 데이');
    });

    it('닫기 버튼을 눌러도 취소 요청은 나가지 않는다', async () => {
      await openCancelDialog();

      await click(dialog().querySelector('button[aria-label="닫기"]') ?? undefined);

      expect(container.querySelector('dialog')).toBeNull();
      expect(posts()).toEqual([]);
    });

    it('확정하면 취소 요청을 한 번 보내고, 목록을 다시 불러와 취소한 신청 수를 알린다', async () => {
      await openCancelDialog();
      listed = [];

      await click(button('행사 취소하기', dialog()));

      expect(posts()).toEqual([{ method: 'POST', url: '/api/admin/programs/7/cancel', body: undefined }]);
      expect(text(container.querySelector('[role="status"]'))).toContain(
        '행사를 취소했어요. 신청 3건도 함께 취소했어요.',
      );
      expect(container.querySelector('dialog')).toBeNull();
      expect(text(container)).not.toContain('가을 백패킹 데이');
      expect(programListUrls()).toEqual(['/api/programs?page=0', '/api/programs?page=0']);
    });

    it('취소할 신청이 없었으면 그렇게 알린다', async () => {
      cancelResponse = async () => json(200, { programId: 7, status: 'CANCELED', canceledApplicationCount: 0 });
      await openCancelDialog();

      await click(button('행사 취소하기', dialog()));

      expect(text(container.querySelector('[role="status"]'))).toContain('행사를 취소했어요. 취소할 신청은 없었어요.');
    });

    it('처리 중에 확정을 다시 눌러도 요청은 한 번만 나간다', async () => {
      let release: () => void = () => undefined;
      const gate = new Promise<void>((resolve) => {
        release = resolve;
      });
      cancelResponse = async () => {
        await gate;
        return json(200, { programId: 7, status: 'CANCELED', canceledApplicationCount: 1 });
      };
      await openCancelDialog();

      const confirm = button('행사 취소하기', dialog());
      await click(confirm);
      await click(confirm);
      await click(button('행사 취소하기', dialog()));

      expect(posts()).toHaveLength(1);
      expect(confirm?.disabled).toBe(true);
      expect(button('아니요, 그대로 둘게요', dialog())?.disabled).toBe(true);

      await act(async () => release());

      expect(posts()).toHaveLength(1);
    });

    it('이미 취소된 행사(409)면 이미 처리됐다고 알리고, 닫으면 그 행사를 목록에서 빼고 다시 불러온다', async () => {
      cancelResponse = async () => json(409, { code: 'PROGRAM_INVALID_STATE', message: 'x', traceId: 't' });
      await openCancelDialog();

      await click(button('행사 취소하기', dialog()));

      const alert = dialog().querySelector('[role="alert"]');
      expect(text(alert)).toContain('이미 취소된 행사예요');
      expect(text(alert)).not.toContain('행사를 취소하지 못했어요');
      expect(container.querySelector('[role="status"]')).toBeNull();
      expect(programListUrls()).toHaveLength(1);

      listed = [];
      await click(button('닫기', dialog()));

      expect(container.querySelector('dialog')).toBeNull();
      expect(text(container.querySelector('[role="status"]'))).toContain('이미 취소된 행사예요.');
      expect(text(container)).not.toContain('가을 백패킹 데이');
      expect(programListUrls()).toHaveLength(2);
    });

    it('끝난 행사 취소를 서버가 거절하면(409 PROGRAM_ENDED) 이유를 알리고 목록은 건드리지 않는다', async () => {
      cancelResponse = async () => json(409, { code: 'PROGRAM_ENDED', message: 'x', traceId: 't' });
      await openCancelDialog();

      await click(button('행사 취소하기', dialog()));

      const alert = text(dialog().querySelector('[role="alert"]'));
      expect(alert).toContain('행사를 취소하지 못했어요');
      expect(alert).toContain('이미 끝난 행사는 취소할 수 없어요.');

      await click(button('닫기', dialog()));

      expect(programListUrls()).toHaveLength(1);
      expect(detailUrls()).toEqual([]);
      expect(text(container)).toContain('가을 백패킹 데이');
    });

    it.each([
      ['응답을 받지 못하면', async () => Promise.reject(new TypeError('Failed to fetch'))],
      ['앞단이 502를 돌려주면', async () => json(502, { code: 'INTERNAL_ERROR', message: 'x', traceId: 't' })],
      ['앞단이 504를 돌려주면', async () => json(504, { code: 'INTERNAL_ERROR', message: 'x', traceId: 't' })],
    ])('%s 취소 여부를 확인하지 못했다고 알린다', async (_name, reply) => {
      cancelResponse = reply;
      await openCancelDialog();

      await click(button('행사 취소하기', dialog()));

      const alert = text(dialog().querySelector('[role="alert"]'));
      expect(alert).toContain('처리 결과를 확인하지 못했어요');
      expect(alert).toContain('행사가 취소됐는지 확인하지 못했어요.');
      expect(alert).not.toContain('행사를 취소하지 못했어요');
    });

    it('응답을 못 받고 닫았을 때 행사가 실제로 취소돼 있으면 그렇게 알리고 목록에서 뺀다', async () => {
      cancelResponse = async () => Promise.reject(new TypeError('Failed to fetch'));
      await openCancelDialog();
      await click(button('행사 취소하기', dialog()));
      listed = [];

      await click(button('닫기', dialog()));

      expect(detailUrls()).toHaveLength(1);
      expect(text(container.querySelector('[role="status"]'))).toContain(
        '행사가 취소된 것을 확인했어요. 신청도 함께 취소됐어요.',
      );
      expect(text(container)).not.toContain('가을 백패킹 데이');
      expect(programListUrls()).toHaveLength(2);
    });

    it('응답을 못 받고 닫았는데 행사가 취소되지 않았으면, 둘째 쪽까지 펼친 목록을 그대로 둔 채 그렇게 알린다', async () => {
      hasNext = true;
      secondPage = [program({ programId: 8, title: '겨울 설악산 야영' })];
      cancelResponse = async () => Promise.reject(new TypeError('Failed to fetch'));
      detailReply = async () => json(200, { ...program(), status: 'OPEN' });
      await mount();
      await click(button('더 보기'));
      await click(button('행사 취소'));
      await click(button('행사 취소하기', dialog()));

      await click(button('닫기', dialog()));

      expect(text(container.querySelector('[role="status"]'))).toContain('행사는 아직 취소되지 않았어요.');
      expect(text(container)).toContain('가을 백패킹 데이');
      expect(text(container)).toContain('겨울 설악산 야영');
      expect(programListUrls()).toEqual(['/api/programs?page=0', '/api/programs?page=1']);
    });

    it('응답을 못 받고 닫았는데 상태도 확인하지 못하면 목록은 그대로 두고 그렇게 알린다', async () => {
      cancelResponse = async () => Promise.reject(new TypeError('Failed to fetch'));
      detailReply = async () => json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 't' });
      await openCancelDialog();
      await click(button('행사 취소하기', dialog()));

      await click(button('닫기', dialog()));

      expect(text(container.querySelector('[role="status"]'))).toContain('행사가 취소됐는지 확인하지 못했어요.');
      expect(text(container)).toContain('가을 백패킹 데이');
      expect(programListUrls()).toHaveLength(1);
    });

    it('응답을 못 받은 취소를 다시 눌렀을 때 이미 처리돼 있으면(409) 이미 취소된 행사라고 알린다', async () => {
      cancelResponse = async () => Promise.reject(new TypeError('Failed to fetch'));
      await openCancelDialog();
      await click(button('행사 취소하기', dialog()));
      cancelResponse = async () => json(409, { code: 'PROGRAM_INVALID_STATE', message: 'x', traceId: 't' });

      await click(button('행사 취소하기', dialog()));

      const alert = text(dialog().querySelector('[role="alert"]'));
      expect(alert).toContain('이미 취소된 행사예요');
      expect(alert).not.toContain('행사를 취소하지 못했어요');
      expect(posts()).toHaveLength(2);
    });

    it('행사를 찾을 수 없으면(404) 대화상자 안에 오류를 보여 준다', async () => {
      cancelResponse = async () => json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      await openCancelDialog();

      await click(button('행사 취소하기', dialog()));

      expect(text(dialog().querySelector('[role="alert"]'))).toContain('찾는 내용이 없거나 삭제됐어요.');
    });

    it('서버 오류면 문의 번호를 함께 보여 주고 다시 시도할 수 있다', async () => {
      cancelResponse = async () => json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-9' });
      await openCancelDialog();

      await click(button('행사 취소하기', dialog()));

      expect(text(dialog().querySelector('[role="alert"]'))).toContain('trace-9');
      expect(button('행사 취소하기', dialog())?.disabled).toBe(false);

      cancelResponse = async () => json(200, { programId: 7, status: 'CANCELED', canceledApplicationCount: 2 });
      await click(button('행사 취소하기', dialog()));

      expect(posts()).toHaveLength(2);
      expect(text(container.querySelector('[role="status"]'))).toContain('신청 2건도 함께 취소했어요.');
    });
  });

  describe('저장 뒤 안내', () => {
    it('등록하고 돌아오면 안내를 한 번 보여 주고 주소에서 쿼리를 지운다', async () => {
      window.history.replaceState(null, '', '/admin/programs?saved=created');

      await mount();

      expect(text(container.querySelector('[role="status"]'))).toContain('행사를 등록했어요.');
      expect(window.location.pathname + window.location.search).toBe('/admin/programs');
    });

    it('수정하고 돌아오면 수정했다는 안내를 보여 준다', async () => {
      window.history.replaceState(null, '', '/admin/programs?saved=revised');

      await mount();

      expect(text(container.querySelector('[role="status"]'))).toContain('행사를 수정했어요.');
      expect(window.location.search).toBe('');
    });

    it('알 수 없는 값이면 안내를 보이지 않는다', async () => {
      window.history.replaceState(null, '', '/admin/programs?saved=other');

      await mount();

      expect(container.querySelector('[role="status"]')).toBeNull();
    });

    it('안내가 없는 주소로 열면 안내도 없다', async () => {
      await mount();

      expect(container.querySelector('[role="status"]')).toBeNull();
    });
  });

  it('관리자가 아니면 목록을 불러오지 않고 안내만 보여 준다', async () => {
    me = { ...ME, role: 'USER' };

    await mount();

    expect(text(container)).toContain('관리자만 볼 수 있어요');
    expect(programListUrls()).toEqual([]);
    expect(button('행사 취소')).toBeUndefined();
  });
});
