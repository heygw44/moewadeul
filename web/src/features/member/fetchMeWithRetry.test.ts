import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchMe } from '../../api/auth';
import { ApiError } from '../../api/client';
import type { Me } from '../../api/types';
import { fetchMeWithRetry } from './fetchMeWithRetry';

vi.mock('../../api/auth', () => ({ fetchMe: vi.fn() }));

const fetchMeMock = vi.mocked(fetchMe);

const ME: Me = {
  memberId: 1,
  email: 'a@b.c',
  nickname: '산사람',
  status: 'ACTIVE',
  role: 'USER',
  selfAgeGroup: null,
  selfGender: null,
  identityVerified: false,
  trustLevel: 1,
};

const serverError = () => new ApiError(500, 'INTERNAL_ERROR', null);
const networkError = () => new ApiError(0, 'NETWORK_ERROR', null);

describe('fetchMeWithRetry', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    fetchMeMock.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('네트워크 오류나 5xx는 1초, 2초 간격으로 두 번 더 묻고 성공하면 그 값을 돌려준다', async () => {
    fetchMeMock.mockRejectedValueOnce(networkError()).mockRejectedValueOnce(serverError()).mockResolvedValueOnce(ME);
    const result = fetchMeWithRetry();

    expect(fetchMeMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(999);
    expect(fetchMeMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(fetchMeMock).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1999);
    expect(fetchMeMock).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1);

    await expect(result).resolves.toBe(ME);
    expect(fetchMeMock).toHaveBeenCalledTimes(3);
  });

  it('세 번 모두 실패하면 로그인하지 않은 것으로 바꾸지 않고 마지막 오류를 던진다', async () => {
    const last = serverError();
    fetchMeMock.mockRejectedValueOnce(networkError()).mockRejectedValueOnce(serverError()).mockRejectedValueOnce(last);
    const result = fetchMeWithRetry().catch((error: unknown) => error);

    await vi.advanceTimersByTimeAsync(3000);
    await expect(result).resolves.toBe(last);
    expect(fetchMeMock).toHaveBeenCalledTimes(3);
  });

  it('로그인하지 않은 응답(null)은 다시 묻지 않고 그대로 돌려준다', async () => {
    fetchMeMock.mockResolvedValueOnce(null);

    await expect(fetchMeWithRetry()).resolves.toBeNull();
    expect(fetchMeMock).toHaveBeenCalledTimes(1);
  });

  it('제한 시간을 넘긴 경우와 4xx는 다시 묻지 않고 바로 던진다', async () => {
    const timeout = new ApiError(0, 'REQUEST_TIMEOUT', null);
    fetchMeMock.mockRejectedValueOnce(timeout);
    await expect(fetchMeWithRetry()).rejects.toBe(timeout);
    expect(fetchMeMock).toHaveBeenCalledTimes(1);

    fetchMeMock.mockReset();
    const forbidden = new ApiError(403, 'FORBIDDEN', null);
    fetchMeMock.mockRejectedValueOnce(forbidden);
    await expect(fetchMeWithRetry()).rejects.toBe(forbidden);
    expect(fetchMeMock).toHaveBeenCalledTimes(1);
  });

  it('기다리는 중에 취소하면 더 묻지 않고 끝난다', async () => {
    fetchMeMock.mockRejectedValue(serverError());
    const controller = new AbortController();
    const result = fetchMeWithRetry(controller.signal).catch((error: unknown) => error);

    await vi.advanceTimersByTimeAsync(500);
    controller.abort();
    await vi.advanceTimersByTimeAsync(10_000);

    expect(await result).toBe(controller.signal.reason);
    expect(fetchMeMock).toHaveBeenCalledTimes(1);
  });
});
