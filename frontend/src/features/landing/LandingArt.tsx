import { useId, useLayoutEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { contourPaths } from './contours';
import { approach, useFrameWhileVisible, usePrefersReducedMotion } from './motion';

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

type Ridge = { base: number; amp: number; phase: number; freq: number; fill: string; dots?: Dots };

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

const HERO_ASPECT = 1440 / 520;

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
  { base: 0.869, amp: 0.154, phase: 5.2, freq: 5.4, fill: 'fill-canvas', speed: 0.12, riseFrom: '0%' },
];

const SUN_SCROLL_RISE = 180;

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
