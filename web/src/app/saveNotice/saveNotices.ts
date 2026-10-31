import { useCallback, useEffect, useRef, useSyncExternalStore } from 'react';
import { toUserMessage } from '../../api/errors';
import { isOutcomeUnknown } from '../../api/idempotency';
import { onSignedOut } from '../session/signedOut';

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

let signedOutCount = 0;

onSignedOut(() => {
  signedOutCount += 1;
  publish([]);
});

export function useSaveNotices(): readonly SaveNotice[] {
  return useSyncExternalStore(subscribe, getSnapshot);
}

export type SaveCopy<T> = {
  pending: string;
  done: (result: T) => { title: string; link: SaveNoticeLink };
  failed: string;
  checkAt: SaveNoticeLink;
};

type InFlight = { pendingTitle: string; signedOutCount: number; noticeId: number | null };

function failedNotice<T>(error: unknown, copy: SaveCopy<T>): SaveNoticeBody {
  if (isOutcomeUnknown(error)) {
    return {
      state: 'failed',
      title: '저장됐는지 확인하지 못했어요.',
      body: '다시 저장하기 전에 먼저 확인해 주세요.',
      link: copy.checkAt,
    };
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
      for (const entry of saves) {
        if (entry.signedOutCount === signedOutCount) {
          entry.noticeId = addSaveNotice({ state: 'pending', title: entry.pendingTitle });
        }
      }
    };
  }, []);

  return useCallback(<T>(save: Promise<T>, copy: SaveCopy<T>): Promise<T> => {
    const entry: InFlight = { pendingTitle: copy.pending, signedOutCount, noticeId: null };
    inFlight.current.add(entry);

    function settleAfterLeaving(next: SaveNoticeBody) {
      if (entry.noticeId === null || entry.signedOutCount !== signedOutCount) return;
      replaceSaveNotice(entry.noticeId, next);
    }

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
          settleAfterLeaving(failedNotice(error, copy));
        },
      );
    });
  }, []);
}
