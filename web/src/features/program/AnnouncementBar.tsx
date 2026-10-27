import { useState } from 'react';
import { Link, useLocation } from '../../app/router';
import { Icon } from '../../components/icons';
import { readStored, writeStored } from '../../lib/storage';
import { useOpenProgramAnnouncement } from './useOpenProgramAnnouncement';

const DISMISSED_KEY = 'moewadeul.dismissedProgramId';

export function AnnouncementBar() {
  const program = useOpenProgramAnnouncement();
  const { pathname } = useLocation();
  const [dismissedId, setDismissedId] = useState(() => readStored(DISMISSED_KEY));

  if (!program) return null;
  const id = String(program.programId);
  if (dismissedId === id || pathname === `/programs/${id}`) return null;

  function dismiss() {
    writeStored(DISMISSED_KEY, id);
    setDismissedId(id);
  }

  return (
    <div className="shrink-0 bg-brand text-white [&_:focus-visible]:outline-white">
      <div className="mx-auto flex max-w-screen-xl items-center gap-2 pl-4 pr-1">
        <Link
          to={`/programs/${id}`}
          className="flex min-h-11 min-w-0 flex-1 items-center gap-2 text-sm underline-offset-2 hover:underline"
        >
          <Icon name="calendar" size={18} className="shrink-0" />
          <span className="truncate">
            <span className="font-semibold">공식 행사</span> {program.title} 신청을 받고 있어요
          </span>
          <span className="shrink-0">
            남은 자리 <span className="font-mono tabular-nums">{program.remainingSeats}</span>
          </span>
        </Link>
        <button
          type="button"
          onClick={dismiss}
          aria-label="행사 알림 닫기"
          className="inline-flex size-11 shrink-0 items-center justify-center rounded-control hover:bg-white/15"
        >
          <Icon name="close" size={18} />
        </button>
      </div>
    </div>
  );
}
