import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AdminAuditLog } from '../../api/types';
import { SessionProvider } from '../../app/session/session';
import { AdminAuditLogsPage } from './AdminAuditLogsPage';

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

function log(overrides: Partial<AdminAuditLog>): AdminAuditLog {
  return {
    auditLogId: 100,
    adminId: 1,
    action: 'REPORT_ACTION',
    targetType: 'MEMBER_REPORT',
    targetId: 5,
    detail: null,
    createdAt: '2026-11-05T05:30:00Z',
    ...overrides,
  };
}

function pageOf(content: AdminAuditLog[], page = 0, hasNext = false) {
  return { content, page, size: 20, hasNext };
}

describe('관리자 감사 기록 화면', () => {
  let container: HTMLDivElement;
  let root: Root;
  let me: typeof ME;
  let respond: (query: URLSearchParams) => Response;
  let queries: URLSearchParams[];

  const text = () => container.textContent ?? '';
  const buttons = (label: string) => [...container.querySelectorAll('button')].filter((el) => el.textContent === label);
  const button = (label: string) => buttons(label)[0];
  const field = (label: string) => {
    const found = [...container.querySelectorAll('label')].find((el) => el.textContent === label);
    const control = found?.control;
    if (!(control instanceof HTMLInputElement || control instanceof HTMLSelectElement)) {
      throw new Error(`${label} 입력란이 없다`);
    }
    return control;
  };

  async function type(label: string, value: string) {
    const control = field(label);
    await act(async () => {
      const proto = control instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
      Object.getOwnPropertyDescriptor(proto, 'value')?.set?.call(control, value);
      control.dispatchEvent(new Event(control instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
    });
  }

  async function render() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <AdminAuditLogsPage />
        </SessionProvider>,
      );
    });
  }

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    me = ME;
    queries = [];
    respond = () => json(200, pageOf([]));
    window.history.replaceState(null, '', '/admin/audit-logs');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url === '/api/me') return json(200, me);
        const parsed = new URL(url, 'http://localhost');
        if (parsed.pathname === '/api/admin/audit-logs') {
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

  it('기록마다 조치 이름, 대상, 관리자, 시각, detail 요약을 보인다', async () => {
    respond = () =>
      json(
        200,
        pageOf([
          log({
            auditLogId: 101,
            adminId: 3,
            action: 'REPORT_ACTION',
            targetType: 'MEMBER_REPORT',
            targetId: 5,
            detail: { fromStatus: 'IN_REVIEW', toStatus: 'ACTIONED', sanctionType: 'SUSPEND_7D', level: 2 },
          }),
          log({
            auditLogId: 100,
            adminId: 4,
            action: 'PROGRAM_CANCEL',
            targetType: 'PROGRAM',
            targetId: 9,
            detail: { fromStatus: 'SCHEDULED', toStatus: 'CANCELED', canceledApplicationCount: 3 },
          }),
        ]),
      );
    await render();

    const items = [...container.querySelectorAll('ul[aria-label="감사 기록 목록"] > li')];
    expect(items).toHaveLength(2);
    expect(items[0]?.textContent).toContain('신고 조치');
    expect(items[0]?.textContent).toContain('신고 5번');
    expect(items[0]?.textContent).toContain('관리자 3번');
    expect(items[0]?.textContent).toContain('11월 5일');
    expect(items[0]?.textContent).toContain('14:30');
    expect(items[0]?.textContent).toContain('상태: 검토 중 → 조치 완료');
    expect(items[0]?.textContent).toContain('제재 종류: 7일 정지');
    expect(items[0]?.textContent).toContain('제재 단계: 2단계');
    expect(items[1]?.textContent).toContain('행사 취소');
    expect(items[1]?.textContent).toContain('행사 9번');
    expect(items[1]?.textContent).toContain('취소된 신청: 3건');
  });

  it('신고 대상만 신고 상세로 가는 링크이고, 화면이 없는 대상은 글자만 보인다', async () => {
    respond = () =>
      json(
        200,
        pageOf([
          log({ auditLogId: 2, targetType: 'MEMBER_REPORT', targetId: 5 }),
          log({ auditLogId: 1, action: 'PROGRAM_CREATE', targetType: 'PROGRAM', targetId: 9 }),
        ]),
      );
    await render();

    const links = [...container.querySelectorAll('a')].map((el) => [el.textContent, el.getAttribute('href')]);
    expect(links).toContainEqual(['신고 5번', '/admin/reports/5']);
    expect(links.some(([label]) => label === '행사 9번')).toBe(false);
    expect(text()).toContain('행사 9번');
  });

  it('모르는 detail 키는 키 이름으로, 중첩 값은 한 줄 JSON으로 보인다', async () => {
    respond = () => json(200, pageOf([log({ detail: { brandNew: 'x', nested: { a: [1, 2] } } })]));
    await render();

    expect(text()).toContain('brandNew: x');
    expect(text()).toContain('nested: {"a":[1,2]}');
  });

  it('처음에는 조건 없이 첫 쪽을 요청하고 조건 지우기 버튼이 없다', async () => {
    await render();

    expect(queries).toHaveLength(1);
    expect([...(queries[0] ?? []).keys()].sort()).toEqual(['page', 'size']);
    expect(button('조건 지우기')).toBeUndefined();
  });

  it('조회를 누르면 대상 종류, 관리자 번호, 한국 날짜 경계를 담아 요청한다', async () => {
    await render();

    await type('대상 종류', 'SANCTION');
    await type('관리자 번호', '7');
    await type('시작일', '2026-11-01');
    await type('종료일', '2026-11-05');
    expect(queries).toHaveLength(1);
    await act(async () => button('조회')?.click());

    expect(queries).toHaveLength(2);
    const query = queries[1];
    expect(query?.get('targetType')).toBe('SANCTION');
    expect(query?.get('adminId')).toBe('7');
    expect(query?.get('from')).toBe('2026-10-31T15:00:00.000Z');
    expect(query?.get('to')).toBe('2026-11-05T15:00:00.000Z');
    expect(query?.get('page')).toBe('0');
  });

  it('종료일이 시작일보다 앞서면 안내를 보이고 요청하지 않는다', async () => {
    await render();

    await type('시작일', '2026-11-05');
    await type('종료일', '2026-11-04');
    await act(async () => button('조회')?.click());

    expect(text()).toContain('종료일은 시작일과 같거나 뒤여야 해요.');
    expect(field('종료일').getAttribute('aria-invalid')).toBe('true');
    expect(queries).toHaveLength(1);

    await type('종료일', '2026-11-05');
    expect(text()).not.toContain('종료일은 시작일과 같거나 뒤여야 해요.');
  });

  it('관리자 번호가 양의 정수가 아니면 필드 오류를 보이고 요청하지 않는다', async () => {
    await render();

    await type('관리자 번호', '0');
    await act(async () => button('조회')?.click());

    expect(text()).toContain('관리자 번호는 1 이상의 숫자로 적어 주세요.');
    expect(queries).toHaveLength(1);
  });

  it('조건 지우기를 누르면 입력란을 비우고 조건 없이 다시 요청한다', async () => {
    await render();
    await type('대상 종류', 'MEMORY');
    await type('관리자 번호', '7');
    await act(async () => button('조회')?.click());
    expect(queries).toHaveLength(2);

    await act(async () => button('조건 지우기')?.click());

    expect(queries).toHaveLength(3);
    expect(queries[2]?.has('targetType')).toBe(false);
    expect(queries[2]?.has('adminId')).toBe(false);
    expect(field('대상 종류').value).toBe('');
    expect(field('관리자 번호').value).toBe('');
    expect(button('조건 지우기')).toBeUndefined();
  });

  it('기록이 하나도 없고 조건도 없으면 처음 안내를 보인다', async () => {
    await render();

    expect(text()).toContain('아직 남은 관리자 기록이 없어요');
    expect(text()).not.toContain('조건에 맞는 기록이 없어요');
  });

  it('조건에 맞는 기록이 없으면 조건 지우기로 돌아갈 수 있다', async () => {
    await render();
    await type('관리자 번호', '99');
    await act(async () => button('조회')?.click());

    expect(text()).toContain('조건에 맞는 기록이 없어요');
    expect(buttons('조건 지우기')).toHaveLength(2);

    respond = () => json(200, pageOf([log({})]));
    const inEmptyState = buttons('조건 지우기')[1];
    await act(async () => inEmptyState?.click());

    expect(text()).not.toContain('조건에 맞는 기록이 없어요');
    expect(text()).toContain('신고 조치');
  });

  it('불러오지 못하면 오류를 보이고 다시 불러오기로 이어서 불러온다', async () => {
    respond = () => json(500, { code: 'INTERNAL_ERROR', message: '서버에 문제가 생겼어요.', traceId: 'trace-1' });
    await render();

    const alert = container.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('감사 기록을 불러오지 못했어요');
    expect(alert?.textContent).toContain('trace-1');

    respond = () => json(200, pageOf([log({})]));
    await act(async () => button('다시 불러오기')?.click());

    expect(container.querySelector('[role="alert"]')).toBeNull();
    expect(text()).toContain('신고 조치');
  });

  it('더 보기를 누르면 다음 쪽을 이어 붙인다', async () => {
    respond = (query) =>
      query.get('page') === '0'
        ? json(200, pageOf([log({ auditLogId: 2, action: 'REPORT_DISMISS' })], 0, true))
        : json(200, pageOf([log({ auditLogId: 1, action: 'SANCTION_LIFT', targetType: 'SANCTION' })], 1, false));
    await render();
    expect(text()).toContain('신고 기각');

    await act(async () => button('더 보기')?.click());

    expect(queries.map((query) => query.get('page'))).toEqual(['0', '1']);
    expect(text()).toContain('신고 기각');
    expect(text()).toContain('제재 해제');
    expect(button('더 보기')).toBeUndefined();
  });

  it('관리자가 아니면 안내만 보이고 기록을 요청하지 않는다', async () => {
    me = { ...ME, role: 'USER' };
    await render();

    expect(text()).toContain('관리자만 볼 수 있어요');
    expect(queries).toHaveLength(0);
  });
});
