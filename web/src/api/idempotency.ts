import { ApiError, newIdempotencyKey } from './client';

export interface IdempotentRunner {
  run<T>(request: unknown, send: (idempotencyKey: string) => Promise<T>): Promise<T>;
}

export function createIdempotentRunner(): IdempotentRunner {
  const unanswered = new Map<string, string>();

  return {
    async run<T>(request: unknown, send: (idempotencyKey: string) => Promise<T>): Promise<T> {
      const content = JSON.stringify(request);
      const key = unanswered.get(content) ?? newIdempotencyKey();
      try {
        const result = await send(key);
        unanswered.clear();
        return result;
      } catch (error) {
        if (error instanceof ApiError && error.code === 'NETWORK_ERROR') unanswered.set(content, key);
        else unanswered.delete(content);
        throw error;
      }
    },
  };
}
