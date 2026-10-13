type BrandMarkProps = {
  // 크기와 색만 넘긴다. 굵기와 자간은 이 컴포넌트가 정하므로, 같은 속성의 클래스를 겹쳐 쓰지 않는다.
  className?: string;
};

// 서비스 이름을 한글 워드마크 '뫼와들'로 그린다. 본문과 같은 Pretendard를 굵게 써서 따로 글꼴 파일을 받지 않는다.
export function BrandMark({ className = '' }: BrandMarkProps) {
  return <span className={['font-bold whitespace-nowrap tracking-tight', className].join(' ')}>뫼와들</span>;
}
