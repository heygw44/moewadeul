import { useId, useLayoutEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { contourPaths } from './contours';
import { approach, useFrameWhileVisible, usePrefersReducedMotion } from './motion';

// 랜딩의 그림은 모두 코드로 그린 면이다. 선은 쓰지 않고, 숲색·세이지 톤을 겹쳐 능선과 등고선을 만든다.

/* ───────────── 등고선 ───────────── */

// 칸 전체를 등고선 면으로 채운다. 부모는 relative에 overflow-hidden이어야 한다.
// light는 세이지 면 위(fill·fill-strong), dark는 숲색 면 위(brand·brand-raised)다.
export function Contours({
  tone,
  seed = 1,
  levels = 9,
  className = '',
}: {
  tone: 'light' | 'dark';
  seed?: number;
  levels?: number;
  className?: string;
}) {
  const paths = useMemo(() => contourPaths({ cx: 420, cy: 330, rx: 560, ry: 400, levels, seed }), [seed, levels]);
  const fills = tone === 'light' ? ['fill-fill', 'fill-fill-strong'] : ['fill-brand', 'fill-brand-raised'];
  return (
    <svg
      viewBox="0 0 800 600"
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
      className={`pointer-events-none absolute inset-0 size-full ${className}`}
    >
      {paths.map((d, index) => (
        <path key={index} d={d} className={fills[index % 2]} />
      ))}
    </svg>
  );
}

/* ───────────── 능선과 달 ───────────── */

// 봉우리가 뾰족하고 골짜기가 둥근 능선을 사인 곡선 셋을 섞어 만든다. 같은 인자는 늘 같은 모양이다.
function ridgePath(width: number, height: number, base: number, amp: number, phase: number, freq: number): string {
  const points: string[] = [];
  for (let x = 0; x <= width; x += 12) {
    const t = x / width;
    const peak = (1 - Math.abs(Math.sin(t * Math.PI * freq + phase))) ** 1.3;
    const mid = 1 - Math.abs(Math.sin(t * Math.PI * freq * 2.3 + phase * 1.7));
    const grain = Math.sin(t * Math.PI * freq * 9 + phase * 3) * 0.5 + 0.5;
    const high = peak * 0.62 + mid * 0.26 + grain * 0.12;
    points.push(`${x} ${(base - amp * high).toFixed(1)}`);
  }
  return `M0 ${height} L${points.join(' L')} L${width} ${height} Z`;
}

type Dots = { r: number; className: string };

// 45도 격자로 놓은 망점 무늬를 얹은 면 하나다. dots가 없으면 단색 면이다.
function HalftonePath({ d, base, dots }: { d: string; base: string; dots?: Dots }) {
  const id = useId().replaceAll(':', '');
  return (
    <>
      {dots && (
        <defs>
          <pattern id={id} width={6} height={6} patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
            <circle cx={3} cy={3} r={dots.r} className={dots.className} />
          </pattern>
        </defs>
      )}
      <path d={d} className={base} />
      {dots && <path d={d} fill={`url(#${id})`} />}
    </>
  );
}

type Size = { w: number; h: number };

// 요소의 레이아웃 크기(px)를 재고, 크기가 바뀌면 다시 잰다. 변형(transform)은 크기에 넣지 않는다.
// 그림을 칸의 실제 크기로 그려야 넓은 화면에서 봉우리가 잘리거나 망점이 늘어나지 않는다.
function useBoxSize<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [size, setSize] = useState<Size | null>(null);
  useLayoutEffect(() => {
    const element = ref.current;
    if (!element) return;
    const update = () => {
      const w = element.offsetWidth;
      const h = element.offsetHeight;
      setSize((previous) => (previous && previous.w === w && previous.h === h ? previous : { w, h }));
    };
    update();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(update);
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  return { ref, size };
}

// 능선 한 겹의 모양이다. base와 amp는 칸 높이를 1로 둔 비율이고, freq는 칸이 aspect(가로/세로) 비율일 때의 봉우리 수다.
// 칸이 더 넓으면 봉우리 수를 그만큼 늘려 산의 생김새는 그대로 두고 옆으로 더 이어지게 한다.
type Ridge = { base: number; amp: number; phase: number; freq: number; fill: string; dots?: Dots };

// 능선 한 겹을 칸 크기 그대로의 좌표로 그린다. 좌표와 화면 픽셀이 같아서 잘리는 곳이 없다.
function RidgeSvg({
  size,
  ridge,
  aspect,
  className = '',
}: {
  size: Size;
  ridge: Ridge;
  aspect: number;
  className?: string;
}) {
  return (
    <svg viewBox={`0 0 ${size.w} ${size.h}`} className={`block size-full ${className}`}>
      <HalftonePath
        d={ridgePath(
          size.w,
          size.h,
          ridge.base * size.h,
          ridge.amp * size.h,
          ridge.phase,
          (ridge.freq * size.w) / size.h / aspect,
        )}
        base={ridge.fill}
        dots={ridge.dots}
      />
    </svg>
  );
}

// 첫 화면 그림을 처음 설계한 칸 비율(1440×520)이다.
const HERO_ASPECT = 1440 / 520;

// 첫 화면의 능선 네 겹이다. 뒤에서 앞 순서다.
// speed는 스크롤할 때 내려가는 정도(뒤쪽이 크다), riseFrom은 처음 나타날 때 아래에서 올라오는 거리(칸 높이 비율)다.
const HERO_LAYERS: ReadonlyArray<Ridge & { speed: number; riseFrom: string }> = [
  {
    base: 0.404,
    amp: 0.327,
    phase: 0.6,
    freq: 3.2,
    fill: 'fill-brand-strong',
    dots: { r: 1.4, className: 'fill-brand-ink' },
    speed: 0.9,
    riseFrom: '34%',
  },
  {
    base: 0.558,
    amp: 0.288,
    phase: 2.1,
    freq: 3.9,
    fill: 'fill-brand-strong',
    dots: { r: 2.2, className: 'fill-brand-ink' },
    speed: 0.6,
    riseFrom: '24%',
  },
  {
    base: 0.712,
    amp: 0.231,
    phase: 4.0,
    freq: 4.6,
    fill: 'fill-brand-ink',
    dots: { r: 2.6, className: 'fill-fill-strong' },
    speed: 0.35,
    riseFrom: '12%',
  },
  // 맨 앞 겹은 다음 섹션의 흰 바탕과 이어지므로 올라오지 않는다. 움직이면 첫 화면 아래 끝에 다른 색 띠가 보인다.
  { base: 0.869, amp: 0.154, phase: 5.2, freq: 5.4, fill: 'fill-canvas', speed: 0.12, riseFrom: '0%' },
];

// 스크롤로 해가 더 떠오르는 최대 거리(px)다. 첫 화면이 화면 위로 다 올라갔을 때 이만큼 올라가 있다.
const SUN_SCROLL_RISE = 180;

// 첫 화면 아래쪽을 덮는 그림이다. 부모는 relative에 overflow-hidden이어야 한다.
// 처음 열면 능선이 뒤에서부터 아래에서 올라오고, 해가 능선 뒤에서 떠오른다.
// 스크롤하면 해는 더 떠오르고 능선은 겹마다 다른 속도로 내려간다. 동작 줄이기를 켜면 움직이지 않고 처음 자리에 있다.
// 처음 나타나는 움직임(scene-rise)은 겉 칸에, 스크롤 움직임은 안쪽 칸에 걸어 둘이 서로 덮어쓰지 않게 한다.
export function HeroScene() {
  const reduced = usePrefersReducedMotion();
  const { ref: frameRef, size } = useBoxSize<HTMLDivElement>();
  const layerRefs = useRef<(HTMLDivElement | null)[]>([]);
  const sunRef = useRef<HTMLDivElement>(null);
  const progress = useRef(0);

  useFrameWhileVisible(
    frameRef,
    () => {
      const section = frameRef.current?.parentElement;
      if (!section) return;
      const rect = section.getBoundingClientRect();
      // 첫 화면이 화면 위로 올라간 정도(0~1)다. 맨 위에서는 0, 첫 화면이 다 지나가면 1이다.
      const target = Math.min(1, Math.max(0, -rect.top / rect.height));
      progress.current = approach(progress.current, target, 0.12);
      HERO_LAYERS.forEach((layer, index) => {
        const element = layerRefs.current[index];
        if (element) element.style.transform = `translateY(${(progress.current * layer.speed * 120).toFixed(1)}px)`;
      });
      if (sunRef.current) {
        sunRef.current.style.transform = `translateY(${(-progress.current * SUN_SCROLL_RISE).toFixed(1)}px)`;
      }
    },
    !reduced,
  );

  return (
    <div
      ref={frameRef}
      aria-hidden="true"
      className="pointer-events-none absolute inset-x-0 bottom-0 h-[52%] min-h-[19rem] lg:h-[56%]"
    >
      <div
        className="absolute top-[-16%] right-[8%] w-[min(30vw,17rem)] motion-safe:animate-scene-rise"
        style={{ '--rise-from': '70%', animationDelay: '450ms', animationDuration: '2400ms' } as CSSProperties}
      >
        <div ref={sunRef} className="will-change-transform">
          <Moon />
        </div>
      </div>
      {size &&
        HERO_LAYERS.map((layer, index) => (
          <div
            key={layer.phase}
            className="absolute inset-0 motion-safe:animate-scene-rise"
            style={
              {
                '--rise-from': layer.riseFrom,
                animationDelay: `${index * 140}ms`,
                animationDuration: '1600ms',
              } as CSSProperties
            }
          >
            <div
              ref={(element) => {
                layerRefs.current[index] = element;
              }}
              className="size-full will-change-transform"
            >
              <RidgeSvg size={size} ridge={layer} aspect={HERO_ASPECT} />
            </div>
          </div>
        ))}
    </div>
  );
}

// 세이지 면 위의 달이다. 망점 무늬로 질감을 낸다.
function Moon() {
  const id = useId().replaceAll(':', '');
  return (
    <svg viewBox="0 0 200 200" className="block w-full">
      <defs>
        <pattern id={id} width={6} height={6} patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <circle cx={3} cy={3} r={1.5} className="fill-brand-ink" />
        </pattern>
      </defs>
      <circle cx={100} cy={100} r={100} className="fill-fill" />
      <circle cx={100} cy={100} r={100} fill={`url(#${id})`} />
    </svg>
  );
}

// 카드 그림을 처음 설계한 칸 비율(480×240)과 능선 세 겹이다.
const MINI_ASPECT = 480 / 240;
const MINI_LAYERS: readonly Ridge[] = [
  {
    base: 0.417,
    amp: 0.333,
    phase: 1.0,
    freq: 2.6,
    fill: 'fill-brand-strong',
    dots: { r: 1.8, className: 'fill-brand-ink' },
  },
  { base: 0.708, amp: 0.292, phase: 3.0, freq: 3.4, fill: 'fill-brand-ink' },
  { base: 0.925, amp: 0.167, phase: 5.1, freq: 4.2, fill: 'fill-fill' },
];

// 카드 안에 들어가는 작은 숲색 풍경이다. 가장 앞 능선이 카드 아래 면(fill)과 이어진다.
// 부모는 relative에 overflow-hidden이고 배경이 brand여야 한다.
export function MiniScene() {
  const { ref, size } = useBoxSize<HTMLDivElement>();
  return (
    <div ref={ref} aria-hidden="true" className="pointer-events-none absolute inset-x-0 -bottom-px h-[78%]">
      <div className="absolute top-[6%] right-[10%] w-[24%]">
        <Moon />
      </div>
      {size &&
        MINI_LAYERS.map((layer) => (
          <RidgeSvg key={layer.phase} size={size} ridge={layer} aspect={MINI_ASPECT} className="absolute inset-0" />
        ))}
    </div>
  );
}

// 마무리 면 맨 아래에 깔아 바닥글 면(fill)과 이어 주는 능선 한 겹이다. 처음 설계한 칸 비율은 1440×200이다.
const EDGE_ASPECT = 1440 / 200;
const EDGE_RIDGE: Ridge = { base: 0.6, amp: 0.45, phase: 2.7, freq: 3.6, fill: 'fill-fill' };
export function RidgeEdge() {
  const { ref, size } = useBoxSize<HTMLDivElement>();
  return (
    <div ref={ref} aria-hidden="true" className="pointer-events-none absolute inset-x-0 bottom-0 h-28 lg:h-40">
      {size && <RidgeSvg size={size} ridge={EDGE_RIDGE} aspect={EDGE_ASPECT} />}
    </div>
  );
}
