import { useCallback, useEffect, useRef, useSyncExternalStore } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { isOutcomeUnknown } from '../../api/idempotency';

export type SaveNoticeLink = { to: string; label: string };

type SaveNoticeBody =
  | { state: 'pending'; title: string }
  | { state: 'done'; title: string; link: SaveNoticeLink }
  | { state: 'failed'; title: string; body: string; link?: SaveNoticeLink };

export type SaveNotice = SaveNoticeBody & { id: number };

let notices: readonly SaveNotice[] = [];
let nextId = 1;
const listeners = new Set<() => void>();

function publish(next: readonly SaveNotice[]): void {
  notices = next;
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function getSnapshot(): readonly SaveNotice[] {
  return notices;
}

function addSaveNotice(notice: SaveNoticeBody): number {
  const id = nextId++;
  publish([...notices, { ...notice, id }]);
  return id;
}

function replaceSaveNotice(id: number, notice: SaveNoticeBody): void {
  publish(notices.map((existing) => (existing.id === id ? { ...notice, id } : existing)));
}

export function dismissSaveNotice(id: number): void {
  publish(notices.filter((existing) => existing.id !== id));
}

export function resetSaveNoticesForTest(): void {
  publish([]);
}

export function useSaveNotices(): readonly SaveNotice[] {
  return useSyncExternalStore(subscribe, getSnapshot);
}

export type SaveCopy<T> = {
  pending: string;
  done: (result: T) => { title: string; link: SaveNoticeLink };
  failed: string;
  checkAt: SaveNoticeLink;
};

type InFlight = { pendingTitle: string; noticeId: number | null };

const OUTCOME_UNKNOWN_BODY = '저장됐는지 확인하지 못했어요. 다시 저장하기 전에 먼저 확인해 주세요.';

function failedNotice<T>(error: unknown, copy: SaveCopy<T>): SaveNoticeBody {
  if (isOutcomeUnknown(error)) {
    return { state: 'failed', title: copy.failed, body: OUTCOME_UNKNOWN_BODY, link: copy.checkAt };
  }
  return { state: 'failed', title: copy.failed, body: toUserMessage(error) };
}

export function useSaveOutlivingScreen(): <T>(save: Promise<T>, copy: SaveCopy<T>) => Promise<T> {
  const mounted = useRef(true);
  const inFlight = useRef(new Set<InFlight>());

  useEffect(() => {
    mounted.current = true;
    const saves = inFlight.current;
    return () => {
      mounted.current = false;
      for (const entry of saves) entry.noticeId = addSaveNotice({ state: 'pending', title: entry.pendingTitle });
    };
  }, []);

  return useCallback(<T>(save: Promise<T>, copy: SaveCopy<T>): Promise<T> => {
    const entry: InFlight = { pendingTitle: copy.pending, noticeId: null };
    inFlight.current.add(entry);

    function settleAfterLeaving(next: SaveNoticeBody | null) {
      if (entry.noticeId === null) return;
      if (next === null) dismissSaveNotice(entry.noticeId);
      else replaceSaveNotice(entry.noticeId, next);
    }

    // 화면이 사라진 뒤에는 이 promise를 끝내지 않는다. 끝내면 호출한 쪽의 setState·navigate가 남의 화면에서 실행된다.
    return new Promise<T>((resolve, reject) => {
      save.then(
        (result) => {
          inFlight.current.delete(entry);
          if (mounted.current) resolve(result);
          else settleAfterLeaving({ state: 'done', ...copy.done(result) });
        },
        (error: unknown) => {
          inFlight.current.delete(entry);
          if (mounted.current) {
            reject(error);
            return;
          }
          const needsLogin = error instanceof ApiError && error.code === 'AUTHENTICATION_REQUIRED';
          settleAfterLeaving(needsLogin ? null : failedNotice(error, copy));
        },
      );
    });
  }, []);
}
