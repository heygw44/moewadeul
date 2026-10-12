type BrandMarkProps = {
  // 크기와 색만 넘긴다. 글꼴과 굵기는 이 컴포넌트가 정하므로, 같은 속성의 클래스를 겹쳐 쓰지 않는다.
  className?: string;
  // 데스크톱(lg 이상)에서 글자 가장자리를 잉크가 번진 것처럼 거칠게 깎는다. 작은 글자에 걸면 획이 지저분해지므로 큰 워드마크에만 켠다.
  rough?: boolean;
};

// 거친 가장자리 필터의 id다. 한 화면에 rough 워드마크는 머리글 하나뿐이라 고정 id를 쓴다.
const ROUGH_FILTER_ID = 'brand-mark-rough';

// 서비스 이름을 영문 워드마크로 그린다. 스크린 리더가 영문 글자를 한 자씩 읽지 않도록 한국어 이름을 이름표로 붙인다.
export function BrandMark({ className = '', rough = false }: BrandMarkProps) {
  return (
    <>
      {rough && <RoughFilter />}
      <span
        role="img"
        aria-label="뫼와들"
        className={[
          'font-brand font-extrabold whitespace-nowrap tracking-tight',
          rough ? 'lg:[filter:url(#brand-mark-rough)]' : '',
          className,
        ].join(' ')}
      >
        MOEWADEUL
      </span>
    </>
  );
}

// 노이즈로 가장자리를 조금 밀어낸 뒤, 살짝 흐렸다가 투명도를 계단식으로 잘라 가장자리를 다시 날카롭게 만든다.
// 그래서 털처럼 번지지 않고, 활판으로 찍은 글자처럼 가장자리만 울퉁불퉁하게 깎인다.
function RoughFilter() {
  return (
    <svg aria-hidden="true" focusable="false" width="0" height="0" className="absolute">
      <filter id={ROUGH_FILTER_ID} x="-5%" y="-20%" width="110%" height="140%">
        <feTurbulence type="fractalNoise" baseFrequency="0.45" numOctaves={2} seed={4} result="noise" />
        <feDisplacementMap in="SourceGraphic" in2="noise" scale={1.6} xChannelSelector="R" yChannelSelector="G" result="moved" />
        <feGaussianBlur in="moved" stdDeviation={0.35} result="blurred" />
        <feComponentTransfer in="blurred">
          <feFuncA type="discrete" tableValues="0 0 1 1" />
        </feComponentTransfer>
      </filter>
    </svg>
  );
}
