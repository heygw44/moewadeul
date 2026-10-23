// 섹션 제목·큰 제목 글자 크기. 섹션마다 같은 단계를 쓴다.
export const H2_CLASS = 'text-[clamp(1.875rem,3.2vw,2.75rem)] leading-tight font-extrabold tracking-[-0.035em]';
export const DISPLAY_CLASS = 'text-[clamp(2.75rem,6vw,5.75rem)] leading-none font-extrabold tracking-[-0.045em]';
// 링크에 마우스를 올리면 화살표를 오른쪽으로 조금 민다.
export const NUDGE_CLASS = 'transition-transform duration-300 motion-safe:group-hover:translate-x-1';
// 앱 화면 조각은 앱과 같은 흰 판·행 구분선을 쓴다.
export const FRAGMENT_CLASS = 'w-full rounded-row bg-surface text-[0.9375rem] leading-snug text-ink';
export const TAG_WARN_CLASS =
  'inline-flex rounded-md bg-warning-soft px-2 py-0.5 text-[0.8125rem] font-bold text-warning';
export const TAG_OK_CLASS = 'inline-flex rounded-md bg-accent-soft px-2 py-0.5 text-[0.8125rem] font-bold text-accent';
// 흰 면이나 숲색 면 위에 놓는 흰 알약 버튼이다. 숲색 면에서는 주 버튼을 흰색으로 뒤집는다.
export const WHITE_BUTTON_CLASS = 'bg-white text-brand-strong hover:bg-brand-ink';
