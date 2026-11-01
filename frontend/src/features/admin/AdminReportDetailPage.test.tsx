import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AdminReportDetail, AdminSanctionHistoryItem } from '../../api/types';
import { SessionProvider } from '../../app/session/session';
import { AdminReportDetailPage } from './AdminReportDetailPage';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const REPORT_ID = 5;
const SANCTION_ID = 31;

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

function sanction(overrides: Partial<AdminSanctionHistoryItem>): AdminSanctionHistoryItem {
  return {
    sanctionId: SANCTION_ID,
    type: 'SUSPEND_30D',
    level: 3,
    status: 'ACTIVE',
    reason: '반복 위반',
    startsAt: '2026-10-01T00:00:00Z',
    endsAt: '2026-10-31T00:00:00Z',
    liftedAt: null,
    ...overrides,
  };
}

function detail(sanctionHistory: AdminSanctionHistoryItem[]): AdminReportDetail {
  return {
    reportId: REPORT_ID,
    kind: 'MEMBER',
    type: 'NO_SHOW',
    urgent: false,
    status: 'ACTIONED',
    reporter: { memberId: 2, nickname: '신고자' },
    target: { memberId: 3, nickname: '대상자' },
    basecampId: 9,
    createdAt: '2026-10-01T00:00:00Z',
    content: '나오지 않았어요',
    resultNote: null,
    handledBy: 1,
    handledAt: '2026-10-02T00:00:00Z',
    basecamp: null,
    companionReview: null,
    sanctionHistory,
  };
}

describe('관리자 신고 상세의 제재 해제', () => {
  let container: HTMLDivElement;
  let root: Root;
  let history: AdminSanctionHistoryItem[];
  let liftBodies: unknown[];

  const text = (element: Element | null) => element?.textContent ?? '';
  const button = (label: string, scope: ParentNode = container) =>
    [...scope.querySelectorAll('button')].find((element) => element.textContent === label);
  const dialog = () => {
    const found = container.querySelector('dialog');
    if (!found) throw new Error('해제 다이얼로그가 없다');
    return found;
  };
  const reasonRadio = (label: string) => {
    const radio = [...dialog().querySelectorAll('label')]
      .find((element) => element.textContent === label)
      ?.querySelector('input');
    if (!radio) throw new Error(`${label} 선택지가 없다`);
    return radio;
  };

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    history = [sanction({})];
    liftBodies = [];
    window.history.replaceState(null, '', `/admin/reports/${REPORT_ID}`);
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.open = true;
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.open = false;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/me') return json(200, ME);
        if (url === `/api/admin/member-reports/${REPORT_ID}`) return json(200, detail(history));
        if (url === `/api/admin/sanctions/${SANCTION_ID}/lift` && init?.method === 'POST') {
          liftBodies.push(JSON.parse(String(init.body)));
          return json(200, { sanctionId: SANCTION_ID, status: 'ENDED_EARLY' });
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

  async function openLiftDialog() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <AdminReportDetailPage reportId={REPORT_ID} />
        </SessionProvider>,
      );
    });
    await act(async () => button('해제')?.click());
  }

  it('SN-15: 해제 사유를 고르기 전에는 해제할 수 없고, 고르면 사유 설명이 보인다', async () => {
    await openLiftDialog();
    const submit = button('해제', dialog());

    expect(submit?.disabled).toBe(true);
    expect(text(dialog())).toContain('해제 사유를 골라 주세요.');

    await act(async () => reasonRadio('일찍 해제').click());

    expect(submit?.disabled).toBe(false);
    expect(text(dialog())).toContain('다음 제재 단계와 신뢰 단계에 반영돼요.');

    await act(async () => reasonRadio('제재 취소').click());

    expect(text(dialog())).toContain('제재 이력에서 빠져요.');
    expect(liftBodies).toEqual([]);
  });

  it('SN-15: 일찍 해제를 고르고 해제하면 reason END_EARLY를 보낸다', async () => {
    await openLiftDialog();

    await act(async () => reasonRadio('일찍 해제').click());
    await act(async () => button('해제', dialog())?.click());

    expect(liftBodies).toEqual([{ reason: 'END_EARLY' }]);
  });

  it('SN-15: 제재 취소를 고르고 해제하면 reason REVOKE를 보낸다', async () => {
    await openLiftDialog();

    await act(async () => reasonRadio('제재 취소').click());
    await act(async () => button('해제', dialog())?.click());

    expect(liftBodies).toEqual([{ reason: 'REVOKE' }]);
  });

  it('SN-15: 해제된 제재는 일찍 해제와 제재 취소로 구분해 보여 주고 다시 해제할 수 없다', async () => {
    history = [
      sanction({ sanctionId: 31, status: 'ENDED_EARLY', liftedAt: '2026-10-05T00:00:00Z' }),
      sanction({ sanctionId: 32, status: 'REVOKED', liftedAt: '2026-10-06T00:00:00Z' }),
    ];
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <AdminReportDetailPage reportId={REPORT_ID} />
        </SessionProvider>,
      );
    });

    expect(text(container)).toContain('일찍 해제');
    expect(text(container)).toContain('제재 취소');
    expect(button('해제')).toBeUndefined();
  });
});
