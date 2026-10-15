import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Icon } from '../../components/icons';

type Slide = { name: string; text: string; body: ReactNode };

// 짙은 숲 면 위를 가로로 넘기는 제품 조각 다섯 개다. 가운데 온 조각만 밝히고, 아래에 그 조각의 이름과 설명을 적는다.
// 조각은 랜딩 전용 정적 조각이고, 이름과 숫자는 예시다. 값은 정책과 맞춘다(결제 15분, 후기 14일, 공개 기록은 대략 좌표).
const SLIDES: Slide[] = [
  { name: '조건으로 걸러 보기', text: '산, 숲, 계곡처럼 장소 종류와 물·화장실 여부로 지도를 걸러요. 경고 박지만 빼고 볼 수도 있어요.', body: <FilterFragment /> },
  { name: '선착순 신청, 15분 안에 결제', text: '자리를 잡으면 결제할 시간이 15분 주어져요. 시간이 지나면 자리는 다음 사람에게 넘어가요.', body: <PaymentFragment /> },
  { name: '알림함', text: '합류 승인, 베이스캠프 확정, 빈자리가 나면 알림함과 이메일로 알려 줘요.', body: <NotificationFragment /> },
  { name: '블라인드 동행 후기', text: '함께 다녀온 멤버끼리 14일 안에 서로 써요. 내가 먼저 써야 상대 후기가 보여요.', body: <ReviewFragment /> },
  { name: '나만 보는 기록', text: '다녀온 곳을 글과 사진으로 남겨요. 기본은 비공개이고, 공개해도 위치는 대략만 보여요.', body: <MemoryFragment /> },
];

export function LandingShowcase() {
  const trackRef = useRef<HTMLDivElement>(null);
  const [current, setCurrent] = useState(1);

  // 줄 가운데에 가장 가까운 조각을 고른다
  const pick = useCallback(() => {
    const track = trackRef.current;
    if (!track) return;
    const middle = track.scrollLeft + track.clientWidth / 2;
    let best = 0;
    let bestDistance = Infinity;
    [...track.children].forEach((child, index) => {
      const card = child as HTMLElement;
      const distance = Math.abs(card.offsetLeft + card.offsetWidth / 2 - middle);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = index;
      }
    });
    setCurrent(best);
  }, []);

  const scrollToCard = useCallback((index: number, smooth: boolean) => {
    const track = trackRef.current;
    const card = track?.children[index] as HTMLElement | undefined;
    if (!track || !card) return;
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    track.scrollTo({ left: card.offsetLeft + card.offsetWidth / 2 - track.clientWidth / 2, behavior: smooth && !reduce ? 'smooth' : 'auto' });
  }, []);

  // 처음에는 두 번째 조각(결제 남은 시간)을 가운데 둔다
  useEffect(() => {
    scrollToCard(1, false);
    pick();
  }, [pick, scrollToCard]);

  const slide = SLIDES[current] ?? SLIDES[0]!;

  return (
    <section aria-label="뫼와들 기능" className="flex min-h-[96svh] flex-col justify-center bg-brand-deep py-24 text-canvas [&_:focus-visible]:outline-white">
      <div
        ref={trackRef}
        onScroll={() => requestAnimationFrame(pick)}
        className="landing-carousel grid snap-x snap-mandatory auto-cols-[86vw] grid-flow-col items-center gap-[4vw] overflow-x-auto px-[7vw] lg:auto-cols-[420px] lg:gap-[9vw] lg:px-[calc(50vw-210px)]"
      >
        {SLIDES.map((item, index) => (
          <div
            key={item.name}
            data-ui
            data-current={index === current}
            aria-hidden={index !== current}
            className="landing-carousel-card snap-center rounded-sheet bg-surface px-5 pt-5 pb-6 text-[15px] text-ink"
          >
            {item.body}
          </div>
        ))}
      </div>
      <div className="mx-auto mt-10 grid w-full max-w-screen-xl gap-6 px-4 lg:grid-cols-2 lg:px-10">
        <div aria-live="polite">
          <p className="text-[26px] font-bold tracking-[-0.02em]">{slide.name}</p>
          <p className="mt-1 max-w-[46ch] text-[17px] text-brand-ink">{slide.text}</p>
        </div>
        <div className="flex items-center gap-2 lg:justify-end">
          <ArrowButton label="이전 기능" disabled={current === 0} onClick={() => scrollToCard(current - 1, true)} icon="chevronLeft" />
          <ArrowButton label="다음 기능" disabled={current === SLIDES.length - 1} onClick={() => scrollToCard(current + 1, true)} icon="chevronRight" />
        </div>
      </div>
    </section>
  );
}

function ArrowButton({ label, disabled, onClick, icon }: { label: string; disabled: boolean; onClick: () => void; icon: 'chevronLeft' | 'chevronRight' }) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="grid size-11 place-items-center rounded-control bg-brand text-canvas transition-transform active:scale-95 disabled:opacity-40"
    >
      <Icon name={icon} size={20} />
    </button>
  );
}

function FragmentTitle({ children }: { children: ReactNode }) {
  return <h3 className="mb-3 text-[17px] font-extrabold">{children}</h3>;
}

function Chips({ items }: { items: Array<[string, boolean]> }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {items.map(([label, on]) => (
        <span key={label} className={`rounded-control px-3 py-1.5 text-sm ${on ? 'bg-ink text-surface' : 'bg-canvas'}`}>
          {label}
        </span>
      ))}
    </div>
  );
}

function FilterFragment() {
  return (
    <>
      <FragmentTitle>조건</FragmentTitle>
      <p className="mb-2 text-sm text-ink-muted">장소 종류</p>
      <Chips items={[['산', true], ['숲', true], ['계곡', false], ['바다', false], ['섬', false], ['호수', false]]} />
      <p className="mt-3.5 mb-2 text-sm text-ink-muted">시설</p>
      <Chips items={[['물', true], ['화장실', false]]} />
      <p className="mt-3.5 mb-2 text-sm text-ink-muted">그 밖에</p>
      <Chips items={[['경고 박지 빼기', true], ['휴장 빼기', false]]} />
      <span className="mt-4 flex min-h-12 items-center justify-center rounded-control bg-ink font-semibold text-surface">장소 412곳 보기</span>
    </>
  );
}

function PaymentFragment() {
  const [seconds, setSeconds] = useState(14 * 60 + 52);
  useEffect(() => {
    const timer = window.setInterval(() => setSeconds((left) => Math.max(0, left - 1)), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const mm = String(Math.floor(seconds / 60)).padStart(2, '0');
  const ss = String(seconds % 60).padStart(2, '0');
  return (
    <>
      <FragmentTitle>오대산 선재길 걷고 자는 밤</FragmentTitle>
      <p className="text-sm text-ink-muted">결제를 마치면 신청이 확정돼요</p>
      <p className="mt-3.5 mb-1 text-[44px] leading-none font-extrabold tracking-[-0.03em] tabular-nums">
        {mm}:{ss}
      </p>
      <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-5 gap-y-2">
        <dt className="text-ink-muted">참가비</dt>
        <dd className="text-right tabular-nums">30,000원</dd>
        <dt className="text-ink-muted">남은 자리</dt>
        <dd className="text-right tabular-nums">3/20</dd>
      </dl>
      <span className="mt-4 flex min-h-12 items-center justify-center rounded-control bg-ink font-semibold text-surface">결제하기</span>
    </>
  );
}

function NotificationFragment() {
  const items = [
    ['합류가 승인됐어요', '대청봉 아래 첫눈 기다리는 밤 · 방금'],
    ['빈자리가 생겼어요', '오대산 선재길 걷고 자는 밤 · 12분 전'],
    ['베이스캠프가 확정됐어요', '오픈채팅 링크가 열렸어요 · 어제'],
  ];
  return (
    <>
      <FragmentTitle>알림</FragmentTitle>
      <ul className="flex flex-col gap-1">
        {items.map(([title, meta]) => (
          <li key={title} className="rounded-control bg-canvas px-3 py-2.5 text-sm">
            <b className="block text-[15px]">{title}</b>
            {meta}
          </li>
        ))}
      </ul>
    </>
  );
}

function ReviewFragment() {
  return (
    <>
      <FragmentTitle>산바람 님과 다녀온 후기</FragmentTitle>
      <p className="text-sm text-ink-muted">다시 같이 가고 싶나요?</p>
      <div className="my-2.5 inline-grid grid-flow-col gap-1 rounded-control bg-fill p-1">
        <span className="rounded bg-surface px-4 py-2 font-bold">네</span>
        <span className="rounded px-4 py-2 text-ink-muted">아니요</span>
      </div>
      <div className="mb-2.5 flex flex-wrap gap-1.5">
        {['시간 약속', '흔적 남기지 않기', '준비성'].map((tag) => (
          <span key={tag} className="rounded-control bg-accent-soft px-2.5 py-1.5 text-sm font-semibold text-accent">
            {tag}
          </span>
        ))}
      </div>
      <p className="text-sm text-ink-muted">상대가 쓴 후기는 내 후기를 내면 열려요.</p>
    </>
  );
}

function MemoryFragment() {
  return (
    <>
      <FragmentTitle>기록 공개 범위</FragmentTitle>
      <div className="mb-3 inline-grid grid-flow-col gap-1 rounded-control bg-fill p-1">
        <span className="rounded bg-surface px-4 py-2 font-bold">나만 보기</span>
        <span className="rounded px-4 py-2 text-ink-muted">공개</span>
      </div>
      <dl className="grid grid-cols-[auto_1fr] gap-x-5 gap-y-2">
        <dt className="text-ink-muted">남에게 보이는 위치</dt>
        <dd className="text-right tabular-nums">38.12, 128.46</dd>
        <dt className="text-ink-muted">내가 보는 위치</dt>
        <dd className="text-right tabular-nums">38.1194, 128.4652</dd>
      </dl>
    </>
  );
}
