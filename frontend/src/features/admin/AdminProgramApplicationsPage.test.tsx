import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AdminProgramApplicant, ProgramDetail } from '../../api/types';
import { SessionProvider } from '../../app/session/session';
import { AdminProgramApplicationsPage } from './AdminProgramApplicationsPage';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const PROGRAM_ID = 7;

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

function programDetail(overrides: Partial<ProgramDetail> = {}): ProgramDetail {
  return {
    programId: PROGRAM_ID,
    title: '가을 백패킹 데이',
    description: '함께 걷는 행사예요.',
    locationText: '북한산 백운대 입구',
    spotId: null,
    startAt: '2026-11-21T01:00:00Z',
    endAt: '2026-11-22T03:00:00Z',
    applyOpenAt: '2026-11-10T01:00:00Z',
    applyCloseAt: '2026-11-20T09:00:00Z',
    capacity: 200,
    remainingSeats: 150,
    fee: 30000,
    paymentDeadlineMinutes: 15,
    overnight: true,
    status: 'OPEN',
    ...overrides,
  };
}

function applicant(overrides: Partial<AdminProgramApplicant> = {}): AdminProgramApplicant {
  return {
    applicationId: 101,
    member: { memberId: 21, nickname: '산사람' },
    status: 'PENDING_PAYMENT',
    paymentDueAt: '2026-11-12T01:15:00Z',
    confirmedAt: null,
    canceledAt: null,
    cancelReason: null,
    reviewRequestedAt: null,
    createdAt: '2026-11-12T01:00:00Z',
    ...overrides,
  };
}

type Call = { method: string; url: string };

describe('관리자 행사 신청자 목록', () => {
  let container: HTMLDivElement;
  let root: Root;
  let me: typeof ME;
  let calls: Call[];
  let applicants: AdminProgramApplicant[];
  let hasNext: boolean;
  let listStatus: number;
  let programReply: () => Response;

  const text = (element: Element | null) => element?.textContent ?? '';
  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const link = (label: string) => [...container.querySelectorAll('a')].find((element) => element.textContent === label);
  const rows = () => [...container.querySelectorAll('ul[aria-label="신청자 목록"] > li')];
  const applicantUrls = () => calls.filter((call) => call.url.includes('/applications')).map((call) => call.url);

  async function mount() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <AdminProgramApplicationsPage programId={PROGRAM_ID} />
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
    applicants = [applicant()];
    hasNext = false;
    listStatus = 200;
    programReply = () => json(200, programDetail());
    window.history.replaceState(null, '', `/admin/programs/${PROGRAM_ID}/applications`);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/me') return json(200, me);
        calls.push({ method: init?.method ?? 'GET', url });
        if (url === `/api/programs/${PROGRAM_ID}`) return programReply();
        if (url.startsWith(`/api/admin/programs/${PROGRAM_ID}/applications?`)) {
          if (listStatus !== 200) {
            return json(listStatus, { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-3' });
          }
          return json(200, { content: applicants, page: 0, size: 20, hasNext });
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

  it('위쪽에 행사 제목과 행사 목록으로 가는 링크를 보여 준다', async () => {
    await mount();

    expect(text(container.querySelector('h2'))).toBe('가을 백패킹 데이');
    expect(text(container.querySelector('h2')?.parentElement ?? null)).toContain('신청 받는 중');
    expect(link('행사 목록으로')?.getAttribute('href')).toBe('/admin/programs');
  });

  it('신청자마다 닉네임, 상태, 신청 시각, 결제 기한을 보여 준다', async () => {
    await mount();

    expect(rows()).toHaveLength(1);
    expect(text(rows()[0] ?? null)).toContain('산사람');
    expect(text(rows()[0] ?? null)).toContain('결제 대기');
    expect(text(rows()[0] ?? null)).toContain('신청 11월 12일 (목) 10:00');
    expect(text(rows()[0] ?? null)).toContain('결제 기한 11월 12일 (목) 10:15');
    expect(text(rows()[0] ?? null)).not.toContain('확정 ');
    expect(text(rows()[0] ?? null)).not.toContain('취소 사유');
    expect(applicantUrls()).toEqual([`/api/admin/programs/${PROGRAM_ID}/applications?page=0&size=20`]);
  });

  it('확정된 신청은 확정 시각을, 취소된 신청은 취소 시각과 사유를 보여 준다', async () => {
    applicants = [
      applicant({
        applicationId: 102,
        member: { memberId: 22, nickname: '확정한사람' },
        status: 'CONFIRMED',
        confirmedAt: '2026-11-12T01:05:00Z',
      }),
      applicant({
        applicationId: 103,
        member: { memberId: 23, nickname: '취소한사람' },
        status: 'CANCELED',
        paymentDueAt: null,
        canceledAt: '2026-11-13T02:00:00Z',
        cancelReason: 'PROGRAM_CANCELED',
      }),
    ];

    await mount();

    expect(text(rows()[0] ?? null)).toContain('확정 11월 12일 (목) 10:05');
    expect(text(rows()[1] ?? null)).toContain('취소 11월 13일 (금) 11:00');
    expect(text(rows()[1] ?? null)).toContain('취소 사유 행사 취소');
    expect(text(rows()[1] ?? null)).not.toContain('결제 기한');
  });

  it('취소 사유를 쉬운 말로 바꿔 보여 준다', async () => {
    applicants = [
      applicant({ applicationId: 1, status: 'CANCELED', cancelReason: 'USER', canceledAt: '2026-11-13T02:00:00Z' }),
      applicant({ applicationId: 2, status: 'EXPIRED', cancelReason: 'EXPIRED', canceledAt: '2026-11-13T02:00:00Z' }),
      applicant({
        applicationId: 3,
        status: 'CANCELED',
        cancelReason: 'SANCTIONED',
        canceledAt: '2026-11-13T02:00:00Z',
      }),
      applicant({
        applicationId: 4,
        status: 'CANCELED',
        cancelReason: 'WITHDRAWN',
        canceledAt: '2026-11-13T02:00:00Z',
      }),
    ];

    await mount();

    expect(rows().map((row) => /취소 사유 (.+)$/.exec(text(row))?.[1])).toEqual([
      '직접 취소',
      '결제 기한 만료',
      '이용 정지',
      '회원 탈퇴',
    ]);
  });

  it('이용 정지 회원의 신청이면 확인이 필요하다고 알린다', async () => {
    applicants = [
      applicant({
        applicationId: 1,
        member: { memberId: 1, nickname: '확인필요' },
        reviewRequestedAt: '2026-11-14T00:00:00Z',
      }),
      applicant({ applicationId: 2, member: { memberId: 2, nickname: '평범함' } }),
    ];

    await mount();

    expect(text(rows()[0] ?? null)).toContain('이용 정지 회원의 신청이라 확인이 필요해요');
    expect(text(rows()[1] ?? null)).not.toContain('확인이 필요해요');
  });

  it('이메일은 보여 주지 않는다', async () => {
    await mount();

    expect(text(container.querySelector('ul[aria-label="신청자 목록"]'))).not.toContain('@');
  });

  it('상태 구분을 바꾸면 status 쿼리로 처음부터 다시 불러온다', async () => {
    await mount();

    await click(button('확정'));
    await click(button('취소'));
    await click(button('전체'));

    expect(applicantUrls()).toEqual([
      `/api/admin/programs/${PROGRAM_ID}/applications?page=0&size=20`,
      `/api/admin/programs/${PROGRAM_ID}/applications?status=CONFIRMED&page=0&size=20`,
      `/api/admin/programs/${PROGRAM_ID}/applications?status=CANCELED&page=0&size=20`,
      `/api/admin/programs/${PROGRAM_ID}/applications?page=0&size=20`,
    ]);
  });

  it('상태 구분마다 다른 빈 상태 문구를 보여 준다', async () => {
    applicants = [];
    await mount();
    expect(text(container)).toContain('아직 신청한 회원이 없어요');

    await click(button('결제 대기'));
    expect(text(container)).toContain('결제를 기다리는 신청이 없어요');

    await click(button('확정'));
    expect(text(container)).toContain('확정된 신청이 없어요');

    await click(button('취소'));
    expect(text(container)).toContain('취소된 신청이 없어요');

    await click(button('만료'));
    expect(text(container)).toContain('만료된 신청이 없어요');
  });

  it('더 보기를 누르면 다음 쪽을 이어 붙인다', async () => {
    hasNext = true;
    await mount();

    applicants = [applicant({ applicationId: 102, member: { memberId: 22, nickname: '다음사람' } })];
    hasNext = false;
    await click(button('더 보기'));

    expect(applicantUrls()).toEqual([
      `/api/admin/programs/${PROGRAM_ID}/applications?page=0&size=20`,
      `/api/admin/programs/${PROGRAM_ID}/applications?page=1&size=20`,
    ]);
    expect(rows().map((row) => text(row))).toEqual([
      expect.stringContaining('산사람'),
      expect.stringContaining('다음사람'),
    ]);
    expect(button('더 보기')).toBeUndefined();
  });

  it('신청자를 불러오지 못하면 오류와 다시 불러오기를 보여 주고, 누르면 다시 불러온다', async () => {
    listStatus = 500;
    await mount();

    expect(text(container.querySelector('[role="alert"]'))).toContain('신청자를 불러오지 못했어요');
    expect(text(container.querySelector('[role="alert"]'))).toContain('trace-3');

    listStatus = 200;
    await click(button('다시 불러오기'));

    expect(rows()).toHaveLength(1);
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });

  it('행사 제목을 불러오지 못해도 신청자 목록은 보인다', async () => {
    programReply = () => json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 't' });

    await mount();

    expect(text(container.querySelector('h2'))).toBe('행사 7번');
    expect(rows()).toHaveLength(1);
  });

  it('취소된 행사는 취소됐다고 표시한다', async () => {
    programReply = () => json(200, programDetail({ status: 'CANCELED' }));

    await mount();

    expect(text(container.querySelector('h2')?.parentElement ?? null)).toContain('행사 취소');
  });

  it('관리자가 아니면 신청자를 불러오지 않고 안내만 보여 준다', async () => {
    me = { ...ME, role: 'USER' };

    await mount();

    expect(text(container)).toContain('관리자만 볼 수 있어요');
    expect(calls).toEqual([]);
  });
});
