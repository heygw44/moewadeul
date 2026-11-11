import { useCallback, useEffect, useRef, useState } from 'react';
import { serverTraceId, toUserMessage } from '../api/errors';
import type { CursorPage, Page, PageRequest } from '../api/types';

type ListState<T, F> = {
  filter: F;
  items: T[];
  page: number;
  cursor: number | null;
  hasNext: boolean;
  error: string | null;
  traceId: string | null;
  failedOn: 'first' | 'next' | null;
};

type Fetcher<T, F> = (filter: F, request: PageRequest, signal?: AbortSignal) => Promise<Page<T> | CursorPage<T>>;

function cursorOf<T>(response: Page<T> | CursorPage<T>): number | null {
  return 'nextCursor' in response ? response.nextCursor : null;
}

export interface PagedList<T, F> {
  current: ListState<T, F> | null;
  loadingMore: boolean;
  loadMore: () => Promise<void>;
  updateItems: (update: (items: T[]) => T[]) => void;
  retry: () => void;
  reload: () => void;
  refreshLoaded: () => Promise<void>;
}

type FilterValue = string | number | Readonly<Record<string, string | number | boolean | undefined>>;

function useValueStable<V extends FilterValue>(value: V): V {
  const [stable, setStable] = useState(value);
  if (JSON.stringify(stable) !== JSON.stringify(value)) {
    setStable(value);
    return value;
  }
  return stable;
}

function newItems<T>(known: T[], incoming: T[], itemKey: (item: T) => string | number | null): T[] {
  const seen = new Set<string | number>();
  for (const item of known) {
    const key = itemKey(item);
    if (key !== null) seen.add(key);
  }
  return incoming.filter((item) => {
    const key = itemKey(item);
    if (key === null) return true;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export function usePagedList<T, F extends FilterValue>(
  enabled: boolean,
  nextFilter: F,
  fetcher: Fetcher<T, F>,
  itemKey: (item: T) => string | number | null,
): PagedList<T, F> {
  const filter = useValueStable(nextFilter);
  const [state, setState] = useState<ListState<T, F> | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const generationRef = useRef(0);
  const fetcherRef = useRef(fetcher);
  const itemKeyRef = useRef(itemKey);
  useEffect(() => {
    fetcherRef.current = fetcher;
    itemKeyRef.current = itemKey;
  });

  useEffect(() => {
    if (!enabled) return;
    generationRef.current += 1;
    const controller = new AbortController();
    fetcherRef.current(filter, { page: 0, cursor: null }, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) {
          generationRef.current += 1;
          setState({
            filter,
            items: page.content,
            page: 0,
            cursor: cursorOf(page),
            hasNext: page.hasNext,
            error: null,
            traceId: null,
            failedOn: null,
          });
        }
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) {
          generationRef.current += 1;
          const failure = {
            error: toUserMessage(caught),
            traceId: serverTraceId(caught) ?? null,
            failedOn: 'first' as const,
          };
          setState((previous) =>
            previous !== null && previous.filter === filter
              ? { ...previous, ...failure }
              : { filter, items: [], page: 0, cursor: null, hasNext: false, ...failure },
          );
        }
      },
    );
    return () => controller.abort();
  }, [enabled, filter, attempt]);

  const current = state !== null && state.filter === filter ? state : null;

  const loadMore = useCallback(async () => {
    if (!current || loadingMore) return;
    setLoadingMore(true);
    setState((previous) =>
      previous !== null && previous.filter === filter
        ? { ...previous, error: null, traceId: null, failedOn: null }
        : previous,
    );
    const generation = generationRef.current;
    try {
      const next = await fetcherRef.current(filter, { page: current.page + 1, cursor: current.cursor });
      if (generation !== generationRef.current) return;
      setState((previous) =>
        previous !== null && previous.filter === filter
          ? {
              ...previous,
              items: [...previous.items, ...newItems(previous.items, next.content, itemKeyRef.current)],
              page: current.page + 1,
              cursor: cursorOf(next),
              hasNext: next.hasNext,
              error: null,
              traceId: null,
              failedOn: null,
            }
          : previous,
      );
    } catch (caught) {
      if (generation !== generationRef.current) return;
      setState((previous) =>
        previous !== null && previous.filter === filter
          ? { ...previous, error: toUserMessage(caught), traceId: serverTraceId(caught) ?? null, failedOn: 'next' }
          : previous,
      );
    } finally {
      setLoadingMore(false);
    }
  }, [current, filter, loadingMore]);

  const updateItems = useCallback(
    (update: (items: T[]) => T[]) =>
      setState((previous) =>
        previous !== null && previous.filter === filter ? { ...previous, items: update(previous.items) } : previous,
      ),
    [filter],
  );

  const retry = useCallback(() => {
    if (current?.failedOn === 'next') {
      void loadMore();
      return;
    }
    setState((previous) =>
      previous === null || previous.items.length === 0
        ? null
        : { ...previous, error: null, traceId: null, failedOn: null },
    );
    setAttempt((value) => value + 1);
  }, [current, loadMore]);

  const reload = useCallback(() => setAttempt((value) => value + 1), []);

  const refreshLoaded = useCallback(async () => {
    if (!current) return;
    generationRef.current += 1;
    const generation = generationRef.current;
    try {
      let items: T[] = [];
      let last: Page<T> | CursorPage<T> | null = null;
      let cursor: number | null = null;
      for (let index = 0; index <= current.page; index++) {
        last = await fetcherRef.current(filter, { page: index, cursor });
        cursor = cursorOf(last);
        items = [...items, ...newItems(items, last.content, itemKeyRef.current)];
      }
      if (generation !== generationRef.current || last === null) return;
      const { hasNext } = last;
      setState((previous) =>
        previous !== null && previous.filter === filter
          ? { ...previous, items, page: current.page, cursor, hasNext, error: null, traceId: null, failedOn: null }
          : previous,
      );
    } catch (caught) {
      if (generation !== generationRef.current) return;
      setState((previous) =>
        previous !== null && previous.filter === filter
          ? { ...previous, error: toUserMessage(caught), traceId: serverTraceId(caught) ?? null, failedOn: 'first' }
          : previous,
      );
    }
  }, [current, filter]);

  return { current, loadingMore, loadMore, updateItems, retry, reload, refreshLoaded };
}
