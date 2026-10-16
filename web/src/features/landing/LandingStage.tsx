import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react';
import { fetchSpotsNearby } from '../../api/spots';
import type { NearbySpot } from '../../api/types';
import { Link } from '../../app/router';
import { Icon } from '../../components/icons';
import { createMapView } from '../../map/KakaoMap';
import type { RegionLabel } from '../../map/regionLabel';
import type { LatLng, MapView } from '../../map/types';
import { SpotListRow } from '../spot/SpotListRow';
import { useRegionName } from '../spot/useRegionName';
import {
  approach,
  clamp01,
  riseClass,
  useActiveIndex,
  useFrameWhileVisible,
  useInView,
  usePrefersReducedMotion,
} from './motion';

// 첫 화면 안내판 지도의 위치다. 경기 양평군 옥천면 일대로, 자연휴양림과 공공 야영장이 가까이 모여 있다.
const BOARD_CENTER: LatLng = { lat: 37.555, lng: 127.47 };
const BOARD_LEVEL = 8;
// 안내판에 번호를 붙여 보여 줄 장소 수와, 장소를 찾는 반경(km)이다. 지도에는 4곳을 찍고 목록에는 3곳을 적는다.
const BOARD_SPOT_COUNT = 4;
const BOARD_ROW_COUNT = 3;
const BOARD_RADIUS_KM = 20;
// 안내판이 처음 보일 때 핀을 1번부터 하나씩 짚는 시작 시각과 간격(ms)이다.
const PIN_TOUR_START_MS = 1400;
const PIN_TOUR_STEP_MS = 700;

// 제목·소개 글자 크기. 섹션마다 같은 단계를 쓴다.
const H2_CLASS = 'text-[clamp(2.5rem,5vw,4.5rem)] leading-[1.06] font-extrabold tracking-[-0.045em]';
const SUB_CLASS = 'mx-auto mt-6 max-w-[30em] text-[clamp(1.25rem,1.7vw,1.5rem)] leading-normal';
const STEP_TITLE_CLASS = 'text-[clamp(1.5rem,2.2vw,2rem)] leading-tight font-extrabold tracking-[-0.03em]';
// 처음엔 빠르게 움직이다 끝에서 천천히 멈추는 곡선이다. 등장 움직임(theme.css의 rise)과 같은 곡선을 쓴다.
const EASE_SOFT = 'ease-[cubic-bezier(0.22,1,0.36,1)]';
const TEXT_LINK_CLASS =
  'inline-flex min-h-11 items-center gap-1 text-lg font-bold text-brand underline-offset-4 hover:underline';
// 링크에 마우스를 올리면 화살표를 오른쪽으로 조금 민다.
const NUDGE_CLASS = 'transition-transform duration-300 motion-safe:group-hover:translate-x-1';
const PRIMARY_LINK_CLASS =
  'inline-flex min-h-14 items-center justify-center rounded-control bg-brand px-7 text-lg font-bold text-white transition hover:bg-brand-strong active:scale-[0.97]';

// 처음 온 사람에게 뫼와들이 하는 일을 애플 제품 페이지처럼 한 장면씩 보여 주는 랜딩이다.
// 첫 화면의 안내판은 공개 API가 돌려준 실제 장소다. 근거 섹션과 흐름 섹션의 그림은 앱 화면을 찍은 것이다.
// 장소 상세는 날씨 때문에 기상청을 부르므로 랜딩에서 부르지 않는다.
export function LandingStage() {
  return (
    <>
      <LandingHero />
      <Statement />
      <EvidenceSection />
      <FiguresSection />
      <TrustSection />
      <TrailSection />
      <ClosingSection />
    </>
  );
}

function LandingHero() {
  const reduced = usePrefersReducedMotion();
  const hostRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const boardRef = useRef<HTMLDivElement>(null);
  const scaleRef = useRef(0.92);
  const tourRef = useRef<number[]>([]);
  const [view, setView] = useState<MapView | null>(null);
  const [mapFailed, setMapFailed] = useState(false);
  const [spots, setSpots] = useState<NearbySpot[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const regionName = useRegionName(view);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const element = document.createElement('div');
    element.className = 'absolute inset-0';
    host.appendChild(element);
    let cancelled = false;
    let created: MapView | null = null;
    // 안내판 지도는 그림처럼 고정한다. 끌거나 휠을 굴리면 페이지 스크롤이 지도에 먹히기 때문이다.
    createMapView(element, { center: BOARD_CENTER, level: BOARD_LEVEL, scrollWheel: false, draggable: false }).then(
      (next) => {
        if (cancelled) {
          next.destroy();
          return;
        }
        created = next;
        setView(next);
      },
      () => {
        if (!cancelled) setMapFailed(true);
      },
    );
    return () => {
      cancelled = true;
      created?.destroy();
      element.remove();
      setView(null);
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetchSpotsNearby({ ...BOARD_CENTER, radiusKm: BOARD_RADIUS_KM, size: BOARD_SPOT_COUNT }, controller.signal).then(
      (page) => {
        setSpots(page.content);
        setStatus('ready');
      },
      () => {
        if (!controller.signal.aborted) setStatus('error');
      },
    );
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!view) return;
    view.showMarkers(
      spots.map((spot, index) => ({
        id: spot.spotId,
        kind: spot.type,
        name: spot.name,
        position: { lat: spot.lat, lng: spot.lng },
        warning: spot.parkWarning,
        closed: spot.closedNow,
        label: String(index + 1),
      })),
      [],
    );
  }, [view, spots]);

  // 목록과 지도가 같은 번호로 이어진다는 것을 보여 주려고, 처음 한 번 핀을 1번부터 차례로 골랐다가 놓는다.
  useEffect(() => {
    if (!view || spots.length === 0 || reduced) return;
    const rows = spots.slice(0, BOARD_ROW_COUNT);
    const timers = rows.map((spot, index) =>
      window.setTimeout(() => view.setSelected(spot.spotId), PIN_TOUR_START_MS + index * PIN_TOUR_STEP_MS),
    );
    timers.push(window.setTimeout(() => view.setSelected(null), PIN_TOUR_START_MS + rows.length * PIN_TOUR_STEP_MS));
    tourRef.current = timers;
    return () => timers.forEach((timer) => window.clearTimeout(timer));
  }, [view, spots, reduced]);

  // 장소 행에 마우스를 올리거나 키보드로 고르면 같은 번호의 핀을 고른 상태로 그린다.
  function pointAt(target: EventTarget) {
    if (!view || !(target instanceof Element)) return;
    const row = target.closest('li');
    const index = row?.parentElement ? Array.from(row.parentElement.children).indexOf(row) : -1;
    const spot = spots[index];
    if (!spot) return;
    tourRef.current.forEach((timer) => window.clearTimeout(timer));
    view.setSelected(spot.spotId);
  }

  // 스크롤을 내리면 안내판이 92%에서 100% 크기로 다가온다. 스크롤을 바로 따라가지 않고 매 프레임 조금씩 따라간다.
  useFrameWhileVisible(
    stageRef,
    () => {
      const target = 0.92 + clamp01(window.scrollY / (window.innerHeight * 0.75)) * 0.08;
      scaleRef.current = approach(scaleRef.current, target, 0.1);
      if (boardRef.current) boardRef.current.style.transform = `scale(${scaleRef.current})`;
    },
    !reduced,
  );

  useEffect(() => {
    if (reduced && boardRef.current) boardRef.current.style.transform = '';
  }, [reduced]);

  return (
    <section aria-labelledby="landing-title" className="overflow-hidden">
      <div className="mx-auto max-w-[73.75rem] px-4 pt-16 text-center lg:px-8 lg:pt-28">
        <h1
          id="landing-title"
          className="text-[clamp(3.25rem,7vw,6.5rem)] leading-[1.02] font-extrabold tracking-[-0.05em] text-ink"
        >
          <HeadlineLine>어디서 잘지</HeadlineLine>
          <HeadlineLine delayMs={130}>누구와 갈지</HeadlineLine>
        </h1>
        <p className={`${SUB_CLASS} text-ink-muted motion-safe:animate-rise motion-safe:[animation-delay:420ms]`}>
          공원 경계 경고와 신뢰 단계를 보고 정해요.
        </p>
        <div className="mt-9 flex flex-wrap items-center justify-center gap-x-7 gap-y-3 motion-safe:animate-rise motion-safe:[animation-delay:520ms]">
          <Link to="/map" className={PRIMARY_LINK_CLASS}>
            지도 열기
          </Link>
          <a href="#landing-evidence" className={`group ${TEXT_LINK_CLASS}`}>
            근거 먼저 보기
            <Icon name="chevronRight" size={18} className={NUDGE_CLASS} />
          </a>
        </div>
      </div>

      <div
        ref={stageRef}
        className="mx-auto mt-14 max-w-[67.5rem] px-4 pb-24 motion-safe:animate-rise motion-safe:[animation-delay:300ms] lg:mt-22 lg:px-8 lg:pb-36"
      >
        <div ref={boardRef} className="origin-top overflow-hidden rounded-media bg-surface will-change-transform">
          <div className="flex items-baseline justify-between gap-3 bg-brand px-5 py-4 text-white lg:px-7 lg:py-5">
            <p className="truncate text-xl font-extrabold tracking-[-0.025em] lg:text-2xl">{boardTitle(regionName)}</p>
            <p className="shrink-0 text-sm text-brand-ink">가까운 순</p>
          </div>
          <div className="grid lg:grid-cols-[1.35fr_1fr]">
            <div
              ref={hostRef}
              role="img"
              aria-label="안내판 지도. 목록과 같은 번호로 장소를 찍었어요."
              className={`relative h-64 bg-fill lg:h-auto lg:min-h-[26rem] ${mapFailed ? 'hidden' : ''}`}
            />
            {spots.length > 0 ? (
              <ul
                aria-label="안내판의 장소"
                onMouseOver={(event) => pointAt(event.target)}
                onFocus={(event) => pointAt(event.target)}
                onMouseLeave={() => view?.setSelected(null)}
                onBlur={() => view?.setSelected(null)}
                className="flex flex-col justify-center py-2 motion-safe:[&>li]:animate-rise motion-safe:[&>li:nth-child(2)]:[animation-delay:120ms] motion-safe:[&>li:nth-child(3)]:[animation-delay:240ms] lg:py-4"
              >
                {spots.slice(0, BOARD_ROW_COUNT).map((spot, index) => (
                  <SpotListRow
                    key={spot.spotId}
                    marker={spot}
                    number={index + 1}
                    distanceKm={spot.distanceKm}
                    size="large"
                  />
                ))}
              </ul>
            ) : (
              <p className="self-center px-5 py-8 text-base text-ink-muted lg:px-7">
                {status === 'loading'
                  ? '가까운 장소를 찾고 있어요.'
                  : status === 'error'
                    ? '장소를 불러오지 못했어요. 지도에서 찾아 주세요.'
                    : '이 근처에는 장소가 없어요. 지도에서 다른 지역을 찾아 주세요.'}
              </p>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

// 제목 한 줄을 줄 칸 아래에 숨겨 두었다가 올린다. 줄 칸이 넘친 부분을 가려서 글자가 가림막 밑에서 나온다.
function HeadlineLine({ children, delayMs = 0 }: { children: ReactNode; delayMs?: number }) {
  return (
    <span className="block overflow-hidden pb-[0.06em]">
      <span className="block motion-safe:animate-line-up" style={{ animationDelay: `${delayMs}ms` }}>
        {children}
      </span>
    </span>
  );
}

// 지역 이름에 이미 "일대"가 붙어 오는 경우(도 단위)가 있어서 두 번 붙지 않게 한다.
function boardTitle(label: RegionLabel | null): string {
  if (label?.kind !== 'named') return '가까운 장소';
  return label.name.endsWith('일대') ? label.name : `${label.name} 일대`;
}

const STATEMENT =
  '박지에는 공원 경계 경고와 경계 자료의 기준일이 붙어요. 함께 갈 사람에게는 본인확인 여부와 동행 후기가 붙어요. 뫼와들에서는 그 둘을 보고 정해요.';

// 큰 문장 하나가 스크롤에 맞춰 앞 단어부터 옅은 회색에서 먹색으로 채워진다.
function Statement() {
  const reduced = usePrefersReducedMotion();
  const textRef = useRef<HTMLParagraphElement>(null);
  const wordRefs = useRef<(HTMLSpanElement | null)[]>([]);
  const fillRef = useRef(0);
  const litRef = useRef(-1);
  const words = STATEMENT.split(' ');

  useFrameWhileVisible(
    textRef,
    () => {
      const text = textRef.current;
      if (!text) return;
      const rect = text.getBoundingClientRect();
      const target = clamp01((window.innerHeight * 0.85 - rect.top) / (rect.height + window.innerHeight * 0.4));
      fillRef.current = approach(fillRef.current, target, 0.12);
      const lit = Math.round(fillRef.current * words.length);
      if (lit === litRef.current) return;
      litRef.current = lit;
      wordRefs.current.forEach((word, index) => {
        if (word) word.dataset.lit = String(index < lit);
      });
    },
    !reduced,
  );

  return (
    <section aria-label="뫼와들이 하는 일" className="bg-surface py-24 lg:py-40">
      <div className="mx-auto max-w-[73.75rem] px-4 lg:px-8">
        <p
          ref={textRef}
          className="max-w-[24em] text-[1.375rem] leading-[1.45] font-bold tracking-[-0.025em] lg:text-[clamp(1.625rem,2.6vw,2.25rem)]"
        >
          {words.map((word, index) => (
            <Fragment key={index}>
              <span
                ref={(element) => {
                  wordRefs.current[index] = element;
                }}
                className={
                  reduced
                    ? 'text-ink'
                    : `text-ink-faint transition-colors duration-700 ${EASE_SOFT} data-[lit=true]:text-ink`
                }
              >
                {word}
              </span>{' '}
            </Fragment>
          ))}
        </p>
      </div>
    </section>
  );
}

// 근거 무대 그림(박지 상세 화면)에서 단계마다 짚을 부분이다. focus는 그림 높이·너비를 100으로 본 위치와 크기이고,
// crop은 휴대폰에서 단계마다 그림을 잘라 보일 세로 위치다.
const EVIDENCE_STEPS = [
  {
    title: '공원 경계 안일 수 있다고 알려요',
    body: '한계령 능선 아래 공터는 설악산국립공원 경계 안으로 판정됐어요. 경고에는 지정 야영장 밖 야영과 취사가 금지된다는 안내도 들어가요.',
    chip: '설악산국립공원',
    warning: true,
    focus: { top: 13.8, height: 25.6, left: 3.2, width: 93.6 },
    crop: '18%',
    alt: '박지 상세의 공원 경계 경고 상자.',
  },
  {
    title: '어디서 가져온 경계인지 적어요',
    body: '경고마다 경계 자료의 출처와 기준일을 붙여요. 공원 경계는 법적 효력이 없는 참고 자료라서 정확한 경계는 공식 고시 도면에서 확인해야 해요.',
    chip: '한국보호지역통합DB(KDPA) · 2025-12-31',
    warning: false,
    focus: { top: 31, height: 7, left: 9.6, width: 85 },
    crop: '29%',
    alt: '경고 상자 안의 출처와 기준일 줄.',
  },
  {
    title: '경고가 붙은 곳으로는 동행을 모으지 않아요',
    body: '베이스캠프 열기 버튼이 꺼져요. 화면을 거치지 않고 바로 요청해도 막혀요.',
    chip: '이 장소에서 베이스캠프 열기 · 꺼짐',
    warning: false,
    focus: { top: 86.6, height: 10.2, left: 2.4, width: 95.2 },
    crop: '100%',
    alt: '꺼진 "이 장소에서 베이스캠프 열기" 버튼과 그 이유.',
  },
];
const EVIDENCE_IMAGE = '/landing/spot-detail-warning.webp';
const NO_FOCUS = { top: 0, height: 0, left: 0, width: 0 };

// 박지 상세 화면을 붙잡아 두고, 근거 세 단계를 지날 때마다 그 부분으로 화면을 옮겨 강조한다.
// 휴대폰에서는 붙잡아 둘 자리가 없어서 단계마다 해당 부분을 자른 그림을 둔다.
function EvidenceSection() {
  const { ref: leadRef, inView: leadInView } = useInView<HTMLDivElement>();
  const stepRefs = useRef<(HTMLLIElement | null)[]>([]);
  const active = useActiveIndex(stepRefs);
  const stageRef = useRef<HTMLDivElement>(null);
  const imageRef = useRef<HTMLImageElement>(null);
  const [panY, setPanY] = useState(0);
  const focus = EVIDENCE_STEPS[active]?.focus ?? NO_FOCUS;

  // 강조할 부분이 무대 가운데 오도록 그림을 옮긴다. 그림 끝을 넘어가지는 않는다.
  useEffect(() => {
    function measure() {
      const stage = stageRef.current;
      const image = imageRef.current;
      if (!stage || !image) return;
      const imageHeight = image.getBoundingClientRect().height;
      if (!imageHeight) return;
      const center = ((focus.top + focus.height / 2) / 100) * imageHeight;
      setPanY(Math.max(0, Math.min(imageHeight - stage.clientHeight, center - stage.clientHeight / 2)));
    }
    measure();
    const image = imageRef.current;
    image?.addEventListener('load', measure);
    window.addEventListener('resize', measure);
    return () => {
      image?.removeEventListener('load', measure);
      window.removeEventListener('resize', measure);
    };
  }, [focus]);

  return (
    <section
      id="landing-evidence"
      aria-labelledby="landing-evidence-title"
      className="scroll-mt-16 bg-surface pt-6 pb-24 lg:pb-40"
    >
      <div className="mx-auto max-w-[73.75rem] px-4 lg:px-8">
        <div ref={leadRef} className="text-center">
          <h2 id="landing-evidence-title" className={`${H2_CLASS} text-ink ${riseClass(leadInView)}`}>
            근거부터 봐요
          </h2>
          <p className={`${SUB_CLASS} text-ink-muted ${riseClass(leadInView)} motion-safe:[animation-delay:90ms]`}>
            박지를 등록하면 그 자리가 공원 경계와 겹치는지 바로 판정해요.
          </p>
        </div>

        <div className="mt-10 grid lg:mt-24 lg:grid-cols-2 lg:gap-20">
          <ol>
            {EVIDENCE_STEPS.map((step, index) => (
              <li
                key={step.title}
                ref={(element) => {
                  stepRefs.current[index] = element;
                }}
                data-on={index === active}
                className={`flex flex-col py-7 lg:min-h-[60svh] lg:justify-center lg:py-0 lg:opacity-25 lg:transition-opacity lg:duration-700 lg:data-[on=true]:opacity-100 lg:motion-reduce:opacity-100 ${EASE_SOFT}`}
              >
                <h3 className={`${STEP_TITLE_CLASS} text-ink`}>{step.title}</h3>
                <p className="mt-3 max-w-[28em] text-lg text-ink-muted">{step.body}</p>
                <p
                  className={`mt-5 self-start rounded-control px-3 py-2 font-mono text-[0.9375rem] font-bold ${
                    step.warning ? 'bg-warning-soft text-warning' : 'bg-canvas text-ink'
                  }`}
                >
                  {step.chip}
                </p>
                <div className="mt-5 h-60 overflow-hidden rounded-media bg-canvas lg:hidden">
                  <img
                    src={EVIDENCE_IMAGE}
                    alt={step.alt}
                    loading="lazy"
                    className="size-full object-cover"
                    style={{ objectPosition: `0 ${step.crop}` }}
                  />
                </div>
              </li>
            ))}
          </ol>

          <div
            ref={stageRef}
            aria-hidden="true"
            className="sticky top-28 hidden h-[calc(100svh-11rem)] max-h-[45rem] overflow-hidden rounded-media bg-canvas lg:block"
          >
            <div
              className={`absolute inset-x-0 top-0 transition-transform duration-[1200ms] motion-reduce:transition-none ${EASE_SOFT}`}
              style={{ transform: `translateY(${-panY}px)` }}
            >
              <img ref={imageRef} src={EVIDENCE_IMAGE} alt="" className="w-full" />
              <span
                className={`absolute rounded-[0.625rem] border-[3px] border-brand transition-[top,height,left,width] duration-[1200ms] motion-reduce:transition-none ${EASE_SOFT}`}
                style={{
                  top: `${focus.top}%`,
                  height: `${focus.height}%`,
                  left: `${focus.left}%`,
                  width: `${focus.width}%`,
                }}
              />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

// 소개하는 숫자는 모두 정책 값이다(반경 검색 최대 반경, 베이스캠프 정원, 신뢰 회원의 '다시 동행' 비율).
const FIGURES = [
  { value: '50km', text: '반경 검색은 최대 50km 안에서 가까운 순으로 찾아요.' },
  { value: '2~6명', text: '베이스캠프 정원이에요. 캠프 리더도 이 안에 들어가요.' },
  { value: '80%', text: '신뢰 회원은 함께 간 사람의 80% 이상에게서 다시 동행하고 싶다는 답을 받았어요.' },
];

function FiguresSection() {
  const { ref, inView } = useInView<HTMLDivElement>();
  return (
    <section aria-labelledby="landing-figures-title" className="py-24 lg:py-40">
      <div ref={ref} className="mx-auto max-w-[73.75rem] px-4 text-center lg:px-8">
        <h2 id="landing-figures-title" className={`${H2_CLASS} text-ink ${riseClass(inView)}`}>
          숫자로 정해 둔 기준
        </h2>
        <ul className="mx-auto mt-12 grid max-w-[60rem] gap-10 sm:grid-cols-3 lg:mt-22">
          {FIGURES.map((figure, index) => (
            <li key={figure.value} className={riseClass(inView)} style={{ animationDelay: `${120 + index * 110}ms` }}>
              <p className="font-mono text-[clamp(2.5rem,5vw,4.5rem)] leading-none font-extrabold tracking-[-0.05em] text-ink">
                {figure.value}
              </p>
              <p className="mx-auto mt-4 max-w-[14em] text-lg text-ink-muted">{figure.text}</p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

// 신뢰 단계마다 무엇을 뜻하는지와 그 단계에서 할 수 있는 일이다. 단계는 서버가 매번 계산하고, 여기서는 기준만 소개한다.
const TRUST_LEVELS = [
  { name: '이메일 인증', meaning: '이메일 인증을 마친 회원이에요.', can: '커뮤니티 글과 기록을 남겨요' },
  {
    name: '본인확인 · 성인',
    meaning: '본인확인을 마친 성인 회원이에요. 연령대와 동성 조건은 이 정보로만 따져요.',
    can: '베이스캠프를 열거나 합류해요',
  },
  {
    name: '신뢰 회원',
    meaning:
      '동행을 3번 이상 마친 회원이에요. 함께 간 사람의 80% 이상이 다시 동행하고 싶다고 했고 최근 180일 동안 제재를 받지 않았어요.',
    can: '단계 2 조건의 캠프에 합류해요',
  },
];

// 숲색 면에 큰 숫자 하나를 붙잡아 두고, 단계 설명을 지날 때마다 숫자를 바꾼다.
function TrustSection() {
  const reduced = usePrefersReducedMotion();
  const { ref: leadRef, inView: leadInView } = useInView<HTMLDivElement>();
  const levelRefs = useRef<(HTMLLIElement | null)[]>([]);
  const active = useActiveIndex(levelRefs);

  return (
    <section aria-labelledby="landing-trust-title" className="bg-brand py-24 text-white lg:py-40">
      <div className="mx-auto max-w-[73.75rem] px-4 lg:px-8">
        <div ref={leadRef} className="text-center">
          <h2 id="landing-trust-title" className={`${H2_CLASS} ${riseClass(leadInView)}`}>
            함께 갈 사람은
            <br />
            단계로 봐요
          </h2>
          <p className={`${SUB_CLASS} text-brand-ink ${riseClass(leadInView)} motion-safe:[animation-delay:90ms]`}>
            신뢰 단계는 볼 때마다 새로 계산해요. 제재를 받으면 바로 내려가요.
          </p>
        </div>

        <div className="mt-10 grid lg:mt-16 lg:grid-cols-2 lg:gap-20">
          <div
            aria-hidden="true"
            className="sticky top-36 hidden h-[min(60svh,32.5rem)] items-center justify-center lg:flex"
          >
            <TrustDigit value={active} reduced={reduced} />
          </div>
          <ol>
            {TRUST_LEVELS.map((level, index) => (
              <li
                key={level.name}
                ref={(element) => {
                  levelRefs.current[index] = element;
                }}
                data-on={index === active}
                className={`grid grid-cols-[5.5rem_minmax(0,1fr)] gap-x-4 gap-y-1.5 py-6 lg:flex lg:min-h-[52svh] lg:flex-col lg:justify-center lg:gap-3 lg:py-0 lg:opacity-30 lg:transition-opacity lg:duration-700 lg:data-[on=true]:opacity-100 lg:motion-reduce:opacity-100 ${EASE_SOFT}`}
              >
                <span
                  aria-hidden="true"
                  className="row-span-3 font-mono text-[5.5rem] leading-[0.9] font-extrabold tracking-[-0.05em] lg:hidden"
                >
                  {index}
                </span>
                <h3 className={STEP_TITLE_CLASS}>
                  <span className="sr-only">신뢰 단계 {index}, </span>
                  {level.name}
                </h3>
                <p className="max-w-[28em] text-lg text-brand-ink">{level.meaning}</p>
                <p className="text-lg font-bold">{level.can}</p>
              </li>
            ))}
          </ol>
        </div>
      </div>
    </section>
  );
}

const DIGIT_SPAN_CLASS = `absolute inset-0 grid place-items-center transition-[transform,opacity] duration-1000 ${EASE_SOFT}`;

// 큰 숫자를 바꿀 때 옛 숫자는 흐려지며 빠지고 새 숫자가 반대쪽에서 겹쳐 들어온다. 스크롤을 거꾸로 올리면 방향도 반대다.
// 숫자 요소는 이 효과 안에서만 만들고 지운다. React는 빈 상자만 그린다.
function TrustDigit({ value, reduced }: { value: number; reduced: boolean }) {
  const boxRef = useRef<HTMLDivElement>(null);
  const shownRef = useRef<number | null>(null);

  useEffect(() => {
    const box = boxRef.current;
    if (!box) return;
    const previous = shownRef.current;
    if (previous === value) return;
    shownRef.current = value;

    const next = document.createElement('span');
    next.className = DIGIT_SPAN_CLASS;
    next.textContent = String(value);
    const old = box.lastElementChild instanceof HTMLElement ? box.lastElementChild : null;
    if (previous === null || reduced || !old) {
      box.replaceChildren(next);
      return;
    }

    const shift = value > previous ? 35 : -35;
    next.style.transform = `translateY(${shift}%)`;
    next.style.opacity = '0';
    box.append(next);
    let frame = requestAnimationFrame(() => {
      frame = requestAnimationFrame(() => {
        next.style.transform = '';
        next.style.opacity = '';
        old.style.transform = `translateY(${-shift}%)`;
        old.style.opacity = '0';
      });
    });
    const timer = window.setTimeout(() => old.remove(), 1000);
    return () => {
      cancelAnimationFrame(frame);
      window.clearTimeout(timer);
      old.remove();
    };
  }, [value, reduced]);

  return (
    <div
      ref={boxRef}
      className="relative h-[1.1em] w-[0.75em] overflow-y-clip font-mono text-[clamp(13.75rem,26vw,22.5rem)] leading-none font-extrabold"
    />
  );
}

function TrailSection() {
  const { ref: leadRef, inView: leadInView } = useInView<HTMLDivElement>();
  const { ref: listRef, inView: listInView } = useInView<HTMLOListElement>();
  return (
    <section aria-labelledby="landing-trail-title" className="py-24 lg:py-40">
      <div className="mx-auto max-w-[73.75rem] px-4 lg:px-8">
        <div ref={leadRef} className="text-center">
          <h2 id="landing-trail-title" className={`${H2_CLASS} text-ink ${riseClass(leadInView)}`}>
            장소에서 동행,
            <br />
            행사까지
          </h2>
          <p className={`${SUB_CLASS} text-ink-muted ${riseClass(leadInView)} motion-safe:[animation-delay:90ms]`}>
            박지를 찾은 그 화면에서 그곳으로 가는 베이스캠프와 공식 행사로 넘어가요.
          </p>
        </div>

        <ol ref={listRef} className="mt-12 grid gap-14 lg:mt-22 lg:grid-cols-3 lg:gap-6">
          <TrailStop
            order={0}
            inView={listInView}
            title="장소를 찾고"
            to="/map"
            linkText="지도 열기"
            picture={
              <img
                src="/landing/map.webp"
                alt="지도 화면. 강원 영월군 한반도면 일대에 번호 핀 1·2가 있어요."
                loading="lazy"
                className="size-full object-cover object-top"
              />
            }
          >
            지도에서 장소를 고르면 날씨·일몰·예상 인원이 함께 나와요.
          </TrailStop>
          <TrailStop
            order={1}
            inView={listInView}
            title="베이스캠프에 합류하고"
            to="/basecamps"
            linkText="베이스캠프 보기"
            picture={
              <img
                src="/landing/basecamp.webp"
                alt="베이스캠프 상세 화면. 방태산 단풍 보러 1박, 10월 24일 출발, 2/4명이에요."
                loading="lazy"
                className="size-full object-cover object-top"
              />
            }
          >
            같은 날 같은 장소로 가는 2~6명 모임이에요. 확정된 멤버에게만 연락 수단이 열려요.
          </TrailStop>
          <TrailStop
            order={2}
            inView={listInView}
            title="공식 행사에 참여해요"
            to="/programs"
            linkText="행사 보기"
            picture={<ProgramRulesCard />}
          >
            운영진이 여는 백패킹 행사예요. 선착순으로 받고 빈자리가 생기면 알림을 보내요.
          </TrailStop>
        </ol>
      </div>
    </section>
  );
}

function TrailStop({
  order,
  inView,
  title,
  to,
  linkText,
  picture,
  children,
}: {
  order: number;
  inView: boolean;
  title: string;
  to: string;
  linkText: string;
  picture: ReactNode;
  children: ReactNode;
}) {
  return (
    <li className={`group flex flex-col ${riseClass(inView)}`} style={{ animationDelay: `${order * 120}ms` }}>
      <div className="mb-7 aspect-[4/3] overflow-hidden rounded-media bg-fill lg:aspect-[4/5]">
        {/* 그림에 마우스를 올리면 천천히 조금 커진다. 앱 화면이든 정책 판이든 같은 움직임이다 */}
        <div
          className={`size-full transition-transform duration-[1200ms] motion-safe:group-hover:scale-[1.03] ${EASE_SOFT}`}
        >
          {picture}
        </div>
      </div>
      <h3 className={`${STEP_TITLE_CLASS} text-ink`}>{title}</h3>
      <p className="mt-2.5 max-w-[22em] text-lg text-ink-muted">{children}</p>
      <Link to={to} className={`group/link mt-3 self-start ${TEXT_LINK_CLASS}`}>
        {linkText}
        <Icon
          name="chevronRight"
          size={18}
          className="transition-transform duration-300 motion-safe:group-hover/link:translate-x-1"
        />
      </Link>
    </li>
  );
}

// 공식 행사 정류장의 그림 자리에 둘 판이다. 행사 화면 대신 신청 규칙(정책 값)을 앱의 판 모양으로 보여 준다.
const PROGRAM_RULES = [
  ['신청', '1인 1건'],
  ['결제 기한', '신청 뒤 15분'],
  ['환불 취소', '시작 3일 전까지'],
  ['빈자리', '생기면 알림'],
];

function ProgramRulesCard() {
  return (
    <div className="flex size-full flex-col bg-canvas">
      <div className="bg-brand px-6 py-6 text-white">
        <p className="text-[0.9375rem] text-brand-ink">공식 행사 신청</p>
        <p className="mt-1 text-2xl font-extrabold tracking-[-0.025em]">선착순으로 받아요</p>
      </div>
      <dl className="grid gap-2.5 p-5 text-[0.9375rem]">
        {PROGRAM_RULES.map(([label, value]) => (
          <div key={label} className="flex justify-between gap-3 rounded-[0.5rem] bg-surface px-4 py-3.5">
            <dt className="text-ink-muted">{label}</dt>
            <dd className="font-mono font-bold text-ink">{value}</dd>
          </div>
        ))}
      </dl>
      <p
        aria-hidden="true"
        className="mx-5 mt-auto mb-5 flex min-h-13 items-center justify-center rounded-control bg-brand text-lg font-bold text-white"
      >
        신청하기
      </p>
    </div>
  );
}

function ClosingSection() {
  const { ref, inView } = useInView<HTMLDivElement>();
  return (
    <section aria-labelledby="landing-closing-title" className="pt-16 pb-28 lg:pt-28 lg:pb-44">
      <div ref={ref} className="mx-auto max-w-[73.75rem] px-4 text-center lg:px-8">
        <h2 id="landing-closing-title" className={`${H2_CLASS} text-ink ${riseClass(inView)}`}>
          이번 주말,
          <br />
          어디서 잘까요?
        </h2>
        <p className={`${SUB_CLASS} text-ink-muted ${riseClass(inView)} motion-safe:[animation-delay:90ms]`}>
          가입하지 않아도 지도와 장소 정보는 볼 수 있어요.
        </p>
        <div
          className={`mt-10 flex flex-wrap items-center justify-center gap-x-7 gap-y-3 ${riseClass(inView)} motion-safe:[animation-delay:180ms]`}
        >
          <Link to="/map" className={PRIMARY_LINK_CLASS}>
            지도 열기
          </Link>
          <Link to="/signup" className={`group ${TEXT_LINK_CLASS}`}>
            회원가입
            <Icon name="chevronRight" size={18} className={NUDGE_CLASS} />
          </Link>
        </div>
      </div>
    </section>
  );
}
