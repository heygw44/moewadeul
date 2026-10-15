// 버튼 변형별 클래스다. Button 컴포넌트와, 버튼처럼 보여야 하는 링크가 함께 쓴다.
export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'danger-outline' | 'ghost' | 'signup';

// Tailwind가 소스 글자를 읽어 클래스를 만들기 때문에 클래스 이름을 이어 붙이지 않고 통째로 적는다.
export const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  // 행동을 부르는 색은 강조색 하나다. 한 화면에 primary는 하나만 둔다.
  primary: 'bg-accent text-white border border-accent',
  // 테두리 대신 옅은 면으로 버튼을 알린다. 이름이 버튼의 할 일을 말해서 경계선이 없어도 알아볼 수 있다.
  secondary: 'bg-fill text-ink border border-fill',
  danger: 'bg-danger text-white border border-danger',
  // 빼기·지우기처럼 다시 할 수 있는 위험 행동에 쓴다. 되돌릴 수 없는 확정은 Dialog 안의 danger로 한다.
  'danger-outline': 'bg-surface text-danger border border-danger',
  ghost: 'bg-transparent text-ink border border-transparent',
  // 머리글의 회원가입에만 쓴다. 먹색으로 채워서 눈에 띄게 하되, 강조색이 아니라서 화면의 primary 하나와 겹치지 않는다.
  signup: 'bg-ink text-white border border-ink',
};

export const HOVER_CLASSES: Record<ButtonVariant, string> = {
  primary: 'hover:bg-accent-strong hover:border-accent-strong',
  secondary: 'hover:bg-fill-strong hover:border-fill-strong',
  // 위험 색에는 더 짙은 토큰이 없어서 hover 색을 바꾸지 않는다.
  danger: '',
  'danger-outline': 'hover:bg-danger-soft',
  ghost: 'hover:bg-fill',
  signup: 'hover:bg-ink/85 hover:border-transparent',
};

// 흐리게 만들면 대비가 떨어지므로 투명도 대신 대비가 확보된 색 조합으로 비활성을 표시한다.
// 보조 버튼이 옅은 면이라서, 비활성은 면을 비우고 선만 두어 보조 버튼과 다르게 보이게 한다.
export const DISABLED_CLASSES = 'bg-surface text-ink-subtle border border-line cursor-not-allowed';

export const BASE_CLASSES =
  'inline-flex min-h-11 items-center justify-center gap-2 rounded-control px-4 text-base font-semibold';

// 버튼처럼 보이는 링크에 쓴다. 로그인이 필요한 행동을 비회원에게 같은 모양으로 보여 줄 때처럼, 버튼과 모양을 맞춰야 할 때 부른다.
export function buttonClassName(variant: ButtonVariant = 'primary'): string {
  return [BASE_CLASSES, VARIANT_CLASSES[variant], HOVER_CLASSES[variant]].filter(Boolean).join(' ');
}
