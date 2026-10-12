import type { ReactNode } from 'react';
import type { IconName } from './iconPaths';
import { Icon } from './icons';

export type StatusTone = 'neutral' | 'closed' | 'forest' | 'sea' | 'earth' | 'warning' | 'danger';

const TONE_CLASSES: Record<StatusTone, string> = {
  neutral: 'text-ink-muted',
  closed: 'text-ink-muted',
  forest: 'text-forest-deep',
  sea: 'text-sea',
  earth: 'text-earth-strong',
  warning: 'text-warning',
  danger: 'text-danger',
};

// 상태를 면을 칠한 알약 대신 색 글자로 적는다. 판단에 필요한 상태만 쓰고, 여러 개는 MetaLine으로 잇는다.
// 아이콘은 공원 경계 경고·휴장·본인확인처럼 색만으로 구분하면 안 되는 상태에만 붙인다.
export function StatusText({ tone, icon, children }: { tone: StatusTone; icon?: IconName; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center gap-1 font-semibold ${TONE_CLASSES[tone]}`}>
      {icon && <Icon name={icon} size={16} className="shrink-0" />}
      {children}
    </span>
  );
}

// 제목 아래 한 줄에 상태와 정보를 가운뎃점으로 잇는다. 빈 값(false, null)은 건너뛴다.
export function MetaLine({ items: raw, className }: { items: ReactNode[]; className?: string }) {
  const items = raw.filter((child) => child !== null && child !== undefined && child !== false && child !== '');
  if (items.length === 0) return null;
  return (
    <span className={['flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-sm text-ink-muted', className ?? ''].join(' ')}>
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
