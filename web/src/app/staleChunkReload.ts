// 배포로 화면 코드 파일 이름이 바뀌면, 옛 탭이 요청한 파일은 서버에 없어서 404가 된다.
// 이때 새로고침하면 새 index.html과 새 파일을 받는다. 새로고침해도 같은 파일이 없으면 끝없이 새로고침하지 않도록 간격을 둔다.
const RELOAD_MARK_KEY = 'moewadeul:stale-chunk-reload-at';
export const MIN_RELOAD_GAP_MS = 10_000;

type MarkStorage = Pick<Storage, 'getItem' | 'setItem'>;

// 새로고침해도 되면 시각을 적고 true를 돌려준다. 최근에 이미 새로고침했거나 시각을 적을 수 없으면 false다.
// 시각을 적을 수 없는 브라우저(저장소 차단)에서 새로고침하면 간격을 지킬 수 없어서 새로고침을 반복할 수 있다.
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
    // 저장소 접근 자체가 예외를 던지는 브라우저에서는 새로고침하지 않고 오류가 RouteBoundary까지 가게 둔다.
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
