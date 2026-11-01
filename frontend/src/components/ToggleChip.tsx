import type { ReactNode } from 'react';
import { Icon } from './icons';

type ToggleChipProps = {
  pressed: boolean;
  onToggle: () => void;
  children: ReactNode;
};

const PRESSED =
  'inline-flex min-h-11 items-center gap-2 rounded-control bg-ink px-4 text-base font-semibold text-white';
const IDLE =
  'inline-flex min-h-11 items-center gap-2 rounded-control bg-fill px-4 text-base text-ink hover:bg-fill-strong';

export function ToggleChip({ pressed, onToggle, children }: ToggleChipProps) {
  return (
    <button type="button" aria-pressed={pressed} onClick={onToggle} className={pressed ? PRESSED : IDLE}>
      {pressed && <Icon name="check" size={16} className="shrink-0" />}
      {children}
    </button>
  );
}
