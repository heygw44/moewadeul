import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiFetch } from './client';

const TIMEOUT_MS = 15_000;

function abortError() {
  return new DOMException('aborted', 'AbortError');
}

function fetchThatNeverAnswers() {
  return vi.fn(
    (_url: string, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        if (init?.signal?.aborted) {
          reject(abortError());
          return;
        }
        init?.signal?.addEventListener('abort', () => reject(abortError()));
      }),
  );
}
async function settle<T>(promise: Promise<T>): Promise<{ value?: T; error?: unknown }> {
  return promise.then(
    (value) => ({ value }),
    (error: unknown) => ({ error }),
  );
}

describe('apiFetch 제한 시간', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('GET은 15초 안에 응답이 없으면 REQUEST_TIMEOUT으로 끝난다', async () => {
    vi.stubGlobal('fetch', fetchThatNeverAnswers());
    let finished = false;
    const result = settle(apiFetch('/api/spots')).then((r) => {
      finished = true;
      return r;
    });

    await vi.advanceTimersByTimeAsync(TIMEOUT_MS - 1);
    expect(finished).toBe(false);

    await vi.advanceTimersByTimeAsync(1);
    const { error } = await result;
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 0, code: 'REQUEST_TIMEOUT' });
  });

  it('GET은 응답 헤더만 오고 본문이 멈춰도 15초 뒤 REQUEST_TIMEOUT으로 끝난다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: string, init?: RequestInit) => ({
        ok: true,
        status: 200,
        headers: new Headers(),
        text: () =>
          new Promise<string>((_resolve, reject) => {
            init?.signal?.addEventListener('abort', () => reject(abortError()));
          }),
      })),
    );
    const result = settle(apiFetch('/api/spots'));

    await vi.advanceTimersByTimeAsync(TIMEOUT_MS);
    expect((await result).error).toMatchObject({ code: 'REQUEST_TIMEOUT' });
  });

  it('호출하는 쪽이 취소하면 제한 시간 오류가 아니라 AbortError가 그대로 나온다', async () => {
    vi.stubGlobal('fetch', fetchThatNeverAnswers());
    const controller = new AbortController();
    const result = settle(apiFetch('/api/spots', { signal: controller.signal }));

    await vi.advanceTimersByTimeAsync(1000);
    controller.abort();
    const { error } = await result;
    expect(error).toBeInstanceOf(DOMException);
    expect(error).toMatchObject({ name: 'AbortError' });
  });

  it('이미 끊긴 신호로 부르면 요청을 보내지 않고 AbortError가 나온다', async () => {
    const fetchMock = fetchThatNeverAnswers();
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    controller.abort();

    const { error } = await settle(apiFetch('/api/spots', { signal: controller.signal }));
    expect(error).toMatchObject({ name: 'AbortError' });
  });

  it('제때 받은 GET 응답은 그대로 돌려주고 제한 시간 타이머를 남기지 않는다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify({ ok: 1 }), { status: 200 })),
    );

    await expect(apiFetch('/api/spots')).resolves.toEqual({ ok: 1 });
    expect(vi.getTimerCount()).toBe(0);
  });

  it('POST는 15초가 지나도 끊지 않는다. 서버가 처리를 마쳤을 수 있기 때문이다', async () => {
    document.cookie = 'XSRF-TOKEN=token';
    let seenSignal: AbortSignal | null | undefined;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: string, init?: RequestInit) => {
        seenSignal = init?.signal;
        await new Promise((resolve) => setTimeout(resolve, 60_000));
        return new Response(JSON.stringify({ id: 7 }), { status: 201 });
      }),
    );
    const result = apiFetch('/api/memories', { method: 'POST', body: { title: 'x' } });

    await vi.advanceTimersByTimeAsync(60_000);
    await expect(result).resolves.toEqual({ id: 7 });
    expect(seenSignal).toBeUndefined();
  });
});
