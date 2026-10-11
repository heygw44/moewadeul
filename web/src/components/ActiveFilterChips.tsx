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
  'pointer-events-auto inline-flex min-h-11 shrink-0 items-center gap-1 rounded-control border border-forest bg-forest-soft pr-2 pl-3 text-sm font-semibold text-ink hover:bg-paper-deep';
const RESET =
  'pointer-events-auto inline-flex min-h-11 shrink-0 items-center rounded-control border border-contour bg-card px-3 text-sm text-ink hover:bg-paper-deep';

// 켜진 조건을 한 줄로 보여 준다. 칩을 누르면 그 조건만 풀고, 줄이 넘치면 가로로 밀어서 본다.
// 지도 위에 띄우므로 줄 너비를 칩만큼으로 줄여서, 칩이 없는 빈자리는 지도를 끄는 손가락을 막지 않게 한다.
// 켜진 조건이 없으면 아무것도 그리지 않는다.
export function ActiveFilterChips({ chips, onReset, className }: ActiveFilterChipsProps) {
  if (chips.length === 0) return null;
  return (
    <ul
      aria-label="켜진 조건"
      className={['pointer-events-auto flex w-fit max-w-full gap-2 overflow-x-auto', className ?? '']
        .filter(Boolean)
        .join(' ')}
    >
      {chips.map((chip) => (
        <li key={chip.key} className="shrink-0">
          <button type="button" aria-label={`${chip.label} 조건 풀기`} onClick={chip.onRemove} className={CHIP}>
            {chip.label}
            <Icon name="close" size={16} className="shrink-0" />
          </button>
        </li>
      ))}
      {chips.length > 1 && (
        <li className="shrink-0">
          <button type="button" onClick={onReset} className={RESET}>
            모두 풀기
          </button>
        </li>
      )}
    </ul>
  );
}
