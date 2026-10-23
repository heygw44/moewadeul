import { useId, useMemo, useRef } from 'react';
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

const SCENE_WIDTH = 1440;
const SCENE_HEIGHT = 520;

// 첫 화면의 능선 네 겹이다. speed가 클수록 스크롤에서 더 많이 내려간다(뒤쪽이 크고 앞쪽이 작다).
const HERO_LAYERS = [
  {
    d: ridgePath(SCENE_WIDTH, SCENE_HEIGHT, 210, 170, 0.6, 3.2),
    base: 'fill-brand-strong',
    dots: { r: 1.4, className: 'fill-brand-ink' },
    speed: 0.9,
  },
  {
    d: ridgePath(SCENE_WIDTH, SCENE_HEIGHT, 290, 150, 2.1, 3.9),
    base: 'fill-brand-strong',
    dots: { r: 2.2, className: 'fill-brand-ink' },
    speed: 0.6,
  },
  {
    d: ridgePath(SCENE_WIDTH, SCENE_HEIGHT, 370, 120, 4.0, 4.6),
    base: 'fill-brand-ink',
    dots: { r: 2.6, className: 'fill-fill-strong' },
    speed: 0.35,
  },
  { d: ridgePath(SCENE_WIDTH, SCENE_HEIGHT, 452, 80, 5.2, 5.4), base: 'fill-canvas', speed: 0.12 },
] as const;

// 첫 화면 아래쪽을 덮는 그림이다. 부모는 relative에 overflow-hidden이어야 한다.
// 스크롤하면 능선이 겹마다 다른 속도로 내려가고 달이 더 빨리 가라앉는다. 동작 줄이기를 켜면 가만히 있다.
export function HeroScene() {
  const reduced = usePrefersReducedMotion();
  const frameRef = useRef<HTMLDivElement>(null);
  const layerRefs = useRef<(HTMLDivElement | null)[]>([]);
  const moonRef = useRef<HTMLDivElement>(null);
  const progress = useRef(0);

  useFrameWhileVisible(
    frameRef,
    () => {
      const frame = frameRef.current;
      if (!frame) return;
      const rect = frame.getBoundingClientRect();
      // 그림 아래 끝이 화면 아래에서 위로 올라간 정도(화면 높이 단위, 0~1)다.
      const target = Math.min(1, Math.max(0, (innerHeight - rect.bottom) / innerHeight));
      progress.current = approach(progress.current, target, 0.1);
      HERO_LAYERS.forEach((layer, index) => {
        const element = layerRefs.current[index];
        if (element) element.style.transform = `translateY(${(progress.current * layer.speed * 120).toFixed(1)}px)`;
      });
      if (moonRef.current) moonRef.current.style.transform = `translateY(${(progress.current * 220).toFixed(1)}px)`;
    },
    !reduced,
  );

  return (
    <div
      ref={frameRef}
      aria-hidden="true"
      className="pointer-events-none absolute inset-x-0 bottom-0 h-[52%] min-h-[19rem] lg:h-[56%]"
    >
      <div ref={moonRef} className="absolute top-[-16%] right-[8%] w-[min(30vw,17rem)] will-change-transform">
        <Moon />
      </div>
      {HERO_LAYERS.map((layer, index) => (
        <div
          key={layer.speed}
          ref={(element) => {
            layerRefs.current[index] = element;
          }}
          className="absolute inset-0 will-change-transform"
        >
          <svg
            viewBox={`0 0 ${SCENE_WIDTH} ${SCENE_HEIGHT}`}
            preserveAspectRatio="xMidYMax slice"
            className="size-full"
          >
            <HalftonePath d={layer.d} base={layer.base} dots={'dots' in layer ? layer.dots : undefined} />
          </svg>
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

const MINI_WIDTH = 480;
const MINI_HEIGHT = 240;
const MINI_LAYERS = [
  {
    d: ridgePath(MINI_WIDTH, MINI_HEIGHT, 100, 80, 1.0, 2.6),
    base: 'fill-brand-strong',
    dots: { r: 1.8, className: 'fill-brand-ink' },
  },
  { d: ridgePath(MINI_WIDTH, MINI_HEIGHT, 170, 70, 3.0, 3.4), base: 'fill-brand-ink' },
  { d: ridgePath(MINI_WIDTH, MINI_HEIGHT, 222, 40, 5.1, 4.2), base: 'fill-fill' },
] as const;

// 카드 안에 들어가는 작은 숲색 풍경이다. 가장 앞 능선이 카드 아래 면(fill)과 이어진다.
// 부모는 relative에 overflow-hidden이고 배경이 brand여야 한다.
export function MiniScene() {
  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 -bottom-px h-[78%]">
      <div className="absolute top-[6%] right-[10%] w-[24%]">
        <Moon />
      </div>
      {MINI_LAYERS.map((layer) => (
        <svg
          key={layer.d}
          viewBox={`0 0 ${MINI_WIDTH} ${MINI_HEIGHT}`}
          preserveAspectRatio="xMidYMax slice"
          className="absolute inset-0 size-full"
        >
          <HalftonePath d={layer.d} base={layer.base} dots={'dots' in layer ? layer.dots : undefined} />
        </svg>
      ))}
    </div>
  );
}

// 마무리 면 맨 아래에 깔아 바닥글 면(fill)과 이어 주는 능선 한 겹이다.
const EDGE_D = ridgePath(SCENE_WIDTH, 200, 120, 90, 2.7, 3.6);
export function RidgeEdge() {
  return (
    <svg
      viewBox={`0 0 ${SCENE_WIDTH} 200`}
      preserveAspectRatio="xMidYMax slice"
      aria-hidden="true"
      className="pointer-events-none absolute inset-x-0 bottom-0 h-28 w-full lg:h-40"
    >
      <path d={EDGE_D} className="fill-fill" />
    </svg>
  );
}
