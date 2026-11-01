import { describe, expect, it } from 'vitest';
import { claimReload, MIN_RELOAD_GAP_MS } from './staleChunkReload';

function memoryStorage(): Pick<Storage, 'getItem' | 'setItem'> {
  const data = new Map<string, string>();
  return {
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => void data.set(key, value),
  };
}

describe('claimReload', () => {
  it('처음에는 새로고침을 허락하고 시각을 남긴다', () => {
    expect(claimReload(memoryStorage(), 1_000_000)).toBe(true);
  });

  it('방금 새로고침했는데도 같은 파일이 없으면 다시 새로고침하지 않는다', () => {
    const storage = memoryStorage();
    claimReload(storage, 1_000_000);

    expect(claimReload(storage, 1_000_000 + MIN_RELOAD_GAP_MS - 1)).toBe(false);
  });

  it('간격이 지나면 다시 허락한다', () => {
    const storage = memoryStorage();
    claimReload(storage, 1_000_000);

    expect(claimReload(storage, 1_000_000 + MIN_RELOAD_GAP_MS)).toBe(true);
  });

  it('시각을 적지 못하는 저장소에서는 새로고침 반복을 막으려고 허락하지 않는다', () => {
    const blocked: Pick<Storage, 'getItem' | 'setItem'> = {
      getItem: () => null,
      setItem: () => {
        throw new DOMException('quota', 'QuotaExceededError');
      },
    };

    expect(claimReload(blocked, 1_000_000)).toBe(false);
  });
});
