import { ApiError, newIdempotencyKey } from './client';

export interface IdempotentRunner {
  run<T>(request: unknown, send: (idempotencyKey: string) => Promise<T>): Promise<T>;
}

// 기록·글·댓글·혼자 가는 일정·베이스캠프를 만드는 요청에 멱등성 키를 정해 준다.
// 서버가 요청을 저장했는데 응답만 사라지면 화면은 실패로 보고, 사용자가 같은 내용으로 다시 누른다.
// 이때 같은 키로 보내면 서버가 새로 만들지 않고 처음 결과를 돌려준다.
// 내용을 고쳐서 보내면 새 키를 쓴다. 같은 키에 다른 내용을 보내면 서버가 422로 거부하기 때문이다.
// 화면 하나에 하나씩 만들어 쓴다.
export function createIdempotentRunner(): IdempotentRunner {
  // 응답을 받지 못한 요청의 내용(JSON)과, 그때 보낸 키
  const unanswered = new Map<string, string>();

  return {
    async run<T>(request: unknown, send: (idempotencyKey: string) => Promise<T>): Promise<T> {
      const content = JSON.stringify(request);
      const key = unanswered.get(content) ?? newIdempotencyKey();
      try {
        const result = await send(key);
        // 만들기에 성공하면 남은 키를 모두 버린다. 같은 내용을 일부러 또 만들 때 이전 결과를 돌려받지 않게 하려는 것이다.
        unanswered.clear();
        return result;
      } catch (error) {
        // 응답을 받지 못했으면 서버가 요청을 저장했는지 알 수 없다. 그래서 같은 내용으로 다시 보낼 때 같은 키를 쓴다.
        if (error instanceof ApiError && error.code === 'NETWORK_ERROR') unanswered.set(content, key);
        else unanswered.delete(content);
        throw error;
      }
    },
  };
}
