// 버튼 변형별 클래스다. Button 컴포넌트와, 버튼처럼 보여야 하는 링크가 함께 쓴다.
export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'danger-outline' | 'ghost';

// Tailwind가 소스 글자를 읽어 클래스를 만들기 때문에 클래스 이름을 이어 붙이지 않고 통째로 적는다.
export const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: 'bg-ink text-white border border-ink',
  secondary: 'bg-card text-ink border border-ink-subtle',
  danger: 'bg-danger text-white border border-danger',
  // 빼기·지우기처럼 다시 할 수 있는 위험 행동에 쓴다. 되돌릴 수 없는 확정은 Dialog 안의 danger로 한다.
  'danger-outline': 'bg-card text-danger border border-danger',
  ghost: 'bg-transparent text-ink border border-transparent',
};

export const HOVER_CLASSES: Record<ButtonVariant, string> = {
  primary: 'hover:bg-ink-muted hover:border-ink-muted',
  secondary: 'hover:bg-paper-deep',
  // 위험 색에는 더 짙은 토큰이 없어서 hover 색을 바꾸지 않는다.
  danger: '',
  'danger-outline': 'hover:bg-danger-soft',
  ghost: 'hover:bg-paper-deep',
};

// 흐리게 만들면 대비가 떨어지므로 투명도 대신 대비가 확보된 색 조합으로 비활성을 표시한다.
export const DISABLED_CLASSES = 'bg-paper-deep text-ink-muted border border-paper-deep cursor-not-allowed';

export const BASE_CLASSES =
  'inline-flex min-h-11 items-center justify-center gap-2 rounded-control px-4 text-base font-semibold';

// 버튼처럼 보이는 링크에 쓴다. 로그인이 필요한 행동을 비회원에게 같은 모양으로 보여 줄 때처럼, 버튼과 모양을 맞춰야 할 때 부른다.
export function buttonClassName(variant: ButtonVariant = 'primary'): string {
  return [BASE_CLASSES, VARIANT_CLASSES[variant], HOVER_CLASSES[variant]].filter(Boolean).join(' ');
}
