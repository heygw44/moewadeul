import { describe, expect, it } from 'vitest';
import { ApiError } from './client';
import { createIdempotentRunner } from './idempotency';

function sender(
  ...outcomes: Array<
    'ok' | 'network' | 'timeout' | 'inProgress' | 'business' | 'badGateway' | 'gatewayTimeout' | 'unavailable'
  >
) {
  const keys: string[] = [];
  const send = async (key: string) => {
    keys.push(key);
    const outcome = outcomes[keys.length - 1];
    if (outcome === 'network') throw new ApiError(0, 'NETWORK_ERROR', null);
    if (outcome === 'timeout') throw new ApiError(0, 'REQUEST_TIMEOUT', null);
    if (outcome === 'inProgress') throw new ApiError(409, 'IDEMPOTENCY_IN_PROGRESS', null);
    if (outcome === 'badGateway') throw new ApiError(502, 'INTERNAL_ERROR', null);
    if (outcome === 'gatewayTimeout') throw new ApiError(504, 'INTERNAL_ERROR', null);
    if (outcome === 'unavailable') throw new ApiError(503, 'RESOURCE_BUSY', null);
    if (outcome === 'business') throw new ApiError(409, 'SOLO_TRIP_OVERLAP', null);
    return { id: keys.length };
  };
  return { keys, send };
}

describe('createIdempotentRunner', () => {
  it('응답을 받지 못한 뒤 같은 내용으로 다시 보내면 같은 키를 쓴다', async () => {
    const runner = createIdempotentRunner();
    const { keys, send } = sender('network', 'ok');

    await expect(runner.run({ title: '가' }, send)).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
    await runner.run({ title: '가' }, send);

    expect(keys).toHaveLength(2);
    expect(keys[1]).toBe(keys[0]);
  });

  it('제한 시간이 지나 끊긴 뒤 같은 내용으로 다시 보내면 같은 키를 쓴다', async () => {
    const runner = createIdempotentRunner();
    const { keys, send } = sender('timeout', 'ok');

    await expect(runner.run({ title: '가' }, send)).rejects.toMatchObject({ code: 'REQUEST_TIMEOUT' });
    await runner.run({ title: '가' }, send);

    expect(keys[1]).toBe(keys[0]);
  });

  it('서버가 아직 처리 중이라고 답한 뒤 같은 내용으로 다시 보내면 같은 키를 쓴다', async () => {
    const runner = createIdempotentRunner();
    const { keys, send } = sender('inProgress', 'ok');

    await expect(runner.run({ title: '가' }, send)).rejects.toMatchObject({ code: 'IDEMPOTENCY_IN_PROGRESS' });
    await runner.run({ title: '가' }, send);

    expect(keys[1]).toBe(keys[0]);
  });

  it.each([
    ['502', 'badGateway'],
    ['504', 'gatewayTimeout'],
  ] as const)('앞단이 %s를 돌려준 뒤 같은 내용으로 다시 보내면 같은 키를 쓴다', async (_status, outcome) => {
    const runner = createIdempotentRunner();
    const { keys, send } = sender(outcome, 'ok');

    await expect(runner.run({ title: '가' }, send)).rejects.toBeInstanceOf(ApiError);
    await runner.run({ title: '가' }, send);

    expect(keys[1]).toBe(keys[0]);
  });

  it('서버가 처리하지 못했다고 밝힌 503 뒤에는 새 키를 쓴다', async () => {
    const runner = createIdempotentRunner();
    const { keys, send } = sender('unavailable', 'ok');

    await expect(runner.run({ title: '가' }, send)).rejects.toMatchObject({ status: 503 });
    await runner.run({ title: '가' }, send);

    expect(keys[1]).not.toBe(keys[0]);
  });

  it('응답을 받지 못한 뒤 내용을 고쳐 보내면 새 키를 쓴다', async () => {
    const runner = createIdempotentRunner();
    const { keys, send } = sender('network', 'ok');

    await expect(runner.run({ title: '가' }, send)).rejects.toBeInstanceOf(ApiError);
    await runner.run({ title: '나' }, send);

    expect(keys[1]).not.toBe(keys[0]);
  });

  it('내용을 고쳤다가 처음 내용으로 되돌려 보내면 처음 키를 다시 쓴다', async () => {
    const runner = createIdempotentRunner();
    const { keys, send } = sender('network', 'network', 'ok');

    await expect(runner.run({ title: '가' }, send)).rejects.toBeInstanceOf(ApiError);
    await expect(runner.run({ title: '나' }, send)).rejects.toBeInstanceOf(ApiError);
    await runner.run({ title: '가' }, send);

    expect(keys[2]).toBe(keys[0]);
  });

  it('서버가 오류로 답했으면 같은 내용으로 다시 보낼 때 새 키를 쓴다', async () => {
    const runner = createIdempotentRunner();
    const { keys, send } = sender('business', 'ok');

    await expect(runner.run({ spotId: 1 }, send)).rejects.toMatchObject({ code: 'SOLO_TRIP_OVERLAP' });
    await runner.run({ spotId: 1 }, send);

    expect(keys[1]).not.toBe(keys[0]);
  });

  it('성공한 뒤 같은 내용을 일부러 또 만들면 새 키를 쓴다', async () => {
    const runner = createIdempotentRunner();
    const { keys, send } = sender('ok', 'ok');

    await runner.run({ content: '댓글' }, send);
    await runner.run({ content: '댓글' }, send);

    expect(keys[1]).not.toBe(keys[0]);
  });

  it('다른 내용의 만들기가 성공하면 응답을 받지 못했던 요청의 키를 버린다', async () => {
    const runner = createIdempotentRunner();
    const { keys, send } = sender('network', 'ok', 'ok');

    await expect(runner.run({ content: '가' }, send)).rejects.toBeInstanceOf(ApiError);
    await runner.run({ content: '나' }, send);
    await runner.run({ content: '가' }, send);

    expect(keys[2]).not.toBe(keys[0]);
  });

  it('같은 내용의 요청이 아직 진행 중일 때 다시 부르면 새로 보내지 않고 같은 결과를 돌려준다', async () => {
    const runner = createIdempotentRunner();
    const keys: string[] = [];
    let release: () => void = () => {};
    const send = (key: string) => {
      keys.push(key);
      return new Promise<{ id: number }>((resolve) => {
        release = () => resolve({ id: keys.length });
      });
    };

    const first = runner.run({ title: '가' }, send);
    const second = runner.run({ title: '가' }, send);
    release();

    await expect(first).resolves.toEqual({ id: 1 });
    await expect(second).resolves.toEqual({ id: 1 });
    expect(keys).toHaveLength(1);
  });

  it('진행 중이던 요청이 끝난 뒤에는 같은 내용도 다시 보낸다', async () => {
    const runner = createIdempotentRunner();
    const { keys, send } = sender('ok', 'ok');

    await runner.run({ title: '가' }, send);
    await runner.run({ title: '가' }, send);

    expect(keys).toHaveLength(2);
  });
});
