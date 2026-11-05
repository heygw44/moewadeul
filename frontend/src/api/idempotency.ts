import { ApiError, newIdempotencyKey } from './client';

export interface IdempotentRunner {
  run<T>(request: unknown, send: (idempotencyKey: string) => Promise<T>): Promise<T>;
}

const GATEWAY_FAILURE_STATUSES: ReadonlySet<number> = new Set([502, 504]);

export function isOutcomeUnknown(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === 'NETWORK_ERROR' ||
      error.code === 'REQUEST_TIMEOUT' ||
      error.code === 'IDEMPOTENCY_IN_PROGRESS' ||
      GATEWAY_FAILURE_STATUSES.has(error.status))
  );
}

export function createIdempotentRunner(): IdempotentRunner {
  const pendingKeys = new Map<string, string>();
  const inFlight = new Map<string, Promise<unknown>>();

  async function attempt<T>(content: string, send: (idempotencyKey: string) => Promise<T>): Promise<T> {
    const key = pendingKeys.get(content) ?? newIdempotencyKey();
    try {
      const result = await send(key);
      pendingKeys.clear();
      return result;
    } catch (error) {
      if (isOutcomeUnknown(error)) pendingKeys.set(content, key);
      else pendingKeys.delete(content);
      throw error;
    }
  }

  return {
    run<T>(request: unknown, send: (idempotencyKey: string) => Promise<T>): Promise<T> {
      const content = JSON.stringify(request);
      const running = inFlight.get(content);
      if (running) return running as Promise<T>;
      const started = attempt(content, send).finally(() => inFlight.delete(content));
      inFlight.set(content, started);
      return started;
    },
  };
}
