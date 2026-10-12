import { useId } from 'react';
import type { ButtonHTMLAttributes } from 'react';
import { BASE_CLASSES, DISABLED_CLASSES, HOVER_CLASSES, VARIANT_CLASSES } from './buttonClass';
import type { ButtonVariant } from './buttonClass';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  loading?: boolean;
  disabledReason?: string;
  fullWidth?: boolean;
};

export function Button({
  variant = 'primary',
  loading = false,
  disabledReason,
  fullWidth = false,
  disabled = false,
  type = 'button',
  className,
  children,
  'aria-describedby': describedBy,
  ...rest
}: ButtonProps) {
  const reasonId = useId();
  const showReason = disabled && !loading && Boolean(disabledReason);

  // 로딩 중에도 원래 색을 유지해서 무엇을 처리하는지 알아볼 수 있게 하고, 클릭만 막는다.
  const stateClasses = disabled
    ? DISABLED_CLASSES
    : loading
      ? `${VARIANT_CLASSES[variant]} cursor-progress`
      : [VARIANT_CLASSES[variant], HOVER_CLASSES[variant]].filter(Boolean).join(' ');

  const describedByIds = [describedBy, showReason ? reasonId : undefined].filter(Boolean).join(' ') || undefined;

  const button = (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      aria-describedby={describedByIds}
      className={[BASE_CLASSES, stateClasses, fullWidth ? 'w-full' : '', className ?? ''].filter(Boolean).join(' ')}
      {...rest}
    >
      {loading && <Spinner />}
      {children}
    </button>
  );

  if (!disabledReason) return button;

  return (
    <div className={fullWidth ? 'flex w-full flex-col gap-1' : 'inline-flex flex-col gap-1'}>
      {button}
      {showReason && (
        <p id={reasonId} className="text-sm text-ink-muted">
          {disabledReason}
        </p>
      )}
    </div>
  );
}

// 둥근 테두리 클래스를 쓰지 않으려고 원 모양은 SVG 호로 그린다. 색은 버튼 글자색을 따른다.
function Spinner() {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      viewBox="0 0 24 24"
      width={16}
      height={16}
      fill="none"
      stroke="currentColor"
      strokeWidth={3}
      className="shrink-0 animate-spin motion-reduce:animate-none"
    >
      <circle cx="12" cy="12" r="9" opacity="0.3" />
      <path d="M21 12a9 9 0 0 0-9-9" strokeLinecap="round" />
    </svg>
  );
}
