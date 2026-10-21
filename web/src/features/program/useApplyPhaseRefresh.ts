import { useEffect, useRef } from 'react';
import { serverNow } from '../../api/client';
import type { ProgramDetail } from '../../api/types';

// setTimeout이 받는 가장 긴 지연(약 24.8일)이다. 이보다 먼 시각에는 타이머를 걸지 않고, 탭이 다시 보일 때만 확인한다.
const MAX_TIMEOUT_MS = 2_147_483_647;
// 신청이 열리는 순간 상세를 열어 둔 사람들이 서버를 한꺼번에 부르지 않도록, 다시 받는 시각을 이 폭 안에서 흩뜨린다.
const SPREAD_MS = 500;
// 시각이 지났는데 서버가 아직 이전 단계를 주면(브라우저 시계 오차) 이 간격으로 다시 받되, 정한 횟수에서 멈춘다.
const RETRY_MS = 1_000;
const MAX_RETRIES = 5;

// 서버는 상세를 읽는 순간의 시각으로 단계(신청 예정·신청 중·마감)를 정해서 준다.
// 그래서 다음 단계로 넘어가는 시각을 화면이 알고, 그 시각이 지나면 상세를 다시 받는다.
function nextPhaseAt(detail: ProgramDetail): number | null {
  if (detail.status === 'UPCOMING') return Date.parse(detail.applyOpenAt);
  if (detail.status === 'OPEN') return Date.parse(detail.applyCloseAt);
  return null;
}

// 신청 시작·마감 시각이 지나면 reload를 불러 상세를 다시 받는다. 탭이 숨겨져 타이머가 늦어져도, 탭이 다시 보일 때 시각을 확인한다.
// 시각은 서버 응답의 Date 헤더로 보정한 값(serverNow)으로 센다. 이 값은 서버보다 늦을 수는 있어도 빠르지는 않다.
// reload는 렌더링마다 바뀌지 않는 함수여야 한다. 바뀌면 effect가 다시 돌아 재시도 횟수가 어긋난다.
export function useApplyPhaseRefresh(detail: ProgramDetail | null, reload: () => void): void {
  // 같은 시각을 두고 다시 받은 횟수다. reload가 새 detail을 가져와 effect가 다시 돌 때마다 센다.
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
      // 시각이 지난 뒤 다시 받았는데도 단계가 그대로다.
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
