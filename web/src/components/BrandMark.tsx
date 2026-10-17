type BrandMarkProps = {
  // 크기와 색만 넘긴다. 굵기와 자간은 이 컴포넌트가 정하므로, 같은 속성의 클래스를 겹쳐 쓰지 않는다.
  className?: string;
};

// 서비스 이름을 산 모양 표식과 한글 워드마크 '뫼와들'로 그린다. 본문과 같은 Pretendard를 굵게 써서 따로 글꼴 파일을 받지 않는다.
// 표식의 봉우리는 숲색, 밑줄(들)은 글자색을 따른다. 크기는 글자 크기(em)를 따라간다.
export function BrandMark({ className = '' }: BrandMarkProps) {
  return (
    <span
      className={[
        'inline-flex items-center gap-[0.35em] font-extrabold whitespace-nowrap tracking-[-0.03em]',
        className,
      ].join(' ')}
    >
      <svg aria-hidden="true" focusable="false" viewBox="0 0 26 26" className="size-[1.15em] shrink-0">
        <path d="M2 21 9.5 8l4.2 7.2L16.5 11 24 21Z" className="fill-brand" />
        <path d="M2 23h22" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" />
      </svg>
      뫼와들
    </span>
  );
}
