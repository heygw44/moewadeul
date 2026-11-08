import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AdminReportSummary } from '../../api/types';
import { SessionProvider } from '../../app/session/session';
import { AdminReportsPage } from './AdminReportsPage';

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

function pageOf(content: AdminReportSummary[], page = 0, hasNext = false) {
  return { content, page, size: 20, hasNext };
}

function report(overrides: Partial<AdminReportSummary> = {}): AdminReportSummary {
  return {
    reportId: 5,
    kind: 'MEMBER',
    type: 'NO_SHOW',
    urgent: false,
    status: 'RECEIVED',
    reporter: { memberId: 2, nickname: '신고자' },
    target: { memberId: 3, nickname: '대상자' },
    basecampId: 9,
    createdAt: '2026-11-01T03:00:00Z',
    ...overrides,
  };
}

describe('관리자 신고 처리 목록', () => {
  let container: HTMLDivElement;
  let root: Root;
  let me: typeof ME;
  let respond: (query: URLSearchParams) => Response;
  let queries: URLSearchParams[];

  const text = (element: Element | null = container) => element?.textContent ?? '';
  const button = (label: string) => [...container.querySelectorAll('button')].find((el) => el.textContent === label);
  const rows = () => [...container.querySelectorAll('section[aria-label="신고 목록"] ul > li')];
  const urgentCheckbox = () => {
    const found = [...container.querySelectorAll('label')].find((el) => el.textContent === '긴급 신고만')?.control;
    if (!(found instanceof HTMLInputElement)) throw new Error('긴급 신고만 체크박스가 없다');
    return found;
  };

  async function render() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <AdminReportsPage />
        </SessionProvider>,
      );
    });
  }

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    me = ME;
    queries = [];
    respond = () => json(200, pageOf([report()]));
    window.history.replaceState(null, '', '/admin/reports');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        const parsed = new URL(url, 'http://localhost');
        if (parsed.pathname === '/api/me') return json(200, me);
        if (parsed.pathname === '/api/admin/member-reports') {
          queries.push(parsed.searchParams);
          return respond(parsed.searchParams);
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

  it('신고마다 우선순위, 사유, 신고한 회원과 대상, 상태, 접수 시각을 보여 주고 상세로 이어진다', async () => {
    respond = () =>
      json(
        200,
        pageOf([
          report({ reportId: 5, urgent: true, type: 'MONEY_REQUEST', kind: 'MEMBER', status: 'IN_REVIEW' }),
          report({
            reportId: 6,
            urgent: false,
            type: 'INAPPROPRIATE_REVIEW',
            kind: 'REVIEW',
            status: 'DISMISSED',
            reporter: { memberId: 4, nickname: '다른신고자' },
            target: { memberId: 8, nickname: '다른대상' },
          }),
        ]),
      );

    await render();

    expect(rows()).toHaveLength(2);
    const [first, second] = rows();
    expect(text(first ?? null)).toContain('긴급');
    expect(text(first ?? null)).toContain('금전 요구');
    expect(text(first ?? null)).toContain('회원 신고');
    expect(text(first ?? null)).toContain('신고자 → 대상자');
    expect(text(first ?? null)).toContain('검토 중');
    expect(text(first ?? null)).toContain('11월 1일 (일) 12:00');
    expect(first?.querySelector('a')?.getAttribute('href')).toBe('/admin/reports/5');
    expect(text(second ?? null)).toContain('보통');
    expect(text(second ?? null)).toContain('부적절한 후기');
    expect(text(second ?? null)).toContain('후기 신고');
    expect(text(second ?? null)).toContain('다른신고자 → 다른대상');
    expect(text(second ?? null)).toContain('기각');
    expect(second?.querySelector('a')?.getAttribute('href')).toBe('/admin/reports/6');
  });

  it('처음에는 상태와 긴급 조건 없이 첫 쪽을 요청한다', async () => {
    await render();

    expect(queries).toHaveLength(1);
    expect(queries[0]?.get('status')).toBeNull();
    expect(queries[0]?.get('urgent')).toBeNull();
    expect(queries[0]?.get('page')).toBe('0');
    expect(queries[0]?.get('size')).toBe('20');
    expect(button('전체')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('처리 상태를 고르면 그 상태로 다시 요청하고 목록이 바뀌며, 전체로 돌아가면 상태 조건이 빠진다', async () => {
    respond = (query) =>
      query.get('status') === 'ACTIONED'
        ? json(
            200,
            pageOf([report({ reportId: 7, status: 'ACTIONED', reporter: { memberId: 2, nickname: '조치된신고자' } })]),
          )
        : json(200, pageOf([report()]));
    await render();

    await act(async () => button('조치 완료')?.click());

    expect(queries.at(-1)?.get('status')).toBe('ACTIONED');
    expect(queries.at(-1)?.get('page')).toBe('0');
    expect(button('조치 완료')?.getAttribute('aria-pressed')).toBe('true');
    expect(text(rows()[0] ?? null)).toContain('조치된신고자');

    await act(async () => button('전체')?.click());

    expect(queries.at(-1)?.get('status')).toBeNull();
    expect(text(rows()[0] ?? null)).toContain('신고자 → 대상자');
  });

  it('긴급 신고만을 켜면 urgent=true로 요청하고, 상태 조건과 함께 걸리며, 끄면 빠진다', async () => {
    await render();

    await act(async () => urgentCheckbox().click());
    expect(queries.at(-1)?.get('urgent')).toBe('true');
    expect(queries.at(-1)?.get('status')).toBeNull();

    await act(async () => button('검토 중')?.click());
    expect(queries.at(-1)?.get('urgent')).toBe('true');
    expect(queries.at(-1)?.get('status')).toBe('IN_REVIEW');

    await act(async () => urgentCheckbox().click());
    expect(queries.at(-1)?.get('urgent')).toBeNull();
    expect(queries.at(-1)?.get('status')).toBe('IN_REVIEW');
  });

  it('조건에 맞는 신고가 없으면 빈 상태 안내를 보여 준다', async () => {
    respond = () => json(200, pageOf([]));

    await render();

    expect(text()).toContain('조건에 맞는 신고가 없어요');
    expect(text()).toContain('처리 상태나 긴급 조건을 바꿔 주세요.');
    expect(rows()).toHaveLength(0);
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });

  it('더 보기를 누르면 다음 쪽을 요청해 목록 뒤에 붙이고, 마지막 쪽이면 버튼이 사라진다', async () => {
    respond = (query) =>
      query.get('page') === '1'
        ? json(200, pageOf([report({ reportId: 6, reporter: { memberId: 4, nickname: '둘째신고자' } })], 1, false))
        : json(200, pageOf([report()], 0, true));
    await render();
    expect(rows()).toHaveLength(1);

    await act(async () => button('더 보기')?.click());

    expect(queries.at(-1)?.get('page')).toBe('1');
    expect(rows()).toHaveLength(2);
    expect(text(rows()[1] ?? null)).toContain('둘째신고자 → 대상자');
    expect(button('더 보기')).toBeUndefined();
  });

  it('목록을 못 불러오면 오류와 문의 번호를 보여 주고, 다시 불러오기로 목록을 되찾는다', async () => {
    respond = () => json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-reports' });
    await render();

    const alert = container.querySelector('[role="alert"]');
    expect(text(alert)).toContain('신고를 불러오지 못했어요');
    expect(text(alert)).toContain('trace-reports');
    expect(text()).not.toContain('조건에 맞는 신고가 없어요');

    respond = () => json(200, pageOf([report()]));
    await act(async () => button('다시 불러오기')?.click());

    expect(queries).toHaveLength(2);
    expect(container.querySelector('[role="alert"]')).toBeNull();
    expect(rows()).toHaveLength(1);
  });

  it('관리자가 아니면 목록을 요청하지 않고 안내만 보여 준다', async () => {
    me = { ...ME, role: 'MEMBER' };

    await render();

    expect(text()).toContain('관리자만 볼 수 있어요');
    expect(queries).toHaveLength(0);
  });
});
