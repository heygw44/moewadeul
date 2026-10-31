import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchMyTrust } from '../../api/trust';
import type { MyTrust } from '../../api/types';
import { MyPage } from './MyPage';

vi.mock('../../api/trust', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/trust')>()),
  fetchMyTrust: vi.fn(),
}));

vi.mock('../../api/soloTrips', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/soloTrips')>()),
  fetchMySoloTrips: vi.fn(async () => []),
}));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => ({
    status: 'authenticated',
    me: {
      memberId: 1,
      email: 'a@b.c',
      nickname: '산사람',
      status: 'ACTIVE',
      role: 'USER',
      identityVerified: true,
      trustLevel: 1,
    },
    logout: vi.fn(),
  }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function trust(recentEarlyLeaves: { current: number; limit: number }): MyTrust {
  return {
    trustLevel: 1,
    identityVerified: true,
    nextLevel: {
      level: 2,
      completedCompanions: { current: 3, required: 3 },
      rejoinRate: { current: 100, required: 70 },
      recentEarlyLeaves,
      noRecentSanction: true,
    },
  };
}

describe('내 정보: 신뢰 단계 진행', () => {
  let container: HTMLDivElement;
  let root: Root;

  const row = (label: string) =>
    [...container.querySelectorAll('dl > div')].find((element) => element.textContent?.includes(label));

  beforeEach(() => {
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('최근 180일 임박 탈퇴 횟수가 한도에 닿았으면 몇 번 중 몇 번인지 한 줄로 보여 준다', async () => {
    vi.mocked(fetchMyTrust).mockResolvedValue(trust({ current: 3, limit: 3 }));

    await act(async () => root.render(<MyPage />));

    expect(row('최근 180일 임박 탈퇴')?.textContent).toContain('3 / 3회 미만');
  });

  it('임박 탈퇴 줄은 다시 동행 줄과 제재 줄 사이에 놓인다', async () => {
    vi.mocked(fetchMyTrust).mockResolvedValue(trust({ current: 0, limit: 3 }));

    await act(async () => root.render(<MyPage />));

    const labels = [...container.querySelectorAll('dl > div')].map((element) => element.textContent ?? '');
    const rejoin = labels.findIndex((text) => text.includes('다시 동행하고 싶어요'));
    const earlyLeave = labels.findIndex((text) => text.includes('최근 180일 임박 탈퇴'));
    const sanction = labels.findIndex((text) => text.includes('최근 180일 확정 제재'));
    expect(rejoin).toBeGreaterThanOrEqual(0);
    expect(earlyLeave).toBe(rejoin + 1);
    expect(sanction).toBe(earlyLeave + 1);
    expect(row('최근 180일 임박 탈퇴')?.textContent).toContain('0 / 3회 미만');
  });
});
