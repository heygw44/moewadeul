import { useEffect, useSyncExternalStore } from 'react';
import { fetchUnreadCount } from '../../api/notifications';
import { useLocation } from '../../app/router';

let unreadCount = 0;
let requestSeq = 0;
const listeners = new Set<() => void>();

function emit(next: number) {
  if (next === unreadCount) return;
  unreadCount = next;
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function getSnapshot() {
  return unreadCount;
}

export function notificationLabel(count: number): string {
  return count > 0 ? `알림, 안 읽음 ${count}개` : '알림';
}

export function useUnreadCount(): number {
  return useSyncExternalStore(subscribe, getSnapshot);
}

export async function refreshUnreadCount(): Promise<void> {
  const seq = ++requestSeq;
  const result = await fetchUnreadCount().catch(() => null);
  if (result && seq === requestSeq) emit(result.count);
}

function resetUnreadCount(): void {
  requestSeq++;
  emit(0);
}

function useUnreadCountSync(enabled: boolean): void {
  const { pathname } = useLocation();

  useEffect(() => {
    if (!enabled) {
      resetUnreadCount();
      return;
    }
    void refreshUnreadCount();
  }, [enabled, pathname]);

  useEffect(() => {
    if (!enabled) return;
    function handleVisibility() {
      if (document.visibilityState === 'visible') void refreshUnreadCount();
    }
    document.addEventListener('visibilitychange', handleVisibility);
    return () => document.removeEventListener('visibilitychange', handleVisibility);
  }, [enabled]);
}

export function UnreadCountSync({ enabled }: { enabled: boolean }): null {
  useUnreadCountSync(enabled);
  return null;
}
