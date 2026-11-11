import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CursorPage, Page, PageRequest } from '../api/types';
import { usePagedList } from './usePagedList';
import type { PagedList } from './usePagedList';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

type Result = PagedList<number, string>;

function page(content: number[], pageNumber: number, hasNext: boolean): Page<number> {
  return { content, page: pageNumber, size: content.length, hasNext } as Page<number>;
}

type Deferred = { resolve: (value: Page<number> | CursorPage<number>) => void; reject: (reason: unknown) => void };

describe('usePagedList', () => {
  let container: HTMLDivElement;
  let root: Root;
  let latest: Result;
  let pending: Map<string, Deferred[]>;
  let fetcher: (filter: string, request: PageRequest) => Promise<Page<number> | CursorPage<number>>;
  let keyOf: (item: number) => number | null;

  function Probe({ filter }: { filter: string }) {
    latest = usePagedList(true, filter, fetcher, keyOf);
    return null;
  }

  async function settle(key: string, value: Page<number> | CursorPage<number>) {
    const next = pending.get(key)?.shift();
    if (!next) throw new Error(`대기 중인 요청이 없어요: ${key}`);
    await act(async () => next.resolve(value));
  }

  async function fail(key: string) {
    const next = pending.get(key)?.shift();
    if (!next) throw new Error(`대기 중인 요청이 없어요: ${key}`);
    await act(async () => next.reject(new Error('네트워크 오류')));
  }

  beforeEach(() => {
    container = document.createElement('div');
    root = createRoot(container);
    pending = new Map();
    keyOf = (item) => item;
    fetcher = vi.fn(
      (filter: string, request: PageRequest) =>
        new Promise<Page<number> | CursorPage<number>>((resolve, reject) => {
          const key = `${filter}:${request.page}`;
          pending.set(key, [...(pending.get(key) ?? []), { resolve, reject }]);
        }),
    );
  });

  afterEach(() => act(() => root.unmount()));

  async function mount(filter: string) {
    await act(async () => root.render(<Probe filter={filter} />));
  }

  it('reload가 첫 쪽을 다시 받는 동안 끝난 이전 loadMore 응답은 새 목록에 붙이지 않는다', async () => {
    await mount('A');
    await settle('A:0', page([1, 2], 0, true));
    await act(async () => void latest.loadMore());

    await act(async () => latest.reload());
    await settle('A:0', page([2, 3], 0, true));
    await settle('A:1', page([3, 4], 1, false));

    expect(latest.current?.items).toEqual([2, 3]);
    expect(latest.current?.page).toBe(0);
  });

  it('reload가 응답을 기다리는 사이에 시작한 loadMore 응답도 버린다', async () => {
    await mount('A');
    await settle('A:0', page([1, 2], 0, true));

    await act(async () => latest.reload());
    await act(async () => void latest.loadMore());
    await settle('A:0', page([2, 3], 0, true));
    await settle('A:1', page([9], 1, false));

    expect(latest.current?.items).toEqual([2, 3]);
  });

  it('reload는 응답이 오기 전까지 이전 목록을 그대로 둔다', async () => {
    await mount('A');
    await settle('A:0', page([1, 2], 0, false));

    await act(async () => latest.reload());

    expect(latest.current?.items).toEqual([1, 2]);
  });

  it('필터가 바뀐 뒤에 도착한 이전 필터의 loadMore 응답은 새 목록에 붙이지 않는다', async () => {
    await mount('A');
    await settle('A:0', page([1], 0, true));
    await act(async () => void latest.loadMore());

    await mount('B');
    await settle('B:0', page([7], 0, true));
    await settle('A:1', page([2], 1, false));

    expect(latest.current?.items).toEqual([7]);
    expect(latest.current?.hasNext).toBe(true);
  });

  it('받아 둔 목록이 있으면 reload가 실패해도 목록은 그대로 두고 오류만 알린다', async () => {
    await mount('A');
    await settle('A:0', page([1, 2], 0, true));

    await act(async () => latest.reload());
    await fail('A:0');

    expect(latest.current?.items).toEqual([1, 2]);
    expect(latest.current?.hasNext).toBe(true);
    expect(latest.current?.error).not.toBeNull();
  });

  it('reload 실패 뒤 retry는 목록을 비우지 않고 첫 쪽을 다시 받는다', async () => {
    await mount('A');
    await settle('A:0', page([1, 2], 0, false));
    await act(async () => latest.reload());
    await fail('A:0');

    await act(async () => latest.retry());

    expect(latest.current?.items).toEqual([1, 2]);
    expect(latest.current?.error).toBeNull();
    await settle('A:0', page([5], 0, false));
    expect(latest.current?.items).toEqual([5]);
    expect(latest.current?.error).toBeNull();
  });

  it('필터가 바뀐 뒤 첫 쪽이 실패하면 이전 필터의 목록을 보여 주지 않는다', async () => {
    await mount('A');
    await settle('A:0', page([1, 2], 0, false));

    await mount('B');
    await fail('B:0');

    expect(latest.current?.items).toEqual([]);
    expect(latest.current?.error).not.toBeNull();
  });

  it('처음 받는 첫 쪽이 실패하면 retry가 처음부터 다시 받는다', async () => {
    await mount('A');
    await fail('A:0');
    expect(latest.current?.items).toEqual([]);
    expect(latest.current?.error).not.toBeNull();

    await act(async () => latest.retry());

    expect(latest.current).toBeNull();
    await settle('A:0', page([1], 0, false));
    expect(latest.current?.items).toEqual([1]);
    expect(latest.current?.error).toBeNull();
  });

  it('loadMore가 실패하면 받아 둔 목록을 두고 retry는 실패한 쪽만 다시 받는다', async () => {
    await mount('A');
    await settle('A:0', page([1, 2], 0, true));
    await act(async () => void latest.loadMore());
    await fail('A:1');

    expect(latest.current?.items).toEqual([1, 2]);
    expect(latest.current?.error).not.toBeNull();

    await act(async () => latest.retry());

    expect(latest.current?.items).toEqual([1, 2]);
    expect(pending.get('A:0') ?? []).toHaveLength(0);
    await settle('A:1', page([3], 1, false));
    expect(latest.current?.items).toEqual([1, 2, 3]);
    expect(latest.current?.page).toBe(1);
    expect(latest.current?.hasNext).toBe(false);
    expect(latest.current?.error).toBeNull();
  });

  it('updateItems는 받아 둔 목록의 항목만 바꾸고 쪽 정보는 그대로 둔다', async () => {
    await mount('A');
    await settle('A:0', page([1, 2], 0, true));

    await act(async () => latest.updateItems((items) => items.map((item) => item * 10)));

    expect(latest.current?.items).toEqual([10, 20]);
    expect(latest.current?.page).toBe(0);
    expect(latest.current?.hasNext).toBe(true);
  });

  it('렌더마다 새로 만든 필터 객체라도 값이 같으면 다시 받지 않고 목록을 그대로 둔다', async () => {
    let objectLatest: PagedList<number, { q: string }> | undefined;
    const objectFetcher = vi.fn((filter: { q: string }, request: PageRequest) => fetcher(filter.q, request));
    function ObjectProbe({ q }: { q: string }) {
      objectLatest = usePagedList(true, { q }, objectFetcher, keyOf);
      return null;
    }

    await act(async () => root.render(<ObjectProbe q="A" />));
    await settle('A:0', page([1, 2], 0, false));
    await act(async () => root.render(<ObjectProbe q="A" />));

    expect(objectFetcher).toHaveBeenCalledTimes(1);
    expect(objectLatest?.current?.items).toEqual([1, 2]);

    await act(async () => root.render(<ObjectProbe q="B" />));

    expect(objectFetcher).toHaveBeenCalledTimes(2);
    expect(objectLatest?.current).toBeNull();
  });

  it('다음 쪽이 앞 쪽과 겹치면 이미 받은 항목은 빼고 순서를 지킨 채 이어 붙인다', async () => {
    await mount('A');
    await settle('A:0', page([1, 2, 3, 4], 0, true));

    await act(async () => void latest.loadMore());
    await settle('A:1', page([3, 4, 5, 6], 1, false));

    expect(latest.current?.items).toEqual([1, 2, 3, 4, 5, 6]);
    expect(latest.current?.page).toBe(1);
    expect(latest.current?.hasNext).toBe(false);
  });

  it('다음 쪽 안에서 같은 항목이 두 번 와도 한 번만 붙인다', async () => {
    await mount('A');
    await settle('A:0', page([1, 2], 0, true));

    await act(async () => void latest.loadMore());
    await settle('A:1', page([3, 3, 4], 1, false));

    expect(latest.current?.items).toEqual([1, 2, 3, 4]);
  });

  it('키가 null인 항목은 겹쳐 보여도 거르지 않고 모두 붙인다', async () => {
    keyOf = (item) => (item < 0 ? null : item);
    await mount('A');
    await settle('A:0', page([1, -1], 0, true));

    await act(async () => void latest.loadMore());
    await settle('A:1', page([-1, 2, 1], 1, false));

    expect(latest.current?.items).toEqual([1, -1, -1, 2]);
  });
  it('쪽 번호 방식 응답이면 cursor를 null로 보내고, 커서 응답이면 직전 응답의 nextCursor를 이어서 보낸다', async () => {
    const requests: PageRequest[] = [];
    fetcher = vi.fn((filter: string, request: PageRequest) => {
      requests.push(request);
      return new Promise<Page<number> | CursorPage<number>>((resolve, reject) => {
        const key = `${filter}:${request.page}`;
        pending.set(key, [...(pending.get(key) ?? []), { resolve, reject }]);
      });
    });
    const cursorPage = (content: number[], nextCursor: number | null): CursorPage<number> => ({
      content,
      size: content.length,
      hasNext: nextCursor !== null,
      nextCursor,
    });

    await mount('A');
    await settle('A:0', cursorPage([9, 8], 8));
    await act(async () => void latest.loadMore());
    await settle('A:1', cursorPage([7, 6], 6));
    await act(async () => void latest.loadMore());
    await settle('A:2', cursorPage([5], null));

    expect(requests).toEqual([
      { page: 0, cursor: null },
      { page: 1, cursor: 8 },
      { page: 2, cursor: 6 },
    ]);
    expect(latest.current?.items).toEqual([9, 8, 7, 6, 5]);
    expect(latest.current?.page).toBe(2);
    expect(latest.current?.hasNext).toBe(false);
  });

  it('쪽 번호 방식 응답이면 다음 요청의 cursor는 null이다', async () => {
    const requests: PageRequest[] = [];
    fetcher = vi.fn((filter: string, request: PageRequest) => {
      requests.push(request);
      return new Promise<Page<number> | CursorPage<number>>((resolve, reject) => {
        const key = `${filter}:${request.page}`;
        pending.set(key, [...(pending.get(key) ?? []), { resolve, reject }]);
      });
    });

    await mount('A');
    await settle('A:0', page([1, 2], 0, true));
    await act(async () => void latest.loadMore());

    expect(requests).toEqual([
      { page: 0, cursor: null },
      { page: 1, cursor: null },
    ]);
  });

  it('refreshLoaded는 커서 응답도 처음부터 nextCursor를 따라 받은 만큼 다시 받는다', async () => {
    const requests: PageRequest[] = [];
    fetcher = vi.fn((filter: string, request: PageRequest) => {
      requests.push(request);
      return new Promise<Page<number> | CursorPage<number>>((resolve, reject) => {
        const key = `${filter}:${request.page}`;
        pending.set(key, [...(pending.get(key) ?? []), { resolve, reject }]);
      });
    });
    const cursorPage = (content: number[], nextCursor: number | null): CursorPage<number> => ({
      content,
      size: content.length,
      hasNext: nextCursor !== null,
      nextCursor,
    });

    await mount('A');
    await settle('A:0', cursorPage([9, 8], 8));
    await act(async () => void latest.loadMore());
    await settle('A:1', cursorPage([7, 6], 6));
    requests.length = 0;

    let refreshed: Promise<void> = Promise.resolve();
    await act(async () => {
      refreshed = latest.refreshLoaded();
    });
    await settle('A:0', cursorPage([10, 9], 9));
    await settle('A:1', cursorPage([8, 7], 7));
    await act(async () => refreshed);

    expect(requests).toEqual([
      { page: 0, cursor: null },
      { page: 1, cursor: 9 },
    ]);
    expect(latest.current?.items).toEqual([10, 9, 8, 7]);
    expect(latest.current?.page).toBe(1);
  });

  it('refreshLoaded는 그사이 목록이 줄어 앞 페이지에서 끝나면 첫 페이지로 돌아가 다시 받지 않는다', async () => {
    const requests: PageRequest[] = [];
    fetcher = vi.fn((filter: string, request: PageRequest) => {
      requests.push(request);
      return new Promise<Page<number> | CursorPage<number>>((resolve, reject) => {
        const key = `${filter}:${request.page}`;
        pending.set(key, [...(pending.get(key) ?? []), { resolve, reject }]);
      });
    });
    const cursorPage = (content: number[], nextCursor: number | null): CursorPage<number> => ({
      content,
      size: content.length,
      hasNext: nextCursor !== null,
      nextCursor,
    });

    await mount('A');
    await settle('A:0', cursorPage([9, 8], 8));
    await act(async () => void latest.loadMore());
    await settle('A:1', cursorPage([7, 6], 6));
    requests.length = 0;

    let refreshed: Promise<void> = Promise.resolve();
    await act(async () => {
      refreshed = latest.refreshLoaded();
    });
    await settle('A:0', cursorPage([9, 8], null));
    await act(async () => refreshed);

    expect(requests).toEqual([{ page: 0, cursor: null }]);
    expect(latest.current?.items).toEqual([9, 8]);
    expect(latest.current?.hasNext).toBe(false);
  });
});
