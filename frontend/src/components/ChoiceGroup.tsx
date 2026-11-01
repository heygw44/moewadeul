import { useId } from 'react';
import { Icon } from './icons';

export type ChoiceOption<T extends string> = {
  value: T;
  label: string;
};

type ChoiceGroupProps<T extends string> = {
  legend: string;
  name: string;
  options: ReadonlyArray<ChoiceOption<T>>;
  value: T | null;
  onChange: (value: T) => void;
  error?: string;
  hint?: string;
  disabled?: boolean;
};

const OPTION_SELECTED =
  'inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-control border-[1.5px] border-ink bg-surface px-4 text-base font-semibold text-ink';
const OPTION_IDLE =
  'inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-control border-[1.5px] border-fill bg-fill px-4 text-base text-ink hover:border-fill-strong hover:bg-fill-strong';

export function ChoiceGroup<T extends string>({
  legend,
  name,
  options,
  value,
  onChange,
  error,
  hint,
  disabled = false,
}: ChoiceGroupProps<T>) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : undefined, error ? errorId : undefined].filter(Boolean).join(' ') || undefined;

  return (
    <fieldset aria-describedby={describedBy} className="flex min-w-0 flex-col gap-1">
      <legend className="mb-1 text-sm font-medium text-ink">{legend}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((option) => (
          <label key={option.value} className={value === option.value ? OPTION_SELECTED : OPTION_IDLE}>
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={value === option.value}
              disabled={disabled}
              aria-invalid={error ? true : undefined}
              onChange={() => onChange(option.value)}
              className="size-5 accent-ink"
            />
            {option.label}
          </label>
        ))}
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
    </fieldset>
  );
}
