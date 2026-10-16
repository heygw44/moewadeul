import type { ReactNode } from 'react';
import { trustLevelCaption } from './memberLabels';

type MemberBoardProps = {
  nickname: string;
  // 비회원이 보는 프로필에는 신뢰 단계가 없다. 그때는 번호 원과 설명 줄을 그리지 않는다.
  trustLevel?: number;
  // 설명 줄을 단계 설명 대신 다른 말로 쓸 때 넘긴다(이메일 인증 전 회원 등).
  caption?: string;
  // 판 오른쪽에 두는 표시나 링크다(본인확인됨, 기록 지도 등).
  aside?: ReactNode;
};

// 회원 한 사람을 소개하는 숲색 판이다. 탐방로 안내판의 번호 원처럼 신뢰 단계를 큰 숫자로 먼저 보여 준다.
export function MemberBoard({ nickname, trustLevel, caption, aside }: MemberBoardProps) {
  return (
    <section
      aria-label="회원 정보"
      className="flex flex-wrap items-center gap-x-4 gap-y-3 rounded-sheet bg-brand px-5 py-5 text-white sm:px-6 [&_:focus-visible]:outline-white"
    >
      {trustLevel !== undefined && (
        <span
          aria-hidden="true"
          className="flex size-14 shrink-0 items-center justify-center rounded-full bg-surface text-[1.75rem] font-extrabold tabular-nums text-brand sm:size-16 sm:text-[1.875rem]"
        >
          {trustLevel}
        </span>
      )}
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <h2 className="break-words text-2xl leading-tight font-extrabold tracking-[-0.03em] sm:text-[1.75rem]">
          {nickname}
        </h2>
        {trustLevel !== undefined && (
          <p className="text-sm text-brand-ink">
            신뢰 단계 {trustLevel} · {caption ?? trustLevelCaption(trustLevel)}
          </p>
        )}
      </div>
      {aside && <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">{aside}</div>}
    </section>
  );
}

// 판 위에 얹는 표시다. 숲색 판 위에서 흰 글자와 옅은 흰 선으로 칸을 나눈다.
export function MemberBoardTag({ children }: { children: ReactNode }) {
  return (
    <span className="inline-flex min-h-9 items-center gap-1.5 rounded-control border border-brand-ink/40 px-3 text-sm font-bold">
      {children}
    </span>
  );
}

// 숲색 판 위에 두는 링크 모양이다.
export const MEMBER_BOARD_LINK_CLASS =
  'inline-flex min-h-11 items-center gap-1.5 rounded-control border border-brand-ink/40 px-3 text-sm font-bold text-white hover:bg-brand-raised';
