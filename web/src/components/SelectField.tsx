import { useId } from 'react';
import type { ComponentPropsWithRef } from 'react';
import { Icon } from './icons';

export type SelectOption = {
  value: string;
  label: string;
};

type SelectFieldProps = Omit<ComponentPropsWithRef<'select'>, 'children'> & {
  label: string;
  options: ReadonlyArray<SelectOption>;
  error?: string;
  hint?: string;
  // 라벨을 화면에서만 감춘다. 스크린 리더는 그대로 읽는다. 묶음 제목이 따로 있을 때 쓴다.
  hideLabel?: boolean;
  // 메시지 없이 오류 모양(빨간 테두리, aria-invalid)만 낸다. 메시지를 묶음 아래 한 번만 보여 줄 때 쓴다.
  invalid?: boolean;
};

// 입력란 글자는 16px 아래로 내리지 않는다. iOS가 작은 입력란에 포커스하면 화면을 확대하기 때문이다.
// 브라우저 기본 모양의 선택란은 최소 높이를 무시해서 옆 입력란보다 낮게 그려진다. 그래서 기본 모양을 끄고(appearance-none)
// 높이를 입력란과 같은 44px로 고정한 뒤, 펼침 화살표는 아이콘으로 직접 그린다.
const SELECT_BASE =
  'h-11 w-full appearance-none rounded-control border-[1.5px] bg-surface pr-10 pl-3 text-base text-ink focus:border-ink disabled:cursor-not-allowed disabled:bg-surface disabled:text-ink-subtle';

export function SelectField({
  label,
  options,
  error,
  hint,
  hideLabel,
  invalid,
  id,
  className,
  'aria-describedby': describedBy,
  ...rest
}: SelectFieldProps) {
  const generatedId = useId();
  const selectId = id ?? generatedId;
  const hintId = `${selectId}-hint`;
  const errorId = `${selectId}-error`;

  const describedByIds =
    [describedBy, hint ? hintId : undefined, error ? errorId : undefined].filter(Boolean).join(' ') || undefined;

  return (
    <div className={['flex flex-col gap-1', className ?? ''].filter(Boolean).join(' ')}>
      <label htmlFor={selectId} className={hideLabel ? 'sr-only' : 'text-sm font-medium text-ink'}>
        {label}
      </label>
      <div className="relative">
        <select
          id={selectId}
          aria-invalid={error || invalid ? true : undefined}
          aria-describedby={describedByIds}
          className={`${SELECT_BASE} ${error || invalid ? 'border-danger' : 'border-ink-subtle'}`}
          {...rest}
        >
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <Icon
          name="chevronRight"
          size={18}
          className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 rotate-90 text-ink-muted"
        />
      </div>
      {hint && (
        <p id={hintId} className="text-sm text-ink-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="flex items-start gap-1 text-sm text-danger">
          <Icon name="alert" size={16} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </p>
      )}
    </div>
  );
}
