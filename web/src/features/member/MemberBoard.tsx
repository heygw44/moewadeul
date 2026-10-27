import type { ReactNode } from 'react';
import { trustLevelCaption } from './memberLabels';

type MemberBoardProps = {
  nickname: string;
  trustLevel?: number;
  caption?: string;
  aside?: ReactNode;
};

export function MemberBoard({ nickname, trustLevel, caption, aside }: MemberBoardProps) {
  return (
    <section
      aria-label="회원 정보"
      className="flex flex-wrap items-center gap-x-4 gap-y-3 rounded-sheet bg-fill px-5 py-5 text-ink sm:px-6"
    >
      {trustLevel !== undefined && (
        <span
          aria-hidden="true"
          className="flex size-14 shrink-0 items-center justify-center rounded-full bg-brand text-[1.75rem] font-extrabold tabular-nums text-white sm:size-16 sm:text-[1.875rem]"
        >
          {trustLevel}
        </span>
      )}
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <h2 className="break-words text-2xl leading-tight font-extrabold tracking-[-0.03em] sm:text-[1.75rem]">
          {nickname}
        </h2>
        {trustLevel !== undefined && (
          <p className="text-sm text-ink-muted">
            신뢰 단계 {trustLevel} · {caption ?? trustLevelCaption(trustLevel)}
          </p>
        )}
      </div>
      {aside && <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">{aside}</div>}
    </section>
  );
}

export function MemberBoardTag({ children }: { children: ReactNode }) {
  return (
    <span className="inline-flex min-h-9 items-center gap-1.5 rounded-control bg-surface px-3 text-sm font-bold text-ink">
      {children}
    </span>
  );
}

export const MEMBER_BOARD_LINK_CLASS =
  'inline-flex min-h-11 items-center gap-1.5 rounded-control bg-surface px-4 text-sm font-bold text-ink hover:bg-fill-strong';
