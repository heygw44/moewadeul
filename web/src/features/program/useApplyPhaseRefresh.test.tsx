import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ProgramDetail } from '../../api/types';
import { useApplyPhaseRefresh } from './useApplyPhaseRefresh';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const NOW = Date.parse('2026-10-28T12:00:00Z');

function openUntil(closeAt: string): ProgramDetail {
  return { status: 'OPEN', applyCloseAt: closeAt } as ProgramDetail;
}

describe('useApplyPhaseRefresh', () => {
  let root: Root;
  let reloads: number;

  function Probe({ detail }: { detail: ProgramDetail }) {
    useApplyPhaseRefresh(detail, () => {
      reloads += 1;
    });
    return null;
  }

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    root = createRoot(document.createElement('div'));
    reloads = 0;
  });

  afterEach(() => {
    act(() => root.unmount());
    vi.useRealTimers();
  });

  it('렌더마다 새 reload 함수를 넘겨도 마감이 지난 뒤 1초 뒤에 한 번 다시 불러온다', async () => {
    const detail = openUntil('2026-10-28T11:59:59Z');
    for (let i = 0; i < 8; i += 1) {
      await act(async () => root.render(<Probe detail={detail} />));
    }

    await act(async () => vi.advanceTimersByTime(1_000));

    expect(reloads).toBe(1);
  });
});
