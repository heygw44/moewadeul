const RELOAD_MARK_KEY = 'moewadeul:stale-chunk-reload-at';
export const MIN_RELOAD_GAP_MS = 10_000;

type MarkStorage = Pick<Storage, 'getItem' | 'setItem'>;

export function claimReload(storage: MarkStorage, now: number): boolean {
  try {
    const last = Number(storage.getItem(RELOAD_MARK_KEY));
    if (Number.isFinite(last) && last > 0 && now - last < MIN_RELOAD_GAP_MS) return false;
    storage.setItem(RELOAD_MARK_KEY, String(now));
    return true;
  } catch {
    return false;
  }
}

export function installStaleChunkReload(): void {
  window.addEventListener('vite:preloadError', (event) => {
    // Vite가 화면 코드(동적 import)를 받지 못하면 이 이벤트를 내보낸다. preventDefault를 부르면 Vite가 오류를 던지지 않는다.
    let storage: Storage;
    try {
      storage = window.sessionStorage;
    } catch {
      return;
    }
    if (!claimReload(storage, Date.now())) return;
    event.preventDefault();
    window.location.reload();
  });
}
