const listeners = new Set<() => void>();

// 세션이 끊길 때(로그아웃, 401, 탈퇴 뒤 재확인) 회원별 화면 상태를 지우도록 각 기능이 구독한다.
export function onSignedOut(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function notifySignedOut(): void {
  listeners.forEach((listener) => listener());
}
