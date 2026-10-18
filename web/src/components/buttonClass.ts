// 버튼 변형별 클래스다. Button 컴포넌트와, 버튼처럼 보여야 하는 링크가 함께 쓴다.
export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'danger-outline' | 'ghost' | 'signup';

// Tailwind가 소스 글자를 읽어 클래스를 만들기 때문에 클래스 이름을 이어 붙이지 않고 통째로 적는다.
export const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  // 행동을 부르는 버튼은 숲색 알약 하나다. 넓게 칠하는 색이 숲색뿐이라 한 화면에 primary는 하나만 둔다.
  primary: 'bg-brand text-white',
  // 테두리 없이 세이지 면 한 단계로 버튼 경계를 낸다. 흰 바탕과 세이지 카드 어디에 놓아도 보인다.
  secondary: 'bg-fill-strong text-brand-strong',
  danger: 'bg-danger text-white',
  // 빼기·지우기처럼 다시 할 수 있는 위험 행동에 쓴다. 되돌릴 수 없는 확정은 Dialog 안의 danger로 한다.
  'danger-outline': 'bg-danger-soft text-danger',
  ghost: 'bg-transparent text-ink',
  // 머리글의 회원가입에만 쓴다. 주 버튼과 같은 숲색 알약이고, 머리글에서 가장 먼저 보이는 행동이다.
  signup: 'bg-brand text-white',
};

export const HOVER_CLASSES: Record<ButtonVariant, string> = {
  primary: 'hover:bg-brand-strong',
  secondary: 'hover:bg-accent-soft hover:text-ink',
  // 위험 색에는 더 짙은 토큰이 없어서 hover 색을 바꾸지 않는다.
  danger: '',
  'danger-outline': 'hover:bg-danger-soft',
  ghost: 'hover:bg-fill',
  signup: 'hover:bg-brand-strong',
};

// 흐리게 만들면 대비가 떨어지므로 투명도 대신 대비가 확보된 색 조합으로 비활성을 표시한다.
// 비활성은 보조 버튼과 같은 면에 회색 글자라서, 글자색으로 누를 수 없는 상태를 구분한다.
export const DISABLED_CLASSES = 'bg-fill-strong text-ink-subtle cursor-not-allowed';

export const BASE_CLASSES =
  'inline-flex min-h-11 items-center justify-center gap-2 rounded-control px-5 text-base font-semibold transition-[background-color,color,transform] duration-200 ease-out active:scale-[0.97] motion-reduce:transition-none motion-reduce:active:scale-100';

// 버튼처럼 보이는 링크에 쓴다. 로그인이 필요한 행동을 비회원에게 같은 모양으로 보여 줄 때처럼, 버튼과 모양을 맞춰야 할 때 부른다.
export function buttonClassName(variant: ButtonVariant = 'primary'): string {
  return [BASE_CLASSES, VARIANT_CLASSES[variant], HOVER_CLASSES[variant]].filter(Boolean).join(' ');
}
