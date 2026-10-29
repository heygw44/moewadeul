// 브라우저는 주소를 읽을 때 탭·줄바꿈을 지우고 '\'를 '/'로 읽는다. 예를 들어 '/%09/evil.com'은 '//evil.com'이 되어
// 다른 사이트로 간다. 그래서 제어 문자나 '\'가 하나라도 있으면 버리고, 남은 값은 브라우저와 같은 규칙(URL)으로
// 이 사이트 주소에 붙여 읽은 뒤 출처가 같을 때만 그 경로를 돌려준다.
// 읽은 경로가 '//'로 시작하면(예: '/..//evil.com') 다시 넘겼을 때 다른 사이트 주소가 되므로 함께 버린다.
export function safeNextPath(search: string): string | null {
  const next = new URLSearchParams(search).get('next');
  return next ? safeInternalPath(next) : null;
}

export function safeInternalPath(path: string): string | null {
  if (!path.startsWith('/') || path.startsWith('//') || hasUnsafeChar(path)) {
    return null;
  }
  let url: URL;
  try {
    url = new URL(path, window.location.origin);
  } catch {
    return null;
  }
  if (url.origin !== window.location.origin || url.pathname.startsWith('//')) {
    return null;
  }
  return url.pathname + url.search + url.hash;
}

function hasUnsafeChar(value: string): boolean {
  for (let i = 0; i < value.length; i++) {
    const code = value.charCodeAt(i);
    if (code <= 0x1f || code === 0x7f || value[i] === '\\') {
      return true;
    }
  }
  return false;
}

export function withNext(path: string, next: string | null): string {
  return next ? `${path}?next=${encodeURIComponent(next)}` : path;
}
