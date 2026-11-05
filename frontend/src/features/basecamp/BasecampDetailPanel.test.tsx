import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SessionProvider } from '../../app/session/session';
import { notifySignedOut } from '../../app/session/signedOut';
import { BasecampDetailPanel } from './BasecampDetailPanel';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const SAFETY_NOTICE = '출발 전 일정을 가족이나 지인과 공유하세요. 플랫폼 밖 송금 요구는 신고해 주세요.';

function detail(status: string) {
  return {
    basecampId: 7,
    title: '가을 능선',
    description: '소개',
    startDate: '2026-11-01',
    endDate: '2026-11-02',
    capacity: 4,
    headcount: 2,
    status,
    joinCondition: {},
    leader: { memberId: 1, nickname: '리더' },
    members: [{ memberId: 1, nickname: '리더', role: 'LEADER' }],
    myRelation: 'NONE',
    safetyNotice: SAFETY_NOTICE,
  };
}

describe('베이스캠프 상세의 안전 안내', () => {
  let container: HTMLDivElement;
  let root: Root;

  async function mount(status: string) {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url === '/api/basecamps/7'
          ? new Response(JSON.stringify(detail(status)), {
              status: 200,
              headers: { 'Content-Type': 'application/json' },
            })
          : new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED', message: 'x', traceId: 't' }), {
              status: 401,
              headers: { 'Content-Type': 'application/json' },
            }),
      ),
    );
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <BasecampDetailPanel basecampId={7} onBack={() => undefined} />
        </SessionProvider>,
      );
    });
  }

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    notifySignedOut();
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  it.each(['RECRUITING', 'CLOSED', 'CONFIRMED', 'COMPLETED', 'CANCELED'])(
    '[SF-01] %s 상태에서도 플랫폼 밖 송금 요구를 신고하라는 안내가 보인다',
    async (status) => {
      await mount(status);

      expect(container.textContent).toContain('플랫폼 밖 송금 요구는 신고해 주세요.');
    },
  );

  it('[F-14][SF-04] 확정 베이스캠프에는 안전 수칙 세 가지가 보인다', async () => {
    await mount('CONFIRMED');

    expect(container.textContent).toContain('출발 전 안전 수칙');
    expect(container.textContent).toContain('출발 전에 일정을 가족이나 지인에게 알려 주세요.');
    expect(container.textContent).toContain('되도록 일행과 떨어지지 마세요.');
    expect(container.textContent).toContain('야영한 자리에는 흔적을 남기지 마세요.');
  });

  it('[SF-04] 모집 중에는 확정 안전 수칙을 보여 주지 않는다', async () => {
    await mount('RECRUITING');

    expect(container.textContent).not.toContain('출발 전 안전 수칙');
  });
});
