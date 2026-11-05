import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { listPrograms } from '../../api/programs';
import type { ProgramPage, ProgramSummary } from '../../api/types';
import { ProgramListPage } from './ProgramListPage';

vi.mock('../../api/programs', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/programs')>()),
  listPrograms: vi.fn(),
}));

vi.mock('./AnnouncementBar', () => ({ AnnouncementBar: () => null }));

const session = vi.hoisted(() => ({ signedIn: true }));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () =>
    session.signedIn
      ? { status: 'authenticated', me: { memberId: 1, nickname: '산사람' } }
      : { status: 'anonymous', me: null },
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function program(id: number, overrides: Partial<ProgramSummary> = {}): ProgramSummary {
  return {
    programId: id,
    title: `행사${id}`,
    locationText: '설악산',
    spotId: null,
    startAt: '2099-11-01T09:00:00+09:00',
    endAt: '2099-11-02T09:00:00+09:00',
    applyOpenAt: '2099-10-01T09:00:00+09:00',
    applyCloseAt: '2099-10-30T09:00:00+09:00',
    capacity: 10,
    remainingSeats: 4,
    fee: 0,
    overnight: true,
    status: 'OPEN',
    ...overrides,
  };
}

function page(items: ProgramSummary[], pageNumber = 0, hasNext = false): ProgramPage {
  return { content: items, page: pageNumber, size: 20, hasNext };
}

describe('공식 행사 목록 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const render = () => act(async () => root.render(<ProgramListPage />));

  beforeEach(() => {
    session.signedIn = true;
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    vi.mocked(listPrograms).mockReset();
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('불러온 행사의 이름, 상태, 숙박 여부, 남은 자리, 참가비가 보이고 누르면 행사 상세로 가는 링크다', async () => {
    vi.mocked(listPrograms).mockResolvedValue(
      page([program(3, { title: '가을 백패킹 입문', remainingSeats: 4, capacity: 10, fee: 15000 })]),
    );

    await render();

    const link = container.querySelector<HTMLAnchorElement>('a[href="/programs/3"]');
    expect(link?.textContent).toContain('가을 백패킹 입문');
    expect(link?.textContent).toContain('신청 받는 중');
    expect(link?.textContent).toContain('숙박');
    expect(link?.textContent).toContain('설악산');
    expect(link?.textContent).toContain('4/10');
    expect(link?.textContent).toContain('15,000원');
    expect(listPrograms).toHaveBeenCalledWith(undefined, 0, expect.anything());
  });

  it('참가비가 0원인 행사는 무료로 보인다', async () => {
    vi.mocked(listPrograms).mockResolvedValue(page([program(1, { fee: 0 })]));

    await render();

    expect(container.textContent).toContain('무료');
  });

  it('행사가 하나도 없으면 안내 문구가 나오고 전체 보기 버튼은 없다', async () => {
    vi.mocked(listPrograms).mockResolvedValue(page([]));

    await render();

    expect(container.textContent).toContain('지금 볼 수 있는 행사가 없어요');
    expect(button('전체 보기')).toBeUndefined();
  });

  it('신청 상태 탭을 누르면 그 상태의 행사만 다시 불러오고, 결과가 없으면 전체 보기로 돌아갈 수 있다', async () => {
    vi.mocked(listPrograms).mockImplementation(async (status) =>
      status === 'CLOSED' ? page([]) : page([program(1), program(2)]),
    );
    await render();
    expect(container.textContent).toMatch(/행사1.*행사2/);

    await act(async () => button('마감')?.click());

    expect(listPrograms).toHaveBeenLastCalledWith('CLOSED', 0, expect.anything());
    expect(container.textContent).toContain('해당하는 행사가 없어요');
    expect(container.textContent).not.toContain('행사1');

    await act(async () => button('전체 보기')?.click());

    expect(container.textContent).toMatch(/행사1.*행사2/);
  });

  it('더 보기를 누르면 다음 쪽 행사가 아래에 붙고 더 보기 버튼이 사라진다', async () => {
    vi.mocked(listPrograms).mockImplementation(async (_status, pageNumber) =>
      pageNumber === 0 ? page([program(1)], 0, true) : page([program(2)], 1, false),
    );
    await render();
    expect(container.textContent).not.toContain('행사2');

    await act(async () => button('더 보기')?.click());

    expect(container.textContent).toMatch(/행사1.*행사2/);
    expect(button('더 보기')).toBeUndefined();
  });

  it('불러오지 못하면 오류 문구와 다시 불러오기 버튼이 나오고, 누르면 목록이 나온다', async () => {
    vi.mocked(listPrograms)
      .mockRejectedValueOnce(
        new ApiError(500, 'INTERNAL_ERROR', { code: 'INTERNAL_ERROR', message: '', traceId: 'trace-77' }),
      )
      .mockResolvedValueOnce(page([program(5)]));

    await render();

    expect(container.textContent).toContain('행사를 불러오지 못했어요');
    expect(container.textContent).toContain('일시적인 문제가 생겼어요');
    expect(container.textContent).toContain('trace-77');
    expect(container.textContent).not.toContain('지금 볼 수 있는 행사가 없어요');

    await act(async () => button('다시 불러오기')?.click());

    expect(container.textContent).toContain('행사5');
    expect(container.textContent).not.toContain('행사를 불러오지 못했어요');
  });

  it('로그인한 회원에게만 내 행사 신청 링크가 보인다', async () => {
    vi.mocked(listPrograms).mockResolvedValue(page([]));
    await render();
    expect(container.querySelector('a[href="/me/program-applications"]')?.textContent).toBe('내 행사 신청');

    session.signedIn = false;
    await act(async () => root.render(<ProgramListPage key="anonymous" />));

    expect(container.querySelector('a[href="/me/program-applications"]')).toBeNull();
  });
});
