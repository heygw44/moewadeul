import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiFetch } from './client';

const TIMEOUT_MS = 15_000;
const MUTATION_TIMEOUT_MS = 30_000;

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

  it('POST는 15초가 지나도 기다려 늦게 온 응답을 돌려준다', async () => {
    document.cookie = 'XSRF-TOKEN=token';
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        await new Promise((resolve) => setTimeout(resolve, 20_000));
        return new Response(JSON.stringify({ id: 7 }), { status: 201 });
      }),
    );
    const result = apiFetch('/api/memories', { method: 'POST', body: { title: 'x' } });

    await vi.advanceTimersByTimeAsync(20_000);
    await expect(result).resolves.toEqual({ id: 7 });
  });

  it('POST는 30초 안에 응답이 없으면 REQUEST_TIMEOUT으로 끝난다', async () => {
    document.cookie = 'XSRF-TOKEN=token';
    vi.stubGlobal('fetch', fetchThatNeverAnswers());
    let finished = false;
    const result = settle(apiFetch('/api/memories', { method: 'POST', body: { title: 'x' } })).then((r) => {
      finished = true;
      return r;
    });

    await vi.advanceTimersByTimeAsync(MUTATION_TIMEOUT_MS - 1);
    expect(finished).toBe(false);

    await vi.advanceTimersByTimeAsync(1);
    expect(finished).toBe(true);
    expect((await result).error).toMatchObject({ status: 0, code: 'REQUEST_TIMEOUT' });
  });
});

describe('apiFetch CSRF 쿠키', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('CSRF 쿠키가 없고 받으러 간 요청도 실패하면 변경 요청을 보내지 않고 NETWORK_ERROR로 끝난다', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/api/me') throw new TypeError('Failed to fetch');
      return new Response(JSON.stringify({ code: 'ACCESS_DENIED', message: '권한' }), { status: 403 });
    });
    vi.stubGlobal('fetch', fetchMock);

    const { error } = await settle(apiFetch('/api/memories', { method: 'POST', body: { title: 'x' } }));

    expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/me']);
  });

  it('CSRF 쿠키를 받아 오면 그 토큰을 실어 변경 요청을 보낸다', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/api/me') {
        document.cookie = 'XSRF-TOKEN=fresh';
        return new Response(null, { status: 401 });
      }
      return new Response(JSON.stringify({ id: 7 }), { status: 201 });
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiFetch('/api/memories', { method: 'POST', body: { title: 'x' } })).resolves.toEqual({ id: 7 });
    const [, init] = fetchMock.mock.calls[1] as unknown as [string, RequestInit];
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('fresh');
  });
});

describe('ApiError 문구', () => {
  it('필드 오류 없는 INVALID_INPUT은 서버가 보낸 구체적인 문구를 보인다', () => {
    const error = new ApiError(400, 'INVALID_INPUT', {
      code: 'INVALID_INPUT',
      message: '답글을 달 수 없는 댓글이에요.',
    });

    expect(error.message).toBe('답글을 달 수 없는 댓글이에요.');
  });

  it('필드 오류가 있는 INVALID_INPUT은 표시된 칸을 안내하는 표 문구를 보인다', () => {
    const error = new ApiError(400, 'INVALID_INPUT', {
      code: 'INVALID_INPUT',
      message: '입력값이 올바르지 않습니다.',
      fieldErrors: [{ field: 'title', reason: '제목을 적어 주세요.' }],
    });

    expect(error.message).toBe('입력한 내용에 맞지 않는 곳이 있어요. 표시된 칸을 수정해 주세요.');
  });

  it('INVALID_INPUT이어도 서버 문구가 비어 있으면 표 문구를 보인다', () => {
    const error = new ApiError(400, 'INVALID_INPUT', { code: 'INVALID_INPUT', message: '' });

    expect(error.message).toBe('입력한 내용에 맞지 않는 곳이 있어요. 표시된 칸을 수정해 주세요.');
  });

  it('다른 코드는 표 문구가 서버 문구보다 먼저다', () => {
    const error = new ApiError(404, 'NOT_FOUND', { code: 'NOT_FOUND', message: 'server text' });

    expect(error.message).toBe('찾는 내용이 없거나 삭제됐어요. 목록에서 다시 골라 주세요.');
  });
});
