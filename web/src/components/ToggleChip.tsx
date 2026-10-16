import type { ReactNode } from 'react';
import { Icon } from './icons';

type ToggleChipProps = {
  pressed: boolean;
  onToggle: () => void;
  children: ReactNode;
};

// Tailwind가 소스 글자를 읽어 클래스를 만들기 때문에 클래스 이름을 이어 붙이지 않고 통째로 적는다.
// 고른 칩은 먹색으로 뒤집는다. 안내판의 지금 위치 표시처럼 한눈에 고른 칸이 보인다.
const PRESSED =
  'inline-flex min-h-11 items-center gap-2 rounded-control border border-ink bg-ink px-3 text-base font-bold text-white';
const IDLE =
  'inline-flex min-h-11 items-center gap-2 rounded-control border border-line bg-surface px-3 text-base text-ink hover:bg-fill';

// 여러 개를 함께 고를 수 있는 칩이다. 고른 칩은 색만으로 구분되지 않게 체크 아이콘을 함께 보여 준다.
export function ToggleChip({ pressed, onToggle, children }: ToggleChipProps) {
  return (
    <button type="button" aria-pressed={pressed} onClick={onToggle} className={pressed ? PRESSED : IDLE}>
      {pressed && <Icon name="check" size={16} className="shrink-0" />}
      {children}
    </button>
  );
}
