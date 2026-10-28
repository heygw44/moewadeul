import type { ReactNode } from 'react';
import type { IconName } from './iconPaths';
import { Icon } from './icons';

export type StatusTone = 'neutral' | 'closed' | 'accent' | 'info' | 'bakji' | 'warning' | 'danger';

const TONE_CLASSES: Record<StatusTone, string> = {
  neutral: 'text-ink-muted',
  closed: 'text-ink-muted',
  accent: 'text-accent',
  info: 'text-info',
  bakji: 'text-bakji-strong',
  warning: 'text-warning',
  danger: 'text-danger',
};

export function StatusText({ tone, icon, children }: { tone: StatusTone; icon?: IconName; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center gap-1 font-semibold ${TONE_CLASSES[tone]}`}>
      {icon && <Icon name={icon} size={16} className="shrink-0" />}
      {children}
    </span>
  );
}

export function MetaLine({ items: raw, className }: { items: ReactNode[]; className?: string }) {
  const items = raw.filter((child) => child !== null && child !== undefined && child !== false && child !== '');
  if (items.length === 0) return null;
  return (
    <span
      className={['flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-sm text-ink-muted', className ?? ''].join(' ')}
    >
      {items.map((item, index) => (
        <span key={index} className="inline-flex items-center gap-1.5">
          {index > 0 && (
            <span aria-hidden="true" className="text-ink-subtle">
              ·
            </span>
          )}
          {item}
        </span>
      ))}
    </span>
  );
}
