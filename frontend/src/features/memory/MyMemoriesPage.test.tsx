import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { fetchMyMemories } from '../../api/memories';
import type { MemorySummary } from '../../api/types';
import { useLocation } from '../../app/router';
import { MyMemoriesPage } from './MyMemoriesPage';

vi.mock('../../api/memories', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/memories')>()),
  fetchMyMemories: vi.fn(),
}));

vi.mock('./MemoryMap', () => ({
  MemoryMap: ({ pins }: { pins: unknown[] }) => <div data-testid="memory-map" data-pins={pins.length} />,
}));

vi.mock('../notification/unreadCount', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../notification/unreadCount')>()),
  refreshUnreadCount: vi.fn(),
}));

type TestSession = {
  status: string;
  me: { memberId: number; nickname: string; status: string } | null;
};

const session = vi.hoisted(() => ({ value: null as unknown as TestSession }));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => session.value,
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function summary(id: number, overrides: Partial<MemorySummary> = {}): MemorySummary {
  return {
    memoryId: id,
    lat: 36.35,
    lng: 127.38,
    visitedDate: '2026-10-03',
    nights: 1,
    visibility: 'PUBLIC',
    status: 'ACTIVE',
    imageCount: 0,
    createdAt: '2026-10-04T00:00:00Z',
    ...overrides,
  };
}

describe('내 기록 지도 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const cell = (id: number) => container.querySelector(`ul[aria-label="기록 목록"] a[href="/memories/${id}"]`);

  function Routed() {
    return useLocation().pathname === '/me/memories' ? <MyMemoriesPage /> : null;
  }

  async function open() {
    await act(async () => root.render(<Routed />));
  }

  beforeEach(() => {
    session.value = { status: 'authenticated', me: { memberId: 1, nickname: '산사람', status: 'ACTIVE' } };
    vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    vi.mocked(fetchMyMemories).mockReset();
    window.history.replaceState(null, '', '/me/memories');
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    window.history.replaceState(null, '', '/');
    vi.restoreAllMocks();
  });

  it('남긴 기록을 지도 핀과 목록으로 보여 주고 나만 보기와 검토 중 표시를 붙인다', async () => {
    vi.mocked(fetchMyMemories).mockResolvedValue([
      summary(1, { spot: { spotId: 12, name: '대둔산 박지' }, imageCount: 2 }),
      summary(2, { visibility: 'PRIVATE', nights: 0 }),
      summary(3, { status: 'PENDING_REVIEW' }),
    ]);

    await open();

    expect(container.querySelector('[data-testid="memory-map"]')?.getAttribute('data-pins')).toBe('3');
    expect(cell(1)?.textContent).toContain('대둔산 박지, 10월 3일 (토), 1박, 사진 2장');
    expect(cell(2)?.textContent).toContain('다녀온 곳, 10월 3일 (토), 당일, 나만 보기');
    expect(container.textContent).toContain('나만 보기');
    expect(container.textContent).toContain('검토 중');
    expect(container.querySelector('a[href="/memories"]')?.textContent).toBe('피드');
  });

  it('남긴 기록이 없으면 빈 상태 안내와 지도로 가는 링크를 보여 준다', async () => {
    vi.mocked(fetchMyMemories).mockResolvedValue([]);

    await open();

    expect(container.textContent).toContain('아직 남긴 기록이 없어요');
    expect(container.querySelector('main a[href="/map"]')?.textContent).toBe('지도에서 기록 남기기');
    expect(container.querySelector('[data-testid="memory-map"]')).toBeNull();
  });

  it('불러오지 못하면 오류 문구를 보여 주고 다시 불러오기를 누르면 기록이 나온다', async () => {
    vi.mocked(fetchMyMemories)
      .mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', null))
      .mockResolvedValueOnce([summary(1)]);

    await open();

    expect(container.querySelector('[role="alert"]')?.textContent).toContain('기록을 불러오지 못했어요');
    expect(container.textContent).not.toContain('아직 남긴 기록이 없어요');

    await act(async () => button('다시 불러오기')?.click());

    expect(cell(1)).not.toBeNull();
    expect(container.textContent).not.toContain('기록을 불러오지 못했어요');
  });

  it('로그인하지 않았으면 기록을 불러오지 않고 로그인 화면으로 보낸다', async () => {
    session.value = { status: 'anonymous', me: null };

    await open();

    expect(fetchMyMemories).not.toHaveBeenCalled();
    expect(window.location.pathname).toBe('/login');
    expect(new URLSearchParams(window.location.search).get('next')).toBe('/me/memories');
  });
});
