import type { ReactNode } from 'react';
import { Link } from '../app/router';

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
