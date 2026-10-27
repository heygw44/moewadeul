import { describe, expect, it } from 'vitest';
import { ApiError } from './client';
import { createIdempotentRunner } from './idempotency';

function sender(...outcomes: Array<'ok' | 'network' | 'business'>) {
  const keys: string[] = [];
  const send = async (key: string) => {
    keys.push(key);
    const outcome = outcomes[keys.length - 1];
    if (outcome === 'network') throw new ApiError(0, 'NETWORK_ERROR', null);
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
});
