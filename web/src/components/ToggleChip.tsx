import type { ReactNode } from 'react';
import { Icon } from './icons';

type ToggleChipProps = {
  pressed: boolean;
  onToggle: () => void;
  children: ReactNode;
};

// Tailwind가 소스 글자를 읽어 클래스를 만들기 때문에 클래스 이름을 이어 붙이지 않고 통째로 적는다.
// 쉬는 칩은 세이지 알약이고, 고른 칩은 먹색 알약으로 뒤집어 한눈에 고른 칸이 보이게 한다.
const PRESSED =
  'inline-flex min-h-11 items-center gap-2 rounded-control bg-ink px-4 text-base font-semibold text-white';
const IDLE =
  'inline-flex min-h-11 items-center gap-2 rounded-control bg-fill px-4 text-base text-ink hover:bg-fill-strong';

// 여러 개를 함께 고를 수 있는 칩이다. 고른 칩은 색만으로 구분되지 않게 체크 아이콘을 함께 보여 준다.
export function ToggleChip({ pressed, onToggle, children }: ToggleChipProps) {
  return (
    <button type="button" aria-pressed={pressed} onClick={onToggle} className={pressed ? PRESSED : IDLE}>
      {pressed && <Icon name="check" size={16} className="shrink-0" />}
      {children}
    </button>
  );
}
