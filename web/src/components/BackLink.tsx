import type { ReactNode } from 'react';
import { Link } from '../app/router';
import { Icon } from './icons';

export function BackLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="inline-flex min-h-11 items-center gap-1 text-accent underline underline-offset-2">
      <Icon name="chevronLeft" size={16} />
      {children}
    </Link>
  );
}
