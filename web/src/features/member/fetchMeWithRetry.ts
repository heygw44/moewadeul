import { fetchMe } from '../../api/auth';
import { ApiError } from '../../api/client';
import type { Me } from '../../api/types';

// 앱을 열 때 신호가 잠깐 나쁜 경우를 견디도록, 서버에 닿지 못했거나 5xx로 실패하면 이 간격으로 두 번 더 묻는다.
const RETRY_DELAYS_MS = [1000, 2000];

// 일시적인 장애만 다시 시도한다. 응답이 없어서 제한 시간을 넘긴 경우는 기다림이 이미 길어서 바로 실패로 넘긴다.
function isTransient(error: unknown): boolean {
  return error instanceof ApiError && (error.code === 'NETWORK_ERROR' || error.status >= 500);
}

function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(signal.reason);
      return;
    }
    const onAbort = () => {
      clearTimeout(timer);
      reject(signal?.reason);
    };
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, ms);
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

// 로그인하지 않았으면(401) null이다. 일시 장애는 몇 번 다시 묻고, 계속 실패하면 마지막 오류를 던진다.
// 던진 오류를 로그인하지 않은 상태로 읽으면 안 된다. 세션이 멀쩡한 회원이 로그인 화면으로 쫓겨난다.
export async function fetchMeWithRetry(signal?: AbortSignal): Promise<Me | null> {
  for (let attempt = 0; ; attempt += 1) {
    try {
      return await fetchMe(signal);
    } catch (error) {
      const delay = RETRY_DELAYS_MS[attempt];
      if (delay === undefined || !isTransient(error)) throw error;
      await sleep(delay, signal);
    }
  }
}
