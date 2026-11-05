import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { listMyProgramApplications } from '../../api/programs';
import type { MyProgramApplicationItem, Page } from '../../api/types';
import { navigate } from '../../app/router';
import { MyProgramApplicationsPage } from './MyProgramApplicationsPage';

vi.mock('../../api/programs', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/programs')>()),
  listMyProgramApplications: vi.fn(),
}));

vi.mock('../../app/router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/router')>()),
  navigate: vi.fn(),
}));

const session = vi.hoisted(() => ({ status: 'authenticated' as 'authenticated' | 'anonymous' | 'loading' }));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => ({
    status: session.status,
    me: session.status === 'authenticated' ? { memberId: 1, nickname: '산사람' } : null,
  }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function application(id: number, overrides: Partial<MyProgramApplicationItem> = {}): MyProgramApplicationItem {
  return {
    applicationId: id,
    status: 'CONFIRMED',
    paymentDueAt: null,
    confirmedAt: '2099-10-01T10:00:00+09:00',
    canceledAt: null,
    cancelReason: null,
    createdAt: '2099-10-01T09:00:00+09:00',
    program: {
      programId: 100 + id,
      title: `행사${id}`,
      locationText: '설악산',
      startAt: '2099-11-01T09:00:00+09:00',
      endAt: '2099-11-02T09:00:00+09:00',
      fee: 0,
      status: 'OPEN',
    },
    ...overrides,
  };
}

function page(items: MyProgramApplicationItem[], pageNumber = 0, hasNext = false): Page<MyProgramApplicationItem> {
  return { content: items, page: pageNumber, size: 20, hasNext };
}

describe('내 행사 신청 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const link = (label: string) => [...container.querySelectorAll('a')].find((element) => element.textContent === label);
  const render = () => act(async () => root.render(<MyProgramApplicationsPage />));

  beforeEach(() => {
    session.status = 'authenticated';
    window.history.replaceState(null, '', '/me/program-applications');
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    vi.mocked(listMyProgramApplications).mockReset();
    vi.mocked(navigate).mockReset();
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('신청한 행사가 상태와 함께 보이고 누르면 행사 상세로 가는 링크다', async () => {
    vi.mocked(listMyProgramApplications).mockResolvedValue(page([application(1), application(2)]));

    await render();

    const link = container.querySelector<HTMLAnchorElement>('a[href="/programs/101"]');
    expect(link?.textContent).toContain('행사1');
    expect(link?.textContent).toContain('확정');
    expect(container.querySelector('a[href="/programs/102"]')?.textContent).toContain('행사2');
    expect(listMyProgramApplications).toHaveBeenCalledWith(undefined, 0, expect.anything());
  });

  it('결제 대기 신청에는 남은 시간이, 취소와 만료 신청에는 그 이유가 보인다', async () => {
    const soon = new Date(Date.now() + 10 * 60 * 1000).toISOString();
    vi.mocked(listMyProgramApplications).mockResolvedValue(
      page([
        application(1, {
          status: 'PENDING_PAYMENT',
          paymentDueAt: soon,
          createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
        }),
        application(2, { status: 'CANCELED', cancelReason: 'USER', canceledAt: '2099-10-02T09:00:00+09:00' }),
        application(3, { status: 'EXPIRED', cancelReason: 'EXPIRED' }),
      ]),
    );

    await render();

    const text = (id: number) => container.querySelector(`a[href="/programs/${100 + id}"]`)?.textContent ?? '';
    expect(text(1)).toContain('결제 대기');
    expect(text(1)).toMatch(/남은 시간\s*\d{2}:\d{2}/);
    expect(text(2)).toContain('취소 사유: 직접 취소');
    expect(text(3)).toContain('결제 기한이 지났어요');
  });

  it('신청한 행사가 없으면 안내와 공식 행사 보기 링크가 나온다', async () => {
    vi.mocked(listMyProgramApplications).mockResolvedValue(page([]));

    await render();

    expect(container.textContent).toContain('신청한 행사가 없어요');
    expect(link('공식 행사 보기')?.getAttribute('href')).toBe('/programs');
  });

  it('상태 탭을 누르면 그 상태의 신청만 다시 불러오고, 결과가 없으면 해당하는 신청이 없다고 알린다', async () => {
    vi.mocked(listMyProgramApplications).mockImplementation(async (status) =>
      status === 'CANCELED' ? page([]) : page([application(1)]),
    );
    await render();
    expect(container.textContent).toContain('행사1');

    await act(async () => button('취소')?.click());

    expect(listMyProgramApplications).toHaveBeenLastCalledWith('CANCELED', 0, expect.anything());
    expect(container.textContent).toContain('해당하는 신청이 없어요');
    expect(container.textContent).not.toContain('행사1');
    expect(link('공식 행사 보기')).toBeUndefined();
  });

  it('더 보기를 누르면 다음 쪽 신청이 아래에 붙고 더 보기 버튼이 사라진다', async () => {
    vi.mocked(listMyProgramApplications).mockImplementation(async (_status, pageNumber) =>
      pageNumber === 0 ? page([application(1)], 0, true) : page([application(2)], 1, false),
    );
    await render();

    await act(async () => button('더 보기')?.click());

    expect(container.textContent).toMatch(/행사1.*행사2/);
    expect(button('더 보기')).toBeUndefined();
  });

  it('불러오지 못하면 오류 문구가 나오고, 다시 불러오기를 누르면 목록이 나온다', async () => {
    vi.mocked(listMyProgramApplications)
      .mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', null))
      .mockResolvedValueOnce(page([application(4)]));

    await render();

    expect(container.textContent).toContain('내 행사 신청을 불러오지 못했어요');
    expect(container.textContent).not.toContain('신청한 행사가 없어요');

    await act(async () => button('다시 불러오기')?.click());

    expect(container.textContent).toContain('행사4');
    expect(container.textContent).not.toContain('내 행사 신청을 불러오지 못했어요');
  });

  it('로그인하지 않았으면 신청 목록을 요청하지 않고 로그인 화면으로 보낸다', async () => {
    session.status = 'anonymous';

    await render();

    expect(listMyProgramApplications).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith('/login?next=%2Fme%2Fprogram-applications', { replace: true });
  });
});
