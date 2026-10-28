import { useCallback, useEffect, useRef, useState } from 'react';
import { serverTraceId, toUserMessage } from '../../api/errors';
import type { Page } from '../../api/types';

type ListState<T, F> = {
  filter: F;
  items: T[];
  page: number;
  hasNext: boolean;
  error: string | null;
  traceId: string | null;
};

type Fetcher<T, F> = (filter: F, page: number, signal?: AbortSignal) => Promise<Page<T>>;

export interface PagedList<T, F> {
  current: ListState<T, F> | null;
  loadingMore: boolean;
  loadMore: () => Promise<void>;
  retry: () => void;
  reload: () => void;
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

export function usePagedList<T, F extends FilterValue>(
  enabled: boolean,
  nextFilter: F,
  fetcher: Fetcher<T, F>,
): PagedList<T, F> {
  const filter = useValueStable(nextFilter);
  const [state, setState] = useState<ListState<T, F> | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const generationRef = useRef(0);
  const fetcherRef = useRef(fetcher);
  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  useEffect(() => {
    if (!enabled) return;
    generationRef.current += 1;
    const controller = new AbortController();
    fetcherRef.current(filter, 0, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) {
          generationRef.current += 1;
          setState({ filter, items: page.content, page: page.page, hasNext: page.hasNext, error: null, traceId: null });
        }
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) {
          generationRef.current += 1;
          setState({
            filter,
            items: [],
            page: 0,
            hasNext: false,
            error: toUserMessage(caught),
            traceId: serverTraceId(caught) ?? null,
          });
        }
      },
    );
    return () => controller.abort();
  }, [enabled, filter, attempt]);

  const current = state !== null && state.filter === filter ? state : null;

  const loadMore = useCallback(async () => {
    if (!current || loadingMore) return;
    setLoadingMore(true);
    const generation = generationRef.current;
    try {
      const next = await fetcherRef.current(filter, current.page + 1);
      if (generation !== generationRef.current) return;
      setState((previous) =>
        previous !== null && previous.filter === filter
          ? {
              ...previous,
              items: [...previous.items, ...next.content],
              page: next.page,
              hasNext: next.hasNext,
              error: null,
              traceId: null,
            }
          : previous,
      );
    } catch (caught) {
      if (generation !== generationRef.current) return;
      setState((previous) =>
        previous !== null && previous.filter === filter
          ? { ...previous, error: toUserMessage(caught), traceId: serverTraceId(caught) ?? null }
          : previous,
      );
    } finally {
      setLoadingMore(false);
    }
  }, [current, filter, loadingMore]);

  const retry = useCallback(() => {
    setState(null);
    setAttempt((value) => value + 1);
  }, []);

  const reload = useCallback(() => setAttempt((value) => value + 1), []);

  return { current, loadingMore, loadMore, retry, reload };
}
