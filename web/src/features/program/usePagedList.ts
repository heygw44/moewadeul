import { useCallback, useEffect, useRef, useState } from 'react';
import { serverTraceId, toUserMessage } from '../../api/errors';
import type { Page } from '../../api/types';

// traceId는 서버가 5xx로 답한 오류의 추적 ID다. 오류를 문구로만 들고 있어도 화면이 문의 번호를 보여 줄 수 있게 함께 둔다.
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
  // 목록을 비우고 첫 쪽부터 다시 받는다.
  retry: () => void;
  // 목록을 그대로 두고 첫 쪽부터 다시 받는다.
  reload: () => void;
}

// 필터가 바뀌면 처음부터 다시 받고, loadMore는 다음 페이지를 이어 붙인다.
// reload는 목록을 비우지 않고 첫 페이지부터 다시 받는다. 목록 항목을 서버에서 바꾼 뒤(숨김·복구) 쪽 번호가 밀려
// loadMore가 항목을 건너뛰지 않게 하려고 쓴다. 다시 받는 동안 이어 붙이던 loadMore 응답은 버린다.
// current가 null이면 현재 필터의 첫 응답을 기다리는 중이다.
export function usePagedList<T, F>(enabled: boolean, filter: F, fetcher: Fetcher<T, F>): PagedList<T, F> {
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
