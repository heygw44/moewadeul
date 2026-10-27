import { fetchMe } from '../../api/auth';
import { ApiError } from '../../api/client';
import type { Me } from '../../api/types';

const RETRY_DELAYS_MS = [1000, 2000];

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
