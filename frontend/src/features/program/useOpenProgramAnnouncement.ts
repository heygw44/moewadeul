import { useEffect, useState } from 'react';
import { serverNow } from '../../api/client';
import { listPrograms } from '../../api/programs';
import type { ProgramSummary } from '../../api/types';

const CACHE_MS = 5 * 60 * 1000;
// setTimeout이 받는 가장 긴 지연(약 24.8일)이다. 이보다 먼 시각에는 타이머를 걸지 않는다.
const MAX_TIMEOUT_MS = 2_147_483_647;
const SPREAD_MS = 500;
const MIN_RELOAD_MS = 1000;

type Announcement = { program: ProgramSummary | null; changeAt: number | null };

let cached: { at: number; changeAt: number | null; promise: Promise<Announcement> } | null = null;

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
    return { program: null, changeAt: null };
  }
}

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
