import { Icon } from './icons';

export type ActiveFilterChip = {
  key: string;
  label: string;
  onRemove: () => void;
};

type ActiveFilterChipsProps = {
  chips: ReadonlyArray<ActiveFilterChip>;
  onReset: () => void;
  className?: string;
};

const CHIP =
  'pointer-events-auto inline-flex min-h-11 shrink-0 items-center gap-1 rounded-control bg-ink pr-3 pl-4 text-sm font-semibold text-white hover:bg-ink/85';
const RESET =
  'pointer-events-auto inline-flex min-h-11 shrink-0 items-center rounded-control bg-fill px-4 text-sm font-semibold text-ink hover:bg-fill-strong';

export function ActiveFilterChips({ chips, onReset, className }: ActiveFilterChipsProps) {
  if (chips.length === 0) return null;
  return (
    <ul
      aria-label="적용한 조건"
      className={['pointer-events-auto flex w-fit max-w-full gap-2 overflow-x-auto', className ?? '']
        .filter(Boolean)
        .join(' ')}
    >
      {chips.map((chip) => (
        <li key={chip.key} className="shrink-0">
          <button type="button" aria-label={`${chip.label} 조건 빼기`} onClick={chip.onRemove} className={CHIP}>
            {chip.label}
            <Icon name="close" size={16} className="shrink-0" />
          </button>
        </li>
      ))}
      {chips.length > 1 && (
        <li className="shrink-0">
          <button type="button" onClick={onReset} className={RESET}>
            모두 지우기
          </button>
        </li>
      )}
    </ul>
  );
}
