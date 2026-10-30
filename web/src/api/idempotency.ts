import { ApiError, newIdempotencyKey } from './client';

export interface IdempotentRunner {
  run<T>(request: unknown, send: (idempotencyKey: string) => Promise<T>): Promise<T>;
}

export function isOutcomeUnknown(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === 'NETWORK_ERROR' || error.code === 'REQUEST_TIMEOUT' || error.code === 'IDEMPOTENCY_IN_PROGRESS')
  );
}

export function createIdempotentRunner(): IdempotentRunner {
  const pendingKeys = new Map<string, string>();

  return {
    async run<T>(request: unknown, send: (idempotencyKey: string) => Promise<T>): Promise<T> {
      const content = JSON.stringify(request);
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
    },
  };
}
