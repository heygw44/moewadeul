import { ApiError, newIdempotencyKey } from './client';

export interface IdempotentRunner {
  run<T>(request: unknown, send: (idempotencyKey: string) => Promise<T>): Promise<T>;
}

// 응답을 받지 못한 요청과 아직 처리 중이라는 409는 첫 요청의 결과를 모른다는 점이 같다.
// 다시 보낼 때 같은 키를 써야 두 번 처리되지 않는다.
export function isUnanswered(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === 'NETWORK_ERROR' || error.code === 'REQUEST_TIMEOUT' || error.code === 'IDEMPOTENCY_IN_PROGRESS')
  );
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
        if (isUnanswered(error)) unanswered.set(content, key);
        else unanswered.delete(content);
        throw error;
      }
    },
  };
}
