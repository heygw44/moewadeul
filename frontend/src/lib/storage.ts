// 사생활 보호 모드나 저장소 차단 환경에서는 localStorage 접근이 예외를 던지므로, 읽기는 null로, 쓰기는 false로 돌려준다.
export function readStored(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function writeStored(key: string, value: string): boolean {
  try {
    window.localStorage.setItem(key, value);
    return true;
  } catch {
    return false;
  }
}
