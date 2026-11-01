import { useId } from 'react';
import type { ComponentPropsWithRef, ReactNode } from 'react';
import { Icon } from './icons';

type TextFieldProps = ComponentPropsWithRef<'input'> & {
  label: string;
  error?: string;
  hint?: string;
  hideLabel?: boolean;
  invalid?: boolean;
  trailing?: ReactNode;
};

// 입력란 글자는 16px 아래로 내리지 않는다. iOS가 작은 입력란에 포커스하면 화면을 확대하기 때문이다.
const INPUT_BASE =
  'min-h-12 w-full rounded-field border-[1.5px] bg-surface px-4 text-base text-ink focus:border-brand disabled:cursor-not-allowed disabled:bg-surface disabled:text-ink-subtle';

export function TextField({
  label,
  error,
  hint,
  hideLabel,
  invalid,
  trailing,
  id,
  className,
  'aria-describedby': describedBy,
  ...rest
}: TextFieldProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const hintId = `${inputId}-hint`;
  const errorId = `${inputId}-error`;

  const describedByIds =
    [describedBy, hint ? hintId : undefined, error ? errorId : undefined].filter(Boolean).join(' ') || undefined;

  const input = (
    <input
      id={inputId}
      aria-invalid={error || invalid ? true : undefined}
      aria-describedby={describedByIds}
      className={`${INPUT_BASE}${trailing ? ' pr-12' : ''} ${error || invalid ? 'border-danger' : 'border-ink-faint'}`}
      {...rest}
    />
  );

  return (
    <div className={['flex flex-col gap-1', className ?? ''].filter(Boolean).join(' ')}>
      <label htmlFor={inputId} className={hideLabel ? 'sr-only' : 'text-sm font-medium text-ink'}>
        {label}
      </label>
      {trailing ? (
        <div className="relative">
          {input}
          <div className="absolute inset-y-0 right-0 flex items-center">{trailing}</div>
        </div>
      ) : (
        input
      )}
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
