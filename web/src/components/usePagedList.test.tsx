import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Page } from '../api/types';
import { usePagedList } from './usePagedList';
import type { PagedList } from './usePagedList';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

type Result = PagedList<number, string>;

function page(content: number[], pageNumber: number, hasNext: boolean): Page<number> {
  return { content, page: pageNumber, size: content.length, hasNext } as Page<number>;
}

type Deferred = { resolve: (value: Page<number>) => void };

describe('usePagedList', () => {
  let container: HTMLDivElement;
  let root: Root;
  let latest: Result;
  let pending: Map<string, Deferred[]>;
  let fetcher: (filter: string, pageNumber: number) => Promise<Page<number>>;

  function Probe({ filter }: { filter: string }) {
    latest = usePagedList(true, filter, fetcher);
    return null;
  }

  async function settle(key: string, value: Page<number>) {
    const next = pending.get(key)?.shift();
    if (!next) throw new Error(`대기 중인 요청이 없어요: ${key}`);
    await act(async () => next.resolve(value));
  }

  beforeEach(() => {
    container = document.createElement('div');
    root = createRoot(container);
    pending = new Map();
    fetcher = vi.fn(
      (filter: string, pageNumber: number) =>
        new Promise<Page<number>>((resolve) => {
          const key = `${filter}:${pageNumber}`;
          pending.set(key, [...(pending.get(key) ?? []), { resolve }]);
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

  it('렌더마다 새로 만든 필터 객체라도 값이 같으면 다시 받지 않고 목록을 그대로 둔다', async () => {
    let objectLatest: PagedList<number, { q: string }> | undefined;
    const objectFetcher = vi.fn((filter: { q: string }, pageNumber: number) => fetcher(filter.q, pageNumber));
    function ObjectProbe({ q }: { q: string }) {
      objectLatest = usePagedList(true, { q }, objectFetcher);
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
});
