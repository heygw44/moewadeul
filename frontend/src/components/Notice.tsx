import type { ReactNode } from 'react';
import type { IconName } from './iconPaths';
import { Icon } from './icons';

type NoticeTone = 'warning' | 'danger' | 'info';

type NoticeProps = {
  tone: NoticeTone;
  title: string;
  icon?: IconName;
  children?: ReactNode;
};

const BOX_CLASSES: Record<NoticeTone, string> = {
  warning: 'bg-warning-soft',
  danger: 'bg-danger-soft',
  info: 'bg-info-soft',
};

const TITLE_CLASSES: Record<NoticeTone, string> = {
  warning: 'text-warning',
  danger: 'text-danger',
  info: 'text-info',
};

const ICON_CLASSES: Record<NoticeTone, string> = {
  warning: 'text-warning-line',
  danger: 'text-danger',
  info: 'text-info',
};

const DEFAULT_ICONS: Record<NoticeTone, IconName> = {
  warning: 'alert',
  danger: 'alert',
  info: 'info',
};

export function Notice({ tone, title, icon, children }: NoticeProps) {
  return (
    <div role="note" className={`flex gap-3 rounded-sheet p-5 ${BOX_CLASSES[tone]}`}>
      <Icon name={icon ?? DEFAULT_ICONS[tone]} size={20} className={`mt-0.5 shrink-0 ${ICON_CLASSES[tone]}`} />
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <p className={`text-[1.0625rem] font-bold ${TITLE_CLASSES[tone]}`}>{title}</p>
        {children !== undefined && children !== null && <div className="text-sm text-ink">{children}</div>}
      </div>
    </div>
  );
}
