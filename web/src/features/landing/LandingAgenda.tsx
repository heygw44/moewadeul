import { useEffect, useMemo, useState } from 'react';
import { listPrograms } from '../../api/programs';
import type { ProgramSummary } from '../../api/types';
import { Link } from '../../app/router';
import { buttonClassName } from '../../components/buttonClass';
import { formatKstTime, kstDateBlock } from '../../lib/datetime';
import { feeText, seatsText } from '../program/programLabels';

// 날짜 탭에 보여 줄 날의 수다.
const MAX_DAYS = 4;

// 신청 중이거나 곧 열릴 실제 공식 행사를 날짜 탭과 띠 행으로 보여 준다. 행사가 없으면 안내 한 줄만 둔다.
// 하나가 실패해도 다른 하나는 보여 주고, 둘 다 실패하면 행사가 없을 때와 같은 안내를 둔다.
export function LandingAgenda() {
  const [programs, setPrograms] = useState<ProgramSummary[] | null>(null);
  const [day, setDay] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    const load = (status: 'OPEN' | 'UPCOMING') =>
      listPrograms(status, 0, controller.signal).then(
        (page) => page.content,
        () => [] as ProgramSummary[],
      );
    Promise.all([load('OPEN'), load('UPCOMING')]).then(([open, upcoming]) => {
      if (controller.signal.aborted) return;
      setPrograms([...open, ...upcoming].sort((a, b) => a.startAt.localeCompare(b.startAt)));
    });
    return () => controller.abort();
  }, []);

  // 한국 날짜(YYYY-MM-DD)로 묶는다
  const days = useMemo(() => {
    const groups = new Map<string, ProgramSummary[]>();
    for (const program of programs ?? []) {
      const key = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date(program.startAt));
      groups.set(key, [...(groups.get(key) ?? []), program]);
    }
    return [...groups.entries()].slice(0, MAX_DAYS);
  }, [programs]);

  const selected = days.find(([key]) => key === day) ?? days[0];

  return (
    <section aria-labelledby="landing-agenda" className="mx-auto max-w-screen-xl px-4 py-24 lg:px-10 lg:py-36">
      <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
        <h2 id="landing-agenda" data-reveal className="text-[36px] leading-[1.08] font-extrabold tracking-[-0.04em] text-ink lg:text-[clamp(36px,4vw,60px)]">
          <span className="block text-ink-muted">곧 열리는</span>
          공식 행사
        </h2>
        <Link to="/programs" className={buttonClassName('primary')}>
          공식 행사 보기
        </Link>
      </div>

      {programs !== null && days.length === 0 && (
        <p className="mt-10 rounded-control bg-surface px-5 py-6 text-lg text-ink">곧 열릴 행사가 없어요. 새 행사가 열리면 공식 행사 목록에 올라와요.</p>
      )}

      {selected && (
        <div className="mt-10 grid gap-4 lg:mt-14 lg:grid-cols-[150px_1fr] lg:gap-10">
          <div role="tablist" aria-label="행사 날짜" className="grid auto-cols-fr grid-flow-col gap-1.5 overflow-x-auto lg:auto-rows-[64px] lg:grid-flow-row lg:content-start">
            {days.map(([key, items]) => {
              const on = key === selected[0];
              const block = kstDateBlock(items[0]!.startAt);
              return (
                <button
                  key={key}
                  type="button"
                  role="tab"
                  aria-selected={on}
                  onClick={() => setDay(key)}
                  className={`grid min-h-16 content-center rounded-control px-4 text-left transition-colors ${on ? 'bg-ink text-surface' : 'bg-fill text-ink hover:bg-fill-strong'}`}
                >
                  <b className="text-base">{block.weekday}요일</b>
                  <span className={`text-sm tabular-nums ${on ? 'text-brand-ink' : 'text-ink-muted'}`}>
                    {block.month} {block.day}일
                  </span>
                </button>
              );
            })}
          </div>
          <ul className="flex flex-col gap-1">
            {selected[1].map((program, index) => (
              <li key={program.programId}>
                <Link
                  to={`/programs/${program.programId}`}
                  className={`grid min-h-20 items-center gap-1 rounded-control px-4 py-3 transition-colors hover:bg-surface lg:grid-cols-[110px_1fr_auto] lg:gap-5 lg:px-5 ${index % 2 === 1 ? 'bg-fill' : 'bg-surface'}`}
                >
                  <span className="text-xl font-bold tabular-nums">{formatKstTime(program.startAt)}</span>
                  <span>
                    <b className="block text-lg font-bold">{program.title}</b>
                    <span className="text-sm text-ink-muted">
                      {program.locationText} · {program.overnight ? '1박 이상 · 본인확인한 성인만' : '당일'}
                    </span>
                  </span>
                  <span className="text-[15px] tabular-nums lg:text-right">
                    <b className="block text-[17px]">
                      {program.status === 'CLOSED' ? '마감' : `남은 자리 ${seatsText(program.remainingSeats, program.capacity)}`}
                    </b>
                    {feeText(program.fee)}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
