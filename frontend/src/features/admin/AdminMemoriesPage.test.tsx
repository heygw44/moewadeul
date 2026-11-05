import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AdminMemorySummary } from '../../api/types';
import { SessionProvider } from '../../app/session/session';
import { AdminMemoriesPage } from './AdminMemoriesPage';

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

function memory(): AdminMemorySummary {
  return {
    memoryId: 5,
    author: { memberId: 7, nickname: '작성자A' },
    approxLat: 37.5,
    approxLng: 127.0,
    visitedDate: '2026-10-20',
    nights: 1,
    content: '다녀온 기록',
    images: [],
    status: 'PENDING_REVIEW',
    reportCount: 5,
    reasonCounts: { SPAM: 5, ABUSE: 0, ILLEGAL_CAMPING: 0, PRIVACY: 0, MONEY_SCAM: 0, OTHER: 0 },
    recentReports: [],
    statusChangedAt: '2026-11-01T03:00:00Z',
  };
}

describe('관리자 기록 검토', () => {
  let container: HTMLDivElement;
  let root: Root;
  let listReply: () => Response;
  let hideCalls: number;

  const text = (element: Element | null) => element?.textContent ?? '';
  const button = (label: string, scope: ParentNode = container) =>
    [...scope.querySelectorAll('button')].find((element) => element.textContent === label);

  beforeEach(async () => {
    container = document.createElement('div');
    document.body.appendChild(container);
    hideCalls = 0;
    listReply = () => json(200, { content: [memory()], page: 0, size: 20, hasNext: false });
    window.history.replaceState(null, '', '/admin/memories');
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.open = true;
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.open = false;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        const { pathname } = new URL(url, 'http://localhost');
        if (pathname === '/api/me') return json(200, ME);
        if (init?.method === 'POST' && pathname === '/api/admin/memories/5/hide') {
          hideCalls += 1;
          return json(200, { memoryId: 5, status: 'HIDDEN' });
        }
        if (pathname === '/api/admin/memories') return listReply();
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <AdminMemoriesPage />
        </SessionProvider>,
      );
    });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  it('숨김은 성공했는데 목록을 다시 불러오지 못해도 대화상자가 닫히고 그 카드가 빠진다', async () => {
    listReply = () => json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-reload' });

    await act(async () => button('숨기기')?.click());
    await act(async () => button('숨기기', container.querySelector('dialog') ?? container)?.click());

    expect(hideCalls).toBe(1);
    expect(container.querySelector('dialog')).toBeNull();
    expect(text(container)).not.toContain('작성자A님의 기록');
    expect(text(container.querySelector('[role="alert"]'))).toContain('기록을 불러오지 못했어요');
  });
});
