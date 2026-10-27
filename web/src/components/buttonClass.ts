export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'danger-outline' | 'ghost' | 'signup';

export const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: 'bg-brand text-white',
  secondary: 'bg-fill-strong text-brand-strong',
  danger: 'bg-danger text-white',
  'danger-outline': 'bg-danger-soft text-danger',
  ghost: 'bg-transparent text-ink',
  signup: 'bg-brand text-white',
};

export const HOVER_CLASSES: Record<ButtonVariant, string> = {
  primary: 'hover:bg-brand-strong',
  secondary: 'hover:bg-accent-soft hover:text-ink',
  danger: '',
  'danger-outline': 'hover:bg-danger-soft',
  ghost: 'hover:bg-fill',
  signup: 'hover:bg-brand-strong',
};

export const DISABLED_CLASSES = 'bg-fill-strong text-ink-subtle cursor-not-allowed';

export const BASE_CLASSES =
  'inline-flex min-h-11 items-center justify-center gap-2 rounded-control px-5 text-base font-semibold transition-[background-color,color,transform] duration-200 ease-out active:scale-[0.97] motion-reduce:transition-none motion-reduce:active:scale-100';

export function buttonClassName(variant: ButtonVariant = 'primary'): string {
  return [BASE_CLASSES, VARIANT_CLASSES[variant], HOVER_CLASSES[variant]].filter(Boolean).join(' ');
}
