import { useEffect, useState } from 'react';
import { serverNow } from '../../api/client';
import { listPrograms } from '../../api/programs';
import type { ProgramSummary } from '../../api/types';

// 화면을 옮길 때마다 알림 띠가 새로 그려지므로, 목록 요청을 한 번 받아 두고 이 시간 동안 다시 쓴다.
const CACHE_MS = 5 * 60 * 1000;
// setTimeout이 받는 가장 긴 지연(약 24.8일)이다. 이보다 먼 시각에는 타이머를 걸지 않는다.
const MAX_TIMEOUT_MS = 2_147_483_647;
// 신청이 열리는 순간 띠를 보고 있던 사람들이 서버를 한꺼번에 부르지 않도록, 다시 받는 시각을 이 폭 안에서 흩뜨린다.
const SPREAD_MS = 500;
// 기준 시각이 이미 지났는데 서버가 같은 시각을 또 주더라도 요청이 연달아 나가지 않게, 다시 받는 간격은 이보다 짧게 하지 않는다.
const MIN_RELOAD_MS = 1000;

// program은 띠에 알릴 행사이고, changeAt은 그 알림이 달라질 수 있는 가장 가까운 시각(밀리초)이다. 없으면 null이다.
// 달라지는 때는 띠에 알린 행사의 신청이 마감될 때, 또는 신청 예정이던 행사가 열릴 때다.
type Announcement = { program: ProgramSummary | null; changeAt: number | null };

let cached: { at: number; changeAt: number | null; promise: Promise<Announcement> } | null = null;

// 신청 중인 행사 가운데 자리가 남아 있고 신청 마감 전인 첫 행사를 고른다. 서버는 시작일이 빠른 순서로 준다.
async function fetchAnnouncement(): Promise<Announcement> {
  try {
    const page = await listPrograms('OPEN', 0);
    const now = serverNow();
    const program = page.content.find((item) => item.remainingSeats > 0 && Date.parse(item.applyCloseAt) > now) ?? null;
    const times = [
      program ? Date.parse(program.applyCloseAt) : null,
      page.nextApplyOpenAt ? Date.parse(page.nextApplyOpenAt) : null,
    ].filter((time): time is number => time !== null && !Number.isNaN(time));
    return { program, changeAt: times.length > 0 ? Math.min(...times) : null };
  } catch {
    // 알림 띠는 덧붙이는 안내라서, 못 받아 오면 띠를 그리지 않을 뿐 사용자에게 오류를 보이지 않는다.
    return { program: null, changeAt: null };
  }
}

// 받은 지 오래됐거나, 알릴 내용이 달라질 시각이 지났으면 다시 받는다.
function loadAnnouncement(): Promise<Announcement> {
  const now = serverNow();
  const stale = cached !== null && (now - cached.at > CACHE_MS || (cached.changeAt !== null && now >= cached.changeAt));
  if (!cached || stale) {
    const entry = { at: now, changeAt: null as number | null, promise: fetchAnnouncement() };
    void entry.promise.then((result) => {
      entry.changeAt = result.changeAt;
    });
    cached = entry;
  }
  return cached.promise;
}

export function useOpenProgramAnnouncement(): ProgramSummary | null {
  const [program, setProgram] = useState<ProgramSummary | null>(null);

  useEffect(() => {
    let active = true;
    let timer: number | undefined;

    // 알릴 내용이 달라지는 시각에 한 번만 다시 받는다. 주기적으로 묻지는 않는다.
    function load() {
      void loadAnnouncement().then((next) => {
        if (!active) return;
        setProgram(next.program);
        if (next.changeAt === null) return;
        const delay = next.changeAt - serverNow();
        if (delay < MAX_TIMEOUT_MS) {
          timer = window.setTimeout(load, Math.max(delay, MIN_RELOAD_MS) + Math.random() * SPREAD_MS);
        }
      });
    }

    load();
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, []);

  return program;
}
