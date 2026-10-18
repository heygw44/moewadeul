import type { ReactNode } from 'react';
import { Link } from '../app/router';

// 제목 아래에 놓는 이동 링크 줄이다. UnderlineTabs와 같은 알약 트랙 모양이고, 현재 화면은 aria-current로 알린다.
export function UnderlineNav({ label, children }: { label: string; children: ReactNode }) {
  return (
    <nav aria-label={label} className="flex w-fit max-w-full gap-1 overflow-x-auto rounded-control bg-fill p-1">
      {children}
    </nav>
  );
}

export function UnderlineNavLink({ to, active, children }: { to: string; active: boolean; children: ReactNode }) {
  return (
    <Link
      to={to}
      aria-current={active ? 'page' : undefined}
      className={[
        'inline-flex min-h-10 items-center whitespace-nowrap rounded-control px-4 text-base font-semibold transition-colors',
        active ? 'bg-ink text-white' : 'text-ink-muted hover:text-ink',
      ].join(' ')}
    >
      {children}
    </Link>
  );
}
