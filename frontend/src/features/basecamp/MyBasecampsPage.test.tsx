import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchMyBasecamps } from '../../api/basecamps';
import { ApiError } from '../../api/client';
import type { MyBasecampItem, MyBasecampRelation, Page } from '../../api/types';
import { navigate } from '../../app/router';
import { MyBasecampsPage } from './MyBasecampsPage';

vi.mock('../../api/basecamps', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/basecamps')>()),
  fetchMyBasecamps: vi.fn(),
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

function basecamp(id: number, relation: MyBasecampRelation, overrides: Partial<MyBasecampItem> = {}): MyBasecampItem {
  return {
    basecampId: id,
    title: `캠프${id}`,
    spot: { spotId: 7, name: '선자령', type: 'CAMPING', lat: 37.7, lng: 128.7 },
    startDate: '2099-11-01',
    endDate: '2099-11-02',
    capacity: 6,
    headcount: 3,
    status: 'RECRUITING',
    myRelation: relation,
    ...overrides,
  } as MyBasecampItem;
}

function page(items: MyBasecampItem[], pageNumber = 0, hasNext = false): Page<MyBasecampItem> {
  return { content: items, page: pageNumber, size: 20, hasNext };
}

describe('내 베이스캠프 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const link = (label: string) => [...container.querySelectorAll('a')].find((element) => element.textContent === label);
  const render = () => act(async () => root.render(<MyBasecampsPage />));

  beforeEach(() => {
    session.status = 'authenticated';
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    vi.mocked(fetchMyBasecamps).mockReset();
    vi.mocked(navigate).mockReset();
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('내 베이스캠프가 상태, 내 역할, 장소, 인원과 함께 보이고 누르면 상세로 가는 링크다', async () => {
    vi.mocked(fetchMyBasecamps).mockResolvedValue(
      page([basecamp(1, 'LEADER'), basecamp(2, 'APPLICANT', { title: '북한산 일출', status: 'CLOSED', headcount: 6 })]),
    );

    await render();

    const first = container.querySelector<HTMLAnchorElement>('a[href="/basecamps/1"]');
    expect(first?.textContent).toContain('캠프1');
    expect(first?.textContent).toContain('모집 중');
    expect(first?.textContent).toContain('캠프 리더');
    expect(first?.textContent).toContain('선자령');
    expect(first?.textContent).toContain('3/6명');
    const second = container.querySelector('a[href="/basecamps/2"]');
    expect(second?.textContent).toContain('북한산 일출');
    expect(second?.textContent).toContain('모집 마감');
    expect(second?.textContent).toContain('승인 대기');
    expect(second?.textContent).toContain('6/6명');
    expect(fetchMyBasecamps).toHaveBeenCalledWith({ relation: undefined, page: 0 }, expect.anything());
  });

  it('베이스캠프가 없으면 안내와 베이스캠프 찾기 링크가 나온다', async () => {
    vi.mocked(fetchMyBasecamps).mockResolvedValue(page([]));

    await render();

    expect(container.textContent).toContain('열었거나 합류한 베이스캠프가 없어요');
    expect(link('베이스캠프 찾기')?.getAttribute('href')).toBe('/basecamps');
  });

  it('역할 탭을 누르면 그 역할로 다시 불러오고 카드에는 역할을 다시 적지 않으며, 결과가 없으면 해당하는 베이스캠프가 없다고 알린다', async () => {
    vi.mocked(fetchMyBasecamps).mockImplementation(async ({ relation }) =>
      relation === 'MEMBER' ? page([]) : page([basecamp(1, 'LEADER')]),
    );
    await render();
    expect(container.querySelector('a[href="/basecamps/1"]')?.textContent).toContain('캠프 리더');

    await act(async () => button('캠프 리더')?.click());

    expect(fetchMyBasecamps).toHaveBeenLastCalledWith({ relation: 'LEADER', page: 0 }, expect.anything());
    expect(container.querySelector('a[href="/basecamps/1"]')?.textContent).not.toContain('캠프 리더');

    await act(async () => button('멤버')?.click());

    expect(container.textContent).toContain('해당하는 베이스캠프가 없어요');
    expect(link('베이스캠프 찾기')).toBeUndefined();
  });

  it('더 보기를 누르면 다음 쪽이 아래에 붙고 더 보기 버튼이 사라진다', async () => {
    vi.mocked(fetchMyBasecamps).mockImplementation(async ({ page: pageNumber }) =>
      pageNumber === 0 ? page([basecamp(1, 'LEADER')], 0, true) : page([basecamp(2, 'MEMBER')], 1, false),
    );
    await render();

    await act(async () => button('더 보기')?.click());

    expect(container.textContent).toMatch(/캠프1.*캠프2/);
    expect(button('더 보기')).toBeUndefined();
  });

  it('같은 베이스캠프에 리더이면서 멤버로 두 번 나와도 둘 다 보인다', async () => {
    vi.mocked(fetchMyBasecamps).mockResolvedValue(page([basecamp(1, 'LEADER'), basecamp(1, 'MEMBER')]));

    await render();

    expect(container.querySelectorAll('a[href="/basecamps/1"]')).toHaveLength(2);
  });

  it('불러오지 못하면 오류 문구가 나오고, 다시 불러오기를 누르면 목록이 나온다', async () => {
    vi.mocked(fetchMyBasecamps)
      .mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', null))
      .mockResolvedValueOnce(page([basecamp(9, 'MEMBER')]));

    await render();

    expect(container.textContent).toContain('내 베이스캠프를 불러오지 못했어요');
    expect(container.textContent).not.toContain('열었거나 합류한 베이스캠프가 없어요');

    await act(async () => button('다시 불러오기')?.click());

    expect(container.textContent).toContain('캠프9');
    expect(container.textContent).not.toContain('내 베이스캠프를 불러오지 못했어요');
  });

  it('로그인하지 않았으면 목록을 요청하지 않고 로그인 화면으로 보낸다', async () => {
    session.status = 'anonymous';

    await render();

    expect(fetchMyBasecamps).not.toHaveBeenCalled();
    expect(vi.mocked(navigate).mock.calls[0]?.[0]).toMatch(/^\/login\?next=/);
  });
});
