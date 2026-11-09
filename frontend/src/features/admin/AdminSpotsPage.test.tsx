import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AdminSpotSummary } from '../../api/types';
import { SessionProvider } from '../../app/session/session';
import { AdminSpotsPage } from './AdminSpotsPage';

vi.mock('../../map/addressName', () => ({ addressAt: vi.fn(async () => null) }));

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

function pageOf(content: AdminSpotSummary[], page = 0, hasNext = false) {
  return { content, page, size: 20, hasNext };
}

function spot(overrides: Partial<AdminSpotSummary> = {}): AdminSpotSummary {
  return {
    spotId: 11,
    type: 'BAKJI',
    name: '강가 박지',
    status: 'PENDING_REVIEW',
    lat: 37.5,
    lng: 127.25,
    parkWarning: false,
    reporter: { memberId: 7, nickname: '제보자A' },
    reportCount: 5,
    reasonCounts: { ILLEGAL_AREA: 3, CLOSED: 0, FALSE_INFO: 2 },
    recentReports: [{ reason: 'FALSE_INFO', content: '사실과 달라요', createdAt: '2026-11-01T03:00:00Z' }],
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

describe('관리자 박지 검토', () => {
  let container: HTMLDivElement;
  let root: Root;
  let calls: Call[];
  let me: typeof ME;
  let lists: Record<string, () => Response>;
  let moderate: (path: string) => Response;

  const text = (element: Element | null = container) => element?.textContent ?? '';
  const button = (label: string, scope: ParentNode = container) =>
    [...scope.querySelectorAll('button')].find((el) => el.textContent === label);
  const cards = () => [...container.querySelectorAll('section[aria-label="박지 검토 목록"] > ul > li')];
  const listCalls = () => calls.filter((call) => call.method === 'GET' && call.path === '/api/admin/spots');
  const postCalls = () => calls.filter((call) => call.method === 'POST');
  const dialog = () => {
    const found = container.querySelector('dialog');
    if (!found) throw new Error('숨기기 확인 대화상자가 없다');
    return found;
  };

  async function render() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <AdminSpotsPage />
        </SessionProvider>,
      );
    });
  }

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    calls = [];
    me = ME;
    lists = { PENDING_REVIEW: () => json(200, pageOf([spot()])), HIDDEN: () => json(200, pageOf([])) };
    moderate = () => json(200, { spotId: 11, status: 'HIDDEN' });
    window.history.replaceState(null, '', '/admin/spots');
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
        if (parsed.pathname === '/api/me') return json(200, me);
        if (method === 'POST') return moderate(parsed.pathname);
        if (parsed.pathname === '/api/admin/spots' && status !== null && lists[status]) return lists[status]();
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  it('검토 대기 박지의 이름, 종류, 제보자, 위치, 신고 수와 사유별 수, 최근 신고를 보여 준다', async () => {
    await render();

    expect(listCalls()).toHaveLength(1);
    expect(listCalls()[0]?.status).toBe('PENDING_REVIEW');
    expect(listCalls()[0]?.page).toBe('0');
    expect(cards()).toHaveLength(1);
    const card = cards()[0] ?? null;
    expect(text(card)).toContain('강가 박지');
    expect(text(card)).toContain('검토 대기');
    expect(text(card)).toContain('제보자A');
    expect(text(card)).toContain('37.50000, 127.25000');
    expect(text(card)).toContain('5건 (야영이 금지된 곳 3, 더는 야영할 수 없는 곳 0, 정보가 사실과 다른 곳 2)');
    expect(text(card)).toContain('11월 1일 (일) 12:00');
    expect(text(card)).toContain('사실과 달라요');
    expect(text(card)).not.toContain('공원 경계 경고');
  });

  it('제보자가 없으면 공공데이터로 적고, 공원 경계에 걸린 박지에는 경고를 붙인다', async () => {
    lists.PENDING_REVIEW = () => json(200, pageOf([spot({ reporter: null, parkWarning: true, recentReports: [] })]));

    await render();

    const card = cards()[0] ?? null;
    expect(text(card)).toContain('공공데이터');
    expect(text(card)).toContain('공원 경계 경고');
    expect(text(card)).not.toContain('최근 신고');
  });

  it('숨김 탭으로 바꾸면 숨김 목록을 요청하고, 그 카드에는 숨기기 없이 복구만 있다', async () => {
    lists.HIDDEN = () => json(200, pageOf([spot({ spotId: 12, name: '숨긴 박지', status: 'HIDDEN' })]));
    await render();
    expect(button('숨기기')).toBeDefined();

    await act(async () => button('숨김')?.click());

    expect(listCalls().at(-1)?.status).toBe('HIDDEN');
    expect(button('숨김')?.getAttribute('aria-pressed')).toBe('true');
    expect(cards()).toHaveLength(1);
    expect(text(cards()[0] ?? null)).toContain('숨긴 박지');
    expect(button('숨기기')).toBeUndefined();
    expect(button('복구')).toBeDefined();
  });

  it('숨기기를 누르면 확인 대화상자가 뜨고, 취소하면 아무 것도 보내지 않는다', async () => {
    await render();

    await act(async () => button('숨기기')?.click());

    expect(text(dialog())).toContain('박지를 숨길까요?');
    expect(text(dialog())).toContain("'강가 박지' 박지를 지도와 검색에서 숨겨요.");

    await act(async () => button('취소', dialog())?.click());

    expect(container.querySelector('dialog')).toBeNull();
    expect(postCalls()).toHaveLength(0);
    expect(cards()).toHaveLength(1);
  });

  it('대화상자에서 숨기기를 확인하면 그 박지의 숨기기 API를 부르고 목록을 다시 받아 카드가 빠진다', async () => {
    await render();
    lists.PENDING_REVIEW = () => json(200, pageOf([]));

    await act(async () => button('숨기기')?.click());
    await act(async () => button('숨기기', dialog())?.click());

    expect(postCalls().map((call) => call.path)).toEqual(['/api/admin/spots/11/hide']);
    expect(listCalls()).toHaveLength(2);
    expect(container.querySelector('dialog')).toBeNull();
    expect(cards()).toHaveLength(0);
    expect(text()).toContain('검토를 기다리는 박지가 없어요');
  });

  it('숨김 탭에서 복구를 누르면 복구 API를 부르고 목록에서 빠진다', async () => {
    lists.HIDDEN = () => json(200, pageOf([spot({ spotId: 12, name: '숨긴 박지', status: 'HIDDEN' })]));
    moderate = () => json(200, { spotId: 12, status: 'ACTIVE' });
    await render();
    await act(async () => button('숨김')?.click());
    lists.HIDDEN = () => json(200, pageOf([]));

    await act(async () => button('복구')?.click());

    expect(postCalls().map((call) => call.path)).toEqual(['/api/admin/spots/12/restore']);
    expect(listCalls().at(-1)?.status).toBe('HIDDEN');
    expect(cards()).toHaveLength(0);
    expect(text()).toContain('숨긴 박지가 없어요');
  });

  it('숨기기가 실패하면 대화상자를 닫고 오류를 보여 주며 카드는 그대로 남는다', async () => {
    moderate = () => json(409, { code: 'INVALID_STATE', message: 'x', traceId: 'trace-hide' });
    await render();

    await act(async () => button('숨기기')?.click());
    await act(async () => button('숨기기', dialog())?.click());

    expect(postCalls()).toHaveLength(1);
    expect(container.querySelector('dialog')).toBeNull();
    expect(text(container.querySelector('[role="alert"]'))).toContain('상태를 바꾸지 못했어요');
    expect(cards()).toHaveLength(1);
    expect(listCalls()).toHaveLength(1);
  });

  it('검토 대기 박지가 없으면 빈 상태 안내를 보여 준다', async () => {
    lists.PENDING_REVIEW = () => json(200, pageOf([]));

    await render();

    expect(text()).toContain('검토를 기다리는 박지가 없어요');
    expect(text()).toContain('신고가 쌓인 박지가 여기에 모여요.');
    expect(cards()).toHaveLength(0);
  });

  it('더 보기를 누르면 다음 쪽을 이어 붙인다', async () => {
    lists.PENDING_REVIEW = () => {
      const requested = listCalls().at(-1)?.page;
      return requested === '1'
        ? json(200, pageOf([spot({ spotId: 12, name: '둘째 박지' })], 1, false))
        : json(200, pageOf([spot()], 0, true));
    };
    await render();

    await act(async () => button('더 보기')?.click());

    expect(listCalls().at(-1)?.page).toBe('1');
    expect(cards()).toHaveLength(2);
    expect(text(cards()[1] ?? null)).toContain('둘째 박지');
    expect(button('더 보기')).toBeUndefined();
  });

  it('목록을 못 불러오면 오류와 문의 번호를 보여 주고, 다시 불러오기로 되찾는다', async () => {
    lists.PENDING_REVIEW = () => json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-spots' });
    await render();

    const alert = container.querySelector('[role="alert"]');
    expect(text(alert)).toContain('박지를 불러오지 못했어요');
    expect(text(alert)).toContain('trace-spots');
    expect(text()).not.toContain('검토를 기다리는 박지가 없어요');

    lists.PENDING_REVIEW = () => json(200, pageOf([spot()]));
    await act(async () => button('다시 불러오기')?.click());

    expect(listCalls()).toHaveLength(2);
    expect(container.querySelector('[role="alert"]')).toBeNull();
    expect(cards()).toHaveLength(1);
  });

  it('관리자가 아니면 목록을 요청하지 않고 안내만 보여 준다', async () => {
    me = { ...ME, role: 'MEMBER' };

    await render();

    expect(text()).toContain('관리자만 볼 수 있어요');
    expect(listCalls()).toHaveLength(0);
  });
});
