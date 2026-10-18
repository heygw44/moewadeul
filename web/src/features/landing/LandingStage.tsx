import { useId, useRef, useState, type CSSProperties, type KeyboardEvent, type ReactNode } from 'react';
import { Link, navigate } from '../../app/router';
import { buttonClassName } from '../../components/buttonClass';
import { Icon } from '../../components/icons';
import { approach, riseClass, useFrameWhileVisible, useInView, usePrefersReducedMotion } from './motion';

// 섹션 제목·소개 글자 크기. 섹션마다 같은 단계를 쓴다.
const H2_CLASS = 'text-[clamp(1.875rem,3.2vw,2.75rem)] leading-tight font-extrabold tracking-[-0.035em]';
const DISPLAY_CLASS = 'text-[clamp(2.75rem,6vw,5.75rem)] leading-none font-extrabold tracking-[-0.045em]';
// 링크에 마우스를 올리면 화살표를 오른쪽으로 조금 민다.
const NUDGE_CLASS = 'transition-transform duration-300 motion-safe:group-hover:translate-x-1';
// 세이지 무대 위에 앱 화면 조각을 올린다. 조각은 앱과 같은 흰 판·행 구분선을 쓴다.
const STAGE_CLASS = 'flex items-center justify-center rounded-media bg-fill p-5 sm:p-9';
const FRAGMENT_CLASS = 'w-full max-w-[27.5rem] rounded-row bg-surface text-[0.9375rem] leading-snug text-ink';
const TAG_WARN_CLASS = 'inline-flex rounded-md bg-warning-soft px-2 py-0.5 text-[0.8125rem] font-bold text-warning';
const TAG_OK_CLASS = 'inline-flex rounded-md bg-accent-soft px-2 py-0.5 text-[0.8125rem] font-bold text-accent';
const CHIP_CLASS = 'inline-flex h-8 items-center rounded-control bg-fill px-3 text-[0.8125rem] font-semibold';

// 처음 온 사람에게 뫼와들이 하는 일을 보여 주는 랜딩이다. 첫 화면에서 공원 경계 판정을 직접 해 보고,
// 아래로 내려가며 박지 찾기·동행 구하기·공식 행사를 탭으로 골라 앱 화면 조각으로 본다.
// 서버를 부르지 않는다. 화면 조각 속 장소·닉네임·날짜는 예시이고, 정책 값(정원, 신뢰 단계, 결제 기한 등)만 실제와 같다.
export function LandingStage() {
  return (
    <>
      <Hero />
      <EntryCards />
      <NightTabs />
      <FlowSection />
      <SafetySection />
      <FaqSection />
      <ClosingSection />
    </>
  );
}

// 섹션이 화면에 들어오면 자식들을 순서대로 떠오르게 한다. i번째 자식은 i × 80ms 늦게 시작한다.
function useRise<T extends Element>() {
  const { ref, inView } = useInView<T>();
  const rise = (index: number): { className: string; style: CSSProperties } => ({
    className: riseClass(inView),
    style: { animationDelay: `${index * 80}ms` },
  });
  return { ref, rise };
}

/* ───────────── 첫 화면 ───────────── */

function Hero() {
  const { ref, rise } = useRise<HTMLElement>();
  const first = rise(0);
  const map = rise(1);
  const second = rise(2);
  const intro = rise(3);
  const action = rise(4);
  return (
    <section ref={ref} aria-labelledby="hero-title" className="mx-auto max-w-[90rem] px-4 pt-8 pb-12 lg:px-8 lg:pt-16">
      <h1 id="hero-title" className="sr-only">
        어디서 잘지, 누구와 갈지
      </h1>
      {/* 한 문장을 둘로 나눠 양쪽 끝에 두고, 그 사이에 지도 조각을 둔다. 휴대폰에서는 위아래로 쌓는다. */}
      <div className="grid items-center gap-4 lg:grid-cols-[1fr_minmax(0,35rem)_1fr] lg:gap-8">
        <p aria-hidden="true" className={`${DISPLAY_CLASS} whitespace-nowrap ${first.className}`} style={first.style}>
          어디서 잘지,
        </p>
        <div className={map.className} style={map.style}>
          <JudgeMap />
        </div>
        <p
          aria-hidden="true"
          className={`${DISPLAY_CLASS} justify-self-end whitespace-nowrap ${second.className}`}
          style={second.style}
        >
          누구와 갈지.
        </p>
      </div>
      <div className="mt-8 flex flex-col gap-5 lg:mt-12 lg:flex-row lg:items-end lg:justify-between">
        <p className={`max-w-[30ch] text-lg leading-normal lg:text-xl ${intro.className}`} style={intro.style}>
          합법적인 박지를 지도에서 찾고, 신뢰 단계를 확인한 사람과 함께 떠나요.
        </p>
        <div className={action.className} style={action.style}>
          <Link to="/map" className={`${buttonClassName('primary')} group min-h-13 w-full px-7 text-lg lg:w-auto`}>
            지도 열기
            <Icon name="chevronRight" size={20} className={NUDGE_CLASS} />
          </Link>
        </div>
      </div>
    </section>
  );
}

// 지도 조각의 좌표계(viewBox)다. 다각형과 핀 위치는 이 좌표로 계산한다.
const MAP_WIDTH = 560;
const MAP_HEIGHT = 476;
// 예시 공원 경계 다각형이다. 실제 공원 경계가 아니라 판정이 어떻게 보이는지 보여 주는 그림이다.
const BOUNDARY: ReadonlyArray<readonly [number, number]> = [
  [120, 40],
  [330, 20],
  [430, 110],
  [400, 260],
  [250, 300],
  [130, 230],
];
const BOUNDARY_PATH = `M${BOUNDARY.map(([x, y]) => `${x} ${y}`).join(' L')} Z`;
// 키보드로 핀을 옮길 때 한 번에 움직이는 거리(지도 좌표)다.
const KEY_STEP = 16;
const KEY_MOVES: Record<string, readonly [number, number]> = {
  ArrowLeft: [-KEY_STEP, 0],
  ArrowRight: [KEY_STEP, 0],
  ArrowUp: [0, -KEY_STEP],
  ArrowDown: [0, KEY_STEP],
};

// 점이 다각형 안에 있는지 판정한다. 점에서 오른쪽으로 그은 반직선이 변을 홀수 번 넘으면 안쪽이다.
function insideBoundary(x: number, y: number): boolean {
  let inside = false;
  for (let i = 0, j = BOUNDARY.length - 1; i < BOUNDARY.length; j = i++) {
    const [xi, yi] = BOUNDARY[i]!;
    const [xj, yj] = BOUNDARY[j]!;
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

function clampPoint(x: number, y: number) {
  return { x: Math.min(MAP_WIDTH, Math.max(0, x)), y: Math.min(MAP_HEIGHT, Math.max(0, y)) };
}

// 이 페이지의 하나뿐인 조작이다. 지도를 누르거나 화살표 키로 핀을 옮기면 공원 경계 안·밖을 다시 판정하고,
// 경계 안이면 베이스캠프 열기 버튼을 막는다. 앱의 실제 규칙(경고 박지로는 베이스캠프를 열 수 없음)을 그대로 보여 준다.
function JudgeMap() {
  const svgRef = useRef<SVGSVGElement>(null);
  const [pin, setPin] = useState({ x: 291, y: 190 });
  const warned = insideBoundary(pin.x, pin.y);
  const hintId = useId();

  function moveTo(clientX: number, clientY: number) {
    const matrix = svgRef.current?.getScreenCTM();
    if (!matrix) return;
    // 화면 좌표를 지도 좌표로 바꾼다. 지도가 칸을 넘치게 채워(slice) 잘려 있어도 정확한 자리를 얻는다.
    const point = new DOMPoint(clientX, clientY).matrixTransform(matrix.inverse());
    setPin(clampPoint(point.x, point.y));
  }

  function handleKeyDown(event: KeyboardEvent<SVGSVGElement>) {
    const move = KEY_MOVES[event.key];
    if (!move) return;
    event.preventDefault();
    setPin((current) => clampPoint(current.x + move[0], current.y + move[1]));
  }

  return (
    <div data-ui className="relative aspect-[4/4.6] overflow-hidden rounded-media bg-[#eef2ea] sm:aspect-[4/3.4]">
      <svg
        ref={svgRef}
        viewBox={`0 0 ${MAP_WIDTH} ${MAP_HEIGHT}`}
        preserveAspectRatio="xMidYMid slice"
        role="application"
        tabIndex={0}
        aria-label="예시 지도. 화살표 키나 누르기로 박지 핀을 옮겨요"
        aria-describedby={hintId}
        onClick={(event) => moveTo(event.clientX, event.clientY)}
        onKeyDown={handleKeyDown}
        className="absolute inset-0 size-full cursor-crosshair touch-manipulation"
      >
        <rect width={MAP_WIDTH} height={MAP_HEIGHT} fill="#eef2ea" />
        <path d="M0 330 C90 300 150 360 240 330 S420 250 560 290 V476 H0Z" fill="#e3eadf" />
        <path
          d="M-10 170 C80 150 120 210 210 190 S360 120 470 150 S560 130 580 120"
          fill="none"
          stroke="#cfe0ea"
          strokeWidth={14}
          strokeLinecap="round"
        />
        <path d="M40 420 C140 380 220 430 300 400 S470 360 540 380" fill="none" stroke="#fff" strokeWidth={6} />
        <g fill="#d6e3d2">
          <circle cx={80} cy={80} r={26} />
          <circle cx={500} cy={60} r={34} />
          <circle cx={470} cy={420} r={30} />
          <circle cx={60} cy={300} r={22} />
        </g>
        <path
          d={BOUNDARY_PATH}
          className="fill-warning stroke-warning"
          fillOpacity={0.08}
          strokeWidth={2}
          strokeDasharray="7 6"
        />
        <text x={226} y={64} fontSize={13} fontWeight={700} className="fill-warning">
          예시 국립공원 경계
        </text>
        {/* 핀은 지도 좌표에 그려서 화면 크기가 바뀌어도 같은 자리에 있다. 끝이 좌표를 가리키는 물방울 모양이다. */}
        <g
          style={{ transform: `translate(${pin.x}px, ${pin.y}px)` }}
          className="transition-transform duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none"
        >
          <path
            d="M0 0 C-4 -9 -15 -15 -15 -28 A15 15 0 1 1 15 -28 C15 -15 4 -9 0 0Z"
            className={warned ? 'fill-warning' : 'fill-brand'}
            stroke="#fff"
            strokeWidth={3}
          />
          <text y={-23} textAnchor="middle" fontSize={14} fontWeight={800} fill="#fff">
            1
          </text>
        </g>
      </svg>

      <p
        id={hintId}
        className="pointer-events-none absolute top-3 left-3 rounded-control bg-surface px-3.5 py-1.5 text-sm font-semibold shadow-raise"
      >
        지도를 눌러 핀을 옮겨 보세요
      </p>

      {/* 판정 결과 창이다. 지도 위에 떠 있어 그림자를 둔다. 결과가 바뀌면 스크린 리더가 읽는다. */}
      <div className="absolute inset-x-3 bottom-3 rounded-row bg-surface p-4 text-[0.9375rem] shadow-raise sm:inset-x-4 sm:bottom-4">
        <p className="text-[1.0625rem] font-bold">이름 없는 박지 · 사용자 제보</p>
        <p className="text-[0.9375rem] text-ink-muted">경계 자료: 예시 공원 경계 · 2026-06-30 기준</p>
        <p
          aria-live="polite"
          className={`mt-2.5 rounded-xl px-3 py-2 font-semibold ${warned ? 'bg-warning-soft text-warning' : 'bg-accent-soft text-accent'}`}
        >
          {warned ? '공원 경계 안이에요. 야영이 금지된 곳일 수 있어요' : '공원 경계 밖이에요. 경고 없이 등록돼요'}
        </p>
        <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
          <span className="text-[0.9375rem] text-ink-muted">
            {warned ? '경고 박지는 베이스캠프를 열 수 없어요' : '이 자리로 베이스캠프를 열 수 있어요'}
          </span>
          <span
            aria-hidden="true"
            className={`inline-flex h-9 w-full items-center justify-center rounded-control px-4 text-[0.9375rem] font-semibold sm:w-auto ${
              warned ? 'bg-fill-strong text-ink-subtle' : 'bg-brand text-white'
            }`}
          >
            베이스캠프 열기
          </span>
        </div>
      </div>
    </div>
  );
}

/* ───────────── 고르는 카드 ───────────── */

const ENTRY_CARDS = [
  {
    to: '/map',
    title: '박지 지도',
    body: '오늘 밤 잘 곳을 찾을 때',
    action: '지도 열기',
    image: '/landing/photos/tent-cone.webp',
    alt: '초원에 세운 흰 원뿔 텐트와 뒤쪽 산 능선',
    imageClass: 'w-[64%]',
  },
  {
    to: '/basecamps',
    title: '베이스캠프',
    body: '함께 갈 사람을 찾을 때',
    action: '둘러보기',
    image: '/landing/photos/boots.webp',
    alt: '나무 데크 위에 선 등산화 한 켤레',
    imageClass: 'w-[72%]',
  },
  {
    to: '/programs',
    title: '공식 행사',
    body: '공식 행사에 가고 싶을 때',
    action: '일정 보기',
    image: '/landing/photos/stove.webp',
    alt: '풀밭에 놓인 버너와 주전자',
    imageClass: 'w-[72%]',
  },
] as const;

// 너비가 다른 카드 셋으로 할 일을 고르게 한다(데스크톱 6:3:3). 같은 모양 카드를 나란히 두지 않으려고 첫 카드를 넓게 둔다.
function EntryCards() {
  const { ref, rise } = useRise<HTMLElement>();
  return (
    <section
      ref={ref}
      aria-label="하려는 일 고르기"
      className="mx-auto grid max-w-[90rem] gap-4 px-4 pb-24 lg:grid-cols-[6fr_3fr_3fr] lg:px-8 lg:pb-40"
    >
      {ENTRY_CARDS.map((card, index) => {
        const r = rise(index);
        return (
          <Link
            key={card.to}
            to={card.to}
            style={r.style}
            className={`group relative flex min-h-64 flex-col overflow-hidden rounded-media bg-fill p-6 transition-transform duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] motion-safe:hover:-translate-y-1 lg:min-h-96 lg:p-8 ${r.className}`}
          >
            <h2 className="relative z-10 text-[1.625rem] leading-tight font-extrabold tracking-[-0.03em]">
              {card.title}
            </h2>
            <p className="relative z-10 mt-1.5 max-w-[22ch] text-[1.0625rem]">{card.body}</p>
            <span className={`${buttonClassName('secondary')} relative z-10 mt-auto self-start`}>
              {card.action}
              <Icon name="chevronRight" size={18} className={NUDGE_CLASS} />
            </span>
            <img
              src={card.image}
              alt={card.alt}
              loading="lazy"
              className={`pointer-events-none absolute right-0 bottom-0 origin-bottom-right mix-blend-multiply transition-transform duration-700 ease-[cubic-bezier(0.22,1,0.36,1)] motion-safe:group-hover:scale-[1.04] ${card.imageClass}`}
            />
          </Link>
        );
      })}
    </section>
  );
}

/* ───────────── 어떤 밤을 준비하세요? (탭) ───────────── */

type TabItem = { title: string; body: string; stage: ReactNode };
type NightTab = {
  key: string;
  label: string;
  question: string;
  action: { to: string; label: string };
  secondary?: { to: string; label: string };
  items: TabItem[];
};

function Photo({ src, alt }: { src: string; alt: string }) {
  return (
    <div className="aspect-[3/2] overflow-hidden rounded-media bg-fill">
      <img src={src} alt={alt} loading="lazy" className="size-full object-cover mix-blend-multiply" />
    </div>
  );
}

// 무대 안은 앱 화면 조각이라 앱의 행 구분선·작은 글자를 그대로 쓴다(data-ui로 표시).
function Stage({ children }: { children: ReactNode }) {
  return (
    <div className={STAGE_CLASS}>
      <div data-ui className="contents">
        {children}
      </div>
    </div>
  );
}

// 목록 행 조각이다. 왼쪽 번호 원, 가운데 이름과 부가 정보, 오른쪽 굵은 거리다.
function FragmentRow({
  number,
  warn = false,
  name,
  meta,
  distance,
  tag,
}: {
  number: number;
  warn?: boolean;
  name: string;
  meta: string;
  distance: string;
  tag?: ReactNode;
}) {
  return (
    <div className="flex items-center gap-3 border-t border-line px-4 py-3 first:border-t-0">
      <span
        className={`grid size-7 shrink-0 place-items-center rounded-full text-xs font-bold text-white ${warn ? 'bg-warning' : 'bg-brand'}`}
      >
        {number}
      </span>
      <div className="min-w-0 flex-1">
        <p className="flex flex-wrap items-center gap-1.5 font-semibold">
          {name} {tag}
        </p>
        <p className="text-[0.9375rem] text-ink-muted">{meta}</p>
      </div>
      <span className="font-mono text-base font-extrabold tracking-[-0.02em]">{distance}</span>
    </div>
  );
}

const NIGHT_TABS: NightTab[] = [
  {
    key: 'spot',
    label: '박지 찾기',
    question: '여기서 자도 되는지, 매번 헷갈리셨나요?',
    action: { to: '/map', label: '지도 열기' },
    secondary: { to: '/bakjis/new', label: '박지 제보' },
    items: [
      {
        title: '내 위치에서 가까운 순으로',
        body: '반경은 50km까지 넓힐 수 있고, 휴장한 곳도 숨기지 않고 표시해요.',
        stage: (
          <Stage>
            <div className={FRAGMENT_CLASS}>
              <div className="flex flex-wrap gap-1.5 border-b border-line px-4 py-3">
                <span className={`${CHIP_CLASS} bg-ink text-white`}>반경 10km</span>
                <span className={CHIP_CLASS}>휴장 제외</span>
                <span className={CHIP_CLASS}>화장실</span>
              </div>
              <FragmentRow number={1} name="양평 옥천 계곡 박지" meta="사용자 제보 · 확인 12명" distance="2.4km" />
              <FragmentRow number={2} name="산음자연휴양림" meta="공공데이터 · 운영 중" distance="6.1km" />
              <FragmentRow
                number={3}
                warn
                name="용문산 능선 박지"
                tag={<span className={TAG_WARN_CLASS}>경계 경고</span>}
                meta="사용자 제보 · 확인 3명"
                distance="8.7km"
              />
            </div>
          </Stage>
        ),
      },
      {
        title: '공원 경계 안이면 먼저 알려 줘요',
        body: '국립공원 경계 자료로 박지를 판정하고, 자료의 출처와 기준일을 함께 보여 줘요.',
        stage: <Photo src="/landing/photos/tent-valley.webp" alt="산비탈 아래 골짜기에 세운 텐트 한 동" />,
      },
      {
        title: '경고 박지로는 모임을 열 수 없어요',
        body: '여럿이 모이는 일정은 경고가 없는 장소에서만 시작해요.',
        stage: (
          <Stage>
            <div className={`${FRAGMENT_CLASS} p-4`}>
              <div className="flex items-center justify-between gap-2">
                <p className="text-[1.0625rem] font-bold">용문산 능선 박지</p>
                <span className={TAG_WARN_CLASS}>경계 경고</span>
              </div>
              <p className="mt-0.5 text-[0.9375rem] text-ink-muted">용문산 일대 · 사용자 제보</p>
              <div className="mt-4 flex gap-2">
                <span className="inline-flex h-9 flex-1 items-center justify-center rounded-control bg-fill-strong text-[0.9375rem] font-semibold text-ink-subtle">
                  베이스캠프 열기
                </span>
                <span className="inline-flex h-9 items-center justify-center rounded-control bg-fill px-4 text-sm font-semibold">
                  혼자 가기
                </span>
              </div>
              <p className="mt-2.5 text-[0.9375rem] text-warning">
                공원 경계 경고가 붙은 박지라 베이스캠프를 열 수 없어요.
              </p>
            </div>
          </Stage>
        ),
      },
    ],
  },
  {
    key: 'companion',
    label: '동행 구하기',
    question: '처음 보는 사람과 산에서 하룻밤, 망설여지나요?',
    action: { to: '/basecamps', label: '베이스캠프 둘러보기' },
    items: [
      {
        title: '신뢰 단계로 먼저 확인해요',
        body: '단계는 저장해 두지 않고, 볼 때마다 지금 기록으로 다시 계산해요.',
        stage: (
          <Stage>
            <ol className={FRAGMENT_CLASS}>
              {[
                { level: 0, title: '이메일 인증', note: '가입하면 바로' },
                { level: 1, title: '본인확인 · 성인', note: '베이스캠프를 열 수 있어요' },
                { level: 2, title: '완료한 동행 3회 · 다시 동행 80%', note: '최근 180일 확정 제재가 없을 때' },
              ].map((step) => (
                <li
                  key={step.level}
                  className={`grid grid-cols-[3rem_1fr] items-center gap-3 border-t border-line px-4 py-3 first:border-t-0 ${step.level === 1 ? 'bg-fill' : ''}`}
                >
                  <span
                    className={`text-center text-3xl font-extrabold tracking-[-0.04em] ${step.level === 1 ? 'text-brand' : 'text-ink-faint'}`}
                  >
                    {step.level}
                  </span>
                  <span>
                    <span className="block font-semibold">{step.title}</span>
                    <span className="block text-[0.9375rem] text-ink-muted">{step.note}</span>
                  </span>
                </li>
              ))}
            </ol>
          </Stage>
        ),
      },
      {
        title: '합류 조건을 정해 두면',
        body: '최소 신뢰 단계, 연령대, 동성만. 조건에 맞는 사람만 신청할 수 있어요. 정원은 리더를 포함해 2~6명이에요.',
        stage: (
          <Stage>
            <div className={`${FRAGMENT_CLASS} p-4`}>
              <div className="flex items-start justify-between gap-2">
                <p className="text-[1.0625rem] font-bold">유명산 1박, 별 보러 가요</p>
                <span className={TAG_OK_CLASS}>모집 중</span>
              </div>
              <p className="mt-0.5 text-[0.9375rem] text-ink-muted">11월 8일(토) 출발 · 1박 · 유명산자연휴양림</p>
              <div className="mt-3 flex flex-wrap gap-1.5">
                <span className={CHIP_CLASS}>신뢰 단계 1 이상</span>
                <span className={CHIP_CLASS}>30대</span>
                <span className={CHIP_CLASS}>동성만</span>
              </div>
              <div className="mt-4 flex items-center justify-between font-bold">
                <span className="text-[0.9375rem] text-ink-muted">멤버</span>
                <span className="font-mono">3 / 4명</span>
              </div>
              <div className="mt-2 h-2 overflow-hidden rounded-full bg-fill-strong">
                <div className="h-full w-3/4 rounded-full bg-brand" />
              </div>
            </div>
          </Stage>
        ),
      },
      {
        title: '연락 수단은 확정된 뒤에',
        body: '베이스캠프가 확정되면 멤버끼리만 보이고, 다녀온 뒤 7일이 지나면 다시 가려져요.',
        stage: <Photo src="/landing/photos/meadow.webp" alt="산 아래 풀밭에 누워 쉬는 백패커" />,
      },
    ],
  },
  {
    key: 'program',
    label: '공식 행사',
    question: '공식 행사 자리, 매번 놓치셨나요?',
    action: { to: '/programs', label: '행사 일정 보기' },
    items: [
      {
        title: '정원만큼만 정확히',
        body: '신청이 한꺼번에 몰려도 선착순으로 정원만큼만 받고, 한 사람은 한 번만 신청해요.',
        stage: (
          <Stage>
            <div className={`${FRAGMENT_CLASS} p-4`}>
              <p className="text-[1.0625rem] font-bold">가을 둘레길 백패킹</p>
              <p className="mt-0.5 text-[0.9375rem] text-ink-muted">11월 15일~16일 · 숙박 행사 · 참가비 45,000원</p>
              <div className="mt-4 flex justify-between font-bold">
                <span>남은 자리</span>
                <span className="font-mono">12 / 200</span>
              </div>
              <div className="mt-2 h-2 overflow-hidden rounded-full bg-fill-strong">
                <div className="h-full w-[94%] rounded-full bg-brand" />
              </div>
              <span className="mt-4 flex h-10 items-center justify-center rounded-control bg-brand text-[0.9375rem] font-semibold text-white">
                신청하기
              </span>
            </div>
          </Stage>
        ),
      },
      {
        title: '결제는 15분 안에',
        body: '기한을 넘기면 자리는 다음 사람에게 돌아가요. 정원이 찼다면 빈자리 알림을 신청해 두세요.',
        stage: (
          <Stage>
            <div className={`${FRAGMENT_CLASS} p-5 text-center`}>
              <p className="text-[0.9375rem] text-ink-muted">결제 대기</p>
              <p className="font-mono text-[2.75rem] leading-tight font-extrabold tracking-[-0.04em]">14:32</p>
              <p className="text-[0.9375rem] text-ink-muted">이 시간이 지나면 신청이 취소되고 자리가 돌아가요</p>
              <span className="mt-4 flex h-10 items-center justify-center rounded-control bg-brand text-[0.9375rem] font-semibold text-white">
                45,000원 결제하기
              </span>
            </div>
          </Stage>
        ),
      },
      {
        title: '시작 3일 전까지는 전액 환불',
        body: '그 뒤로는 취소할 수 없어요. 숙박 행사는 신뢰 단계 1부터 신청할 수 있어요.',
        stage: (
          <Stage>
            <div className={`${FRAGMENT_CLASS} p-4`}>
              <p className="text-[1.0625rem] font-bold">신청 취소</p>
              <p className="mt-0.5 text-[0.9375rem] text-ink-muted">행사 시작 11월 15일 · 오늘 11월 10일</p>
              <div className="mt-3 flex justify-between rounded-xl bg-fill px-4 py-3 font-bold">
                <span>환불 금액</span>
                <span className="font-mono">45,000원</span>
              </div>
              <span className="mt-3 flex h-10 items-center justify-center rounded-control bg-brand text-[0.9375rem] font-semibold text-white">
                전액 환불받고 취소
              </span>
            </div>
          </Stage>
        ),
      },
    ],
  },
];

// 탭으로 세 기능 가운데 하나를 고른다. 탭 줄은 스크롤해도 머리글 아래 붙어 있고,
// 왼쪽에는 기능 이름과 질문을 붙여 두고 오른쪽에서 화면 조각과 설명이 지나간다.
function NightTabs() {
  const [selected, setSelected] = useState(NIGHT_TABS[0]!.key);
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const { ref, rise } = useRise<HTMLElement>();
  const title = rise(0);
  const baseId = useId();

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
    event.preventDefault();
    const next = (index + (event.key === 'ArrowRight' ? 1 : NIGHT_TABS.length - 1)) % NIGHT_TABS.length;
    setSelected(NIGHT_TABS[next]!.key);
    tabRefs.current[next]?.focus();
  }

  return (
    <section ref={ref} aria-labelledby="night-title" className="mx-auto max-w-screen-xl px-4 pb-24 lg:px-8 lg:pb-40">
      <h2 id="night-title" className={`${H2_CLASS} text-center ${title.className}`} style={title.style}>
        어떤 밤을 준비하세요?
      </h2>
      <div className="sticky top-14 z-10 -mx-4 mt-8 mb-8 flex justify-center bg-canvas px-4 py-2 lg:top-16 lg:mb-14 lg:bg-transparent">
        <div role="tablist" aria-label="기능 고르기" className="inline-flex gap-1 rounded-control bg-fill p-1">
          {NIGHT_TABS.map((tab, index) => {
            const on = tab.key === selected;
            return (
              <button
                key={tab.key}
                ref={(element) => {
                  tabRefs.current[index] = element;
                }}
                type="button"
                role="tab"
                id={`${baseId}-tab-${tab.key}`}
                aria-selected={on}
                aria-controls={`${baseId}-panel-${tab.key}`}
                tabIndex={on ? 0 : -1}
                onClick={() => setSelected(tab.key)}
                onKeyDown={(event) => handleKeyDown(event, index)}
                className={`min-h-11 rounded-control px-4 text-[0.9375rem] font-semibold whitespace-nowrap transition-colors sm:px-5 sm:text-base ${
                  on ? 'bg-ink text-white' : 'text-ink-muted hover:text-ink'
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>
      {NIGHT_TABS.map((tab) => (
        <div
          key={tab.key}
          role="tabpanel"
          id={`${baseId}-panel-${tab.key}`}
          aria-labelledby={`${baseId}-tab-${tab.key}`}
          hidden={tab.key !== selected}
          className="grid gap-8 lg:grid-cols-[4fr_8fr] lg:gap-16"
        >
          {tab.key === selected && <TabPanelBody tab={tab} />}
        </div>
      ))}
    </section>
  );
}

// 패널은 고를 때마다 새로 그려서, 항목이 다시 떠오르며 바뀐 내용을 알린다.
function TabPanelBody({ tab }: { tab: NightTab }) {
  const { ref, rise } = useRise<HTMLDivElement>();
  return (
    <>
      <aside className="self-start lg:sticky lg:top-44">
        <h3 className={H2_CLASS}>{tab.label}</h3>
        <p className="mt-3 max-w-[18ch] text-xl leading-snug">{tab.question}</p>
        <div className="mt-7 flex flex-wrap gap-2.5">
          <Link to={tab.action.to} className={`${buttonClassName('primary')} group`}>
            {tab.action.label}
            <Icon name="chevronRight" size={18} className={NUDGE_CLASS} />
          </Link>
          {tab.secondary && (
            <Link to={tab.secondary.to} className={buttonClassName('secondary')}>
              {tab.secondary.label}
            </Link>
          )}
        </div>
      </aside>
      <div ref={ref} className="flex flex-col gap-14 lg:gap-16">
        {tab.items.map((item, index) => {
          const r = rise(index);
          return (
            <div
              key={item.title}
              className={`grid gap-4 lg:grid-cols-[minmax(0,1fr)_13.75rem] lg:gap-8 ${r.className}`}
              style={r.style}
            >
              {item.stage}
              <div>
                <h4 className="text-xl leading-snug font-bold tracking-[-0.02em]">{item.title}</h4>
                <p className="mt-2 max-w-[30ch] text-[1.0625rem]">{item.body}</p>
              </div>
            </div>
          );
        })}
      </div>
    </>
  );
}

/* ───────────── 떠나기까지 세 번이면 돼요 ───────────── */

type FlowStep = { title: string; call?: ReactNode; response: ReactNode; wide?: boolean };

const FLOW_STEPS: FlowStep[] = [
  {
    title: '먼저, 갈 곳을 고르세요.',
    call: (
      <>
        <Icon name="search" size={18} className="shrink-0 text-ink-muted" />
        <span className="flex-1">가평 북면</span>
        <span className="text-[0.9375rem] text-ink-muted">반경 20km</span>
      </>
    ),
    response: (
      <div className={`${FRAGMENT_CLASS} max-w-[28.75rem]`}>
        <FragmentRow number={1} name="명지산 계곡 야영장" meta="공공데이터 · 운영 중" distance="3.2km" />
        <FragmentRow number={2} name="적목리 강변 박지" meta="사용자 제보 · 확인 7명" distance="5.8km" />
      </div>
    ),
  },
  {
    title: '다음, 베이스캠프에 합류를 신청하세요.',
    call: <span>처음 1박이라 장비를 같이 봐 주시면 좋겠어요.</span>,
    response: (
      <div className={`${FRAGMENT_CLASS} flex max-w-[28.75rem] items-center justify-between p-4`}>
        <div>
          <p className="font-bold">합류 승인됨</p>
          <p className="text-[0.9375rem] text-ink-muted">캠프 리더 지윤 · 신뢰 단계 2</p>
        </div>
        <span className="font-mono font-bold">4 / 4명</span>
      </div>
    ),
  },
  {
    title: '출발 전날 0시, 두 명 이상이면 확정돼요.',
    wide: true,
    response: (
      <div className="grid w-full gap-4 rounded-row bg-surface p-4 text-[0.9375rem] sm:grid-cols-2">
        <div>
          <span className={TAG_OK_CLASS}>확정</span>
          <p className="mt-2 text-[1.0625rem] font-bold">명지산 계곡 야영장 · 11월 8일 1박</p>
          <p className="mt-1 text-[0.9375rem] text-ink-muted">멤버 4명 · 다녀온 뒤 7일까지 연락 수단이 보여요</p>
        </div>
        <div className="rounded-xl bg-fill p-4">
          <p className="text-[0.9375rem] text-ink-muted">캠프 리더 연락 수단</p>
          <p className="mt-1 font-bold">확정된 멤버에게만 보이는 오픈채팅 링크</p>
        </div>
      </div>
    ),
  },
];

// 사용자가 넣는 것(흰 칸)과 돌아오는 화면 조각을 한 열에 번갈아 둔다. 마지막 조각은 결과라서 열 밖으로 넓게 둔다.
function FlowSection() {
  const { ref, rise } = useRise<HTMLElement>();
  const heading = rise(0);
  return (
    <section ref={ref} aria-labelledby="flow-title" className="bg-fill py-24 lg:py-40">
      <div className="mx-auto max-w-screen-xl px-4 lg:px-8">
        <div className="max-w-[38.75rem] lg:ml-[calc(25%-2.5rem)]">
          <h2 id="flow-title" className={`${H2_CLASS} ${heading.className}`} style={heading.style}>
            떠나기까지 세 번이면 돼요
          </h2>
          <ol className="mt-12 flex flex-col gap-16 lg:mt-16 lg:gap-22">
            {FLOW_STEPS.map((step, index) => {
              const r = rise(index + 1);
              return (
                <li key={step.title} className={r.className} style={r.style}>
                  <h3 className="text-xl font-bold tracking-[-0.02em]">{step.title}</h3>
                  {step.call && (
                    <div className="mt-4 flex max-w-[32.5rem] items-center gap-2.5 rounded-row bg-surface px-4 py-3.5 text-base">
                      {step.call}
                    </div>
                  )}
                  <div data-ui className={`mt-4 lg:ml-14 ${step.wide ? 'lg:-mr-[40%]' : ''}`}>
                    {step.response}
                  </div>
                </li>
              );
            })}
          </ol>
        </div>
      </div>
    </section>
  );
}

/* ───────────── 안심하고 함께 가도록 ───────────── */

const SAFETY_ITEMS = [
  { title: '위협 신고는 바로 막아요', body: '성희롱이나 위협을 신고하면 확인 전이라도 72시간 동안 이용을 멈춰요.' },
  { title: '제재는 단계대로', body: '경고, 7일 정지, 30일 정지, 영구 정지 순서로 무거워져요.' },
  { title: '후기는 블라인드로', body: '상대에게 후기를 쓰거나 14일이 지나야 내가 받은 후기를 볼 수 있어요.' },
  {
    title: '개인정보는 최소로',
    body: '본인확인 결과는 출생연도와 성별만 남기고, 이름과 전화번호는 저장하지 않아요.',
  },
];

// 오른쪽 사진은 스크롤에 따라 천천히 위로 흐른다. 매 프레임 남은 거리의 일부만 따라가서 멈출 때 부드럽다.
function SafetySection() {
  const { ref, rise } = useRise<HTMLElement>();
  const reduced = usePrefersReducedMotion();
  const frameRef = useRef<HTMLDivElement>(null);
  const imageRef = useRef<HTMLImageElement>(null);
  const offset = useRef(0);

  useFrameWhileVisible(
    frameRef,
    () => {
      const frame = frameRef.current;
      const image = imageRef.current;
      if (!frame || !image) return;
      const rect = frame.getBoundingClientRect();
      // 그림 가운데가 화면 가운데에서 얼마나 떨어졌는지(화면 높이 단위)에 따라 최대 24px 옮긴다.
      const distance = (rect.top + rect.height / 2 - innerHeight / 2) / innerHeight;
      offset.current = approach(offset.current, Math.max(-1, Math.min(1, distance)) * -24, 0.1);
      image.style.transform = `scale(1.08) translateY(${offset.current}px)`;
    },
    !reduced,
  );

  const heading = rise(0);
  const photo = rise(5);
  return (
    <section
      ref={ref}
      aria-labelledby="safety-title"
      className="mx-auto grid max-w-screen-xl items-center gap-10 px-4 py-24 lg:grid-cols-[5fr_7fr] lg:gap-16 lg:px-8 lg:py-40"
    >
      <div>
        <h2 id="safety-title" className={`${H2_CLASS} ${heading.className}`} style={heading.style}>
          안심하고 함께 가도록
        </h2>
        <ul className="mt-9 flex flex-col gap-6">
          {SAFETY_ITEMS.map((item, index) => {
            const r = rise(index + 1);
            return (
              <li key={item.title} className={`max-w-[44ch] ${r.className}`} style={r.style}>
                <p className="text-xl font-bold tracking-[-0.02em]">{item.title}</p>
                <p className="mt-1 text-[1.0625rem]">{item.body}</p>
              </li>
            );
          })}
        </ul>
      </div>
      <div
        ref={frameRef}
        className={`aspect-[7/5] overflow-hidden rounded-media bg-fill ${photo.className}`}
        style={photo.style}
      >
        <img
          ref={imageRef}
          src="/landing/photos/tent-dome.webp"
          alt="고원 풀밭에 세운 돔 텐트"
          loading="lazy"
          className="size-full scale-[1.08] object-cover mix-blend-multiply"
        />
      </div>
    </section>
  );
}

/* ───────────── 자주 묻는 질문 ───────────── */

const FAQ = [
  {
    q: '경고가 붙은 박지는 가면 안 되나요?',
    a: '경고는 공원 경계 자료로 판정한 참고 정보이고 법적 효력은 없어요. 다만 경고 박지로는 베이스캠프를 열 수 없어요.',
  },
  {
    q: '본인확인 때 무엇을 저장하나요?',
    a: '출생연도와 성별, 그리고 한 사람이 계정 하나만 쓰도록 확인하는 값의 해시만 저장해요. 이름과 전화번호는 남기지 않아요.',
  },
  {
    q: '베이스캠프는 몇 명이 함께 가나요?',
    a: '캠프 리더를 포함해 2명에서 6명까지예요. 출발일은 다음 날부터 60일 안에서 고르고, 1박에서 3박까지 갈 수 있어요.',
  },
  {
    q: '멤버와는 어떻게 연락하나요?',
    a: '베이스캠프가 확정되면 멤버끼리 연락 수단이 보여요. 다녀온 뒤 7일이 지나면 다시 가려져요.',
  },
  {
    q: '동행 후기는 언제 보이나요?',
    a: '내가 상대에게 후기를 쓰거나 다녀온 지 14일이 지나야 받은 후기를 볼 수 있어요. 쓴 뒤에는 고치거나 지울 수 없어요.',
  },
];

// 넓은 화면은 왼쪽 질문, 오른쪽에 붙어 있는 큰 답이다. 좁은 화면은 고른 질문 아래에 답을 펼친다.
function FaqSection() {
  const [open, setOpen] = useState(0);
  const { ref, rise } = useRise<HTMLElement>();
  const list = rise(0);
  const answer = FAQ[open]!;
  return (
    <section
      ref={ref}
      aria-labelledby="faq-title"
      className="mx-auto grid max-w-screen-xl gap-8 px-4 pb-24 lg:grid-cols-[5fr_1fr_6fr] lg:px-8 lg:pb-40"
    >
      <div className={list.className} style={list.style}>
        <h2 id="faq-title" className="mb-5 text-xl font-semibold text-ink-muted">
          자주 묻는 질문
        </h2>
        <ul className="flex flex-col gap-1">
          {FAQ.map((item, index) => {
            const on = index === open;
            return (
              <li key={item.q}>
                <button
                  type="button"
                  aria-expanded={on}
                  onClick={() => setOpen(index)}
                  onFocus={() => setOpen(index)}
                  onMouseEnter={() => setOpen(index)}
                  className={`flex min-h-11 w-full items-baseline gap-3.5 py-2 text-left text-xl font-semibold transition-colors ${
                    on ? 'text-ink' : 'text-ink-muted hover:text-ink'
                  }`}
                >
                  <span
                    aria-hidden="true"
                    className={`size-2 shrink-0 -translate-y-1 rounded-full ${on ? 'bg-brand' : 'bg-ink-faint'}`}
                  />
                  {item.q}
                </button>
                {on && <p className="pt-1 pb-4 pl-[1.375rem] text-lg leading-normal lg:hidden">{item.a}</p>}
              </li>
            );
          })}
        </ul>
      </div>
      <p
        aria-live="polite"
        className="hidden max-w-[24ch] self-start text-[1.625rem] leading-snug font-medium tracking-[-0.02em] lg:sticky lg:top-40 lg:col-start-3 lg:block"
      >
        {answer.a}
      </p>
    </section>
  );
}

/* ───────────── 마무리 ───────────── */

// 숲색 면 위에 지역 검색 대화상자를 둔다. 지역을 넣고 누르면 지도 화면이 그 지역을 찾아 옮긴다.
function ClosingSection() {
  const [region, setRegion] = useState('가평 북면');
  const { ref, rise } = useRise<HTMLElement>();
  const inputId = useId();
  const heading = rise(0);
  const dialog = rise(2);

  function submit() {
    const query = region.trim();
    navigate(query === '' ? '/map' : `/map?q=${encodeURIComponent(query)}`);
  }

  return (
    <section
      ref={ref}
      aria-labelledby="closing-title"
      className="relative overflow-hidden bg-brand-raised text-white [&_:focus-visible]:outline-white"
    >
      {/* 모닥불 영상을 흑백으로 바꿔 숲색 면 위에 낮게 깐다. 소리가 없는 꾸밈이라 스크린 리더에서 숨기고, 동작 줄이기를 켜면 보이지 않는다. */}
      <video
        autoPlay
        muted
        loop
        playsInline
        preload="metadata"
        poster="/landing/video/campfire-poster.webp"
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 size-full object-cover opacity-40 mix-blend-multiply grayscale contrast-125 motion-reduce:hidden"
      >
        <source src="/landing/video/campfire.webm" type="video/webm" />
      </video>
      <div className="relative mx-auto grid min-h-[88svh] max-w-screen-xl items-end gap-10 px-4 pt-20 pb-16 lg:grid-cols-[7fr_5fr] lg:px-8 lg:pt-24">
        <div className={heading.className} style={heading.style}>
          <h2 id="closing-title" className={`${DISPLAY_CLASS} whitespace-nowrap`}>
            이번 주말,
            <br />
            어디서 잘까요?
          </h2>
          <p className="mt-5 max-w-[34ch] text-lg text-brand-ink lg:text-xl">
            지역을 넣으면 그 일대의 박지와 야영장을 지도에서 바로 볼 수 있어요. 가입하지 않아도 돼요.
          </p>
        </div>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
          data-ui
          className={`w-full max-w-[27.5rem] self-center justify-self-center rounded-sheet bg-surface p-7 text-ink shadow-raise [&_:focus-visible]:outline-info ${dialog.className}`}
          style={dialog.style}
        >
          <h3 className="text-lg font-bold">어디로 떠나세요?</h3>
          <label htmlFor={inputId} className="mt-4 block text-sm font-semibold text-ink-muted">
            지역
          </label>
          <input
            id={inputId}
            value={region}
            onChange={(event) => setRegion(event.target.value)}
            maxLength={50}
            autoComplete="off"
            className="mt-1.5 h-12 w-full rounded-field border-[1.5px] border-ink-faint bg-surface px-4 text-base focus:border-brand"
          />
          <button type="submit" className={`${buttonClassName('primary')} group mt-5 min-h-13 w-full`}>
            지도에서 보기
            <Icon name="chevronRight" size={18} className={NUDGE_CLASS} />
          </button>
        </form>
      </div>
    </section>
  );
}
