import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { approveApplication, fetchBasecampApplications, fetchBasecampDetail } from '../../api/basecamps';
import type { BasecampApplicationItem, BasecampDetail, Page } from '../../api/types';
import { BasecampManagePage } from './BasecampManagePage';

vi.mock('../../api/basecamps', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/basecamps')>()),
  fetchBasecampDetail: vi.fn(),
  fetchBasecampApplications: vi.fn(),
  approveApplication: vi.fn(),
}));

vi.mock('../member/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../member/session')>()),
  useSession: () => ({ status: 'authenticated', me: { memberId: 1, nickname: '리더' } }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const DETAIL = {
  basecampId: 7,
  title: '설악산 야영',
  description: '',
  startDate: '2026-11-01',
  endDate: '2026-11-02',
  capacity: 6,
  headcount: 2,
  status: 'RECRUITING',
  joinCondition: {},
  leader: { memberId: 1, nickname: '리더' },
  members: [],
  myRelation: 'LEADER',
  safetyNotice: '',
} as unknown as BasecampDetail;

function application(id: number): BasecampApplicationItem {
  return {
    applicationId: id,
    status: 'PENDING',
    message: null,
    appliedAt: '2026-10-01T00:00:00Z',
    applicant: { memberId: 100 + id, nickname: `신청자${id}` },
  };
}

function page(ids: number[], pageNumber: number, hasNext: boolean): Page<BasecampApplicationItem> {
  return { content: ids.map(application), page: pageNumber, size: 20, hasNext } as Page<BasecampApplicationItem>;
}

type ApplicationsCall = { page: number; resolve: (value: Page<BasecampApplicationItem>) => void };

describe('베이스캠프 관리 화면의 신청 목록', () => {
  let container: HTMLDivElement;
  let root: Root;
  let calls: ApplicationsCall[];

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);

  beforeEach(() => {
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    calls = [];
    vi.mocked(fetchBasecampDetail).mockResolvedValue(DETAIL);
    vi.mocked(approveApplication).mockResolvedValue({ headcount: 3, status: 'RECRUITING' });
    vi.mocked(fetchBasecampApplications).mockImplementation(
      (_id, pageNumber) => new Promise((resolve) => calls.push({ page: pageNumber, resolve })),
    );
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('새로고침 중에 늦게 도착한 더 보기 응답은 새 목록에 붙이지 않는다', async () => {
    await act(async () => root.render(<BasecampManagePage basecampId={7} />));
    await act(async () => calls[0]?.resolve(page([1, 2], 0, true)));
    await act(async () => button('더 보기')?.click());
    expect(calls.map((call) => call.page)).toEqual([0, 1]);

    await act(async () => button('승인')?.click());
    expect(calls.map((call) => call.page)).toEqual([0, 1, 0]);
    await act(async () => calls[2]?.resolve(page([2], 0, false)));
    await act(async () => calls[1]?.resolve(page([3], 1, false)));

    const text = container.textContent ?? '';
    expect(text).toContain('신청자2');
    expect(text).not.toContain('신청자3');
  });
});
