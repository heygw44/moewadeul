import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { applyProgram, getProgram, payApplication } from '../../api/programs';
import type { ProgramApplyResponse, ProgramDetail, ProgramPayResponse } from '../../api/types';
import { ProgramDetailPage } from './ProgramDetailPage';

vi.mock('../../api/programs', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/programs')>()),
  getProgram: vi.fn(),
  applyProgram: vi.fn(),
  payApplication: vi.fn(),
}));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => ({ status: 'authenticated', me: { memberId: 1, nickname: '산사람' } }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const DETAIL: ProgramDetail = {
  programId: 3,
  title: '가을 백패킹 입문',
  locationText: '설악산',
  spotId: null,
  startAt: '2099-11-01T09:00:00+09:00',
  endAt: '2099-11-02T09:00:00+09:00',
  applyOpenAt: '2026-10-01T09:00:00+09:00',
  applyCloseAt: '2099-10-30T09:00:00+09:00',
  capacity: 10,
  remainingSeats: 4,
  fee: 0,
  overnight: true,
  status: 'OPEN',
  description: '',
  paymentDeadlineMinutes: 30,
};

const APPLIED: ProgramApplyResponse = {
  applicationId: 9,
  status: 'PENDING_PAYMENT',
  paymentDueAt: '2099-10-01T00:30:00Z',
  amount: 0,
};

describe('행사 신청의 멱등 키', () => {
  let container: HTMLDivElement;
  let root: Root;

  const keys = () => vi.mocked(applyProgram).mock.calls.map((call) => call[1]);
  const applyButton = () =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === '신청하기');

  async function clickApply() {
    await act(async () => applyButton()?.click());
  }

  beforeEach(async () => {
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    vi.mocked(getProgram).mockResolvedValue(DETAIL);
    await act(async () => root.render(<ProgramDetailPage programId={3} />));
  });

  afterEach(async () => {
    vi.mocked(applyProgram).mockReset();
    await act(async () => root.unmount());
    container.remove();
  });

  it('응답을 받지 못한 신청을 다시 보내면 같은 키를 쓰고, 성공한 뒤 새로 신청하면 새 키를 쓴다', async () => {
    vi.mocked(applyProgram)
      .mockRejectedValueOnce(new ApiError(0, 'NETWORK_ERROR', null))
      .mockResolvedValueOnce(APPLIED)
      .mockResolvedValueOnce(APPLIED);

    await clickApply();
    await clickApply();
    await clickApply();

    const [first, second, third] = keys();
    expect(second).toBe(first);
    expect(third).not.toBe(first);
  });

  it('서버가 답을 준 실패 뒤에는 새 키로 다시 보낸다', async () => {
    vi.mocked(applyProgram)
      .mockRejectedValueOnce(new ApiError(409, 'PROGRAM_SOLD_OUT', null))
      .mockResolvedValueOnce(APPLIED);

    await clickApply();
    await clickApply();

    const [first, second] = keys();
    expect(second).not.toBe(first);
  });

  it('결제도 응답을 받지 못했을 때만 같은 키로 다시 보낸다', async () => {
    vi.mocked(getProgram).mockResolvedValue({
      ...DETAIL,
      myApplication: { applicationId: 9, status: 'PENDING_PAYMENT', paymentDueAt: '2099-10-01T00:30:00Z' },
    });
    await act(async () => root.render(<ProgramDetailPage programId={4} />));
    const paid = { applicationId: 9, status: 'CONFIRMED', paidAt: '2099-10-01T00:10:00Z' } as ProgramPayResponse;
    vi.mocked(payApplication)
      .mockRejectedValueOnce(new ApiError(0, 'REQUEST_TIMEOUT', null))
      .mockRejectedValueOnce(new ApiError(409, 'PROGRAM_INVALID_STATE', null))
      .mockResolvedValueOnce(paid);
    const payButton = () =>
      [...container.querySelectorAll('button')].find((element) => element.textContent === '결제하기');

    await act(async () => payButton()?.click());
    await act(async () => payButton()?.click());
    await act(async () => payButton()?.click());

    const [first, second, third] = vi.mocked(payApplication).mock.calls.map((call) => call[1]);
    expect(second).toBe(first);
    expect(third).not.toBe(second);
  });
});
