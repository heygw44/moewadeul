import { useEffect, useRef } from 'react';
import { serverNow } from '../../api/client';
import type { ProgramDetail } from '../../api/types';

// setTimeout이 받는 가장 긴 지연(약 24.8일)이다. 이보다 먼 시각에는 타이머를 걸지 않고, 탭이 다시 보일 때만 확인한다.
const MAX_TIMEOUT_MS = 2_147_483_647;
const SPREAD_MS = 500;
const RETRY_MS = 1_000;
const MAX_RETRIES = 5;

function nextPhaseAt(detail: ProgramDetail): number | null {
  if (detail.status === 'UPCOMING') return Date.parse(detail.applyOpenAt);
  if (detail.status === 'OPEN') return Date.parse(detail.applyCloseAt);
  return null;
}

export function useApplyPhaseRefresh(detail: ProgramDetail | null, reload: () => void): void {
  const retries = useRef<{ at: number; count: number } | null>(null);

  useEffect(() => {
    if (detail === null) return;
    const phaseAt = nextPhaseAt(detail);
    if (phaseAt === null) return;
    const at: number = phaseAt;

    const delay = at - serverNow();
    let timer: number | undefined;
    if (delay > 0) {
      retries.current = null;
      if (delay < MAX_TIMEOUT_MS) timer = window.setTimeout(reload, delay + Math.random() * SPREAD_MS);
    } else {
      const count = retries.current?.at === at ? retries.current.count + 1 : 1;
      retries.current = { at, count };
      if (count <= MAX_RETRIES) timer = window.setTimeout(reload, RETRY_MS);
    }

    function handleVisibility() {
      if (document.visibilityState === 'visible' && serverNow() >= at) reload();
    }
    document.addEventListener('visibilitychange', handleVisibility);
    return () => {
      window.clearTimeout(timer);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [detail, reload]);
}
