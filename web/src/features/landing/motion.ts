import { useEffect, useRef, useState, type CSSProperties, type RefObject } from 'react';

export function useMediaQuery(media: string): boolean {
  const [matches, setMatches] = useState(() => matchMedia(media).matches);
  useEffect(() => {
    const query = matchMedia(media);
    const update = () => setMatches(query.matches);
    query.addEventListener('change', update);
    return () => query.removeEventListener('change', update);
  }, [media]);
  return matches;
}

export function usePrefersReducedMotion(): boolean {
  return useMediaQuery('(prefers-reduced-motion: reduce)');
}

export function useInView<T extends Element>() {
  const ref = useRef<T>(null);
  const [inView, setInView] = useState(() => typeof IntersectionObserver === 'undefined');

  useEffect(() => {
    const element = ref.current;
    if (!element || inView) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry?.isIntersecting) return;
        setInView(true);
        observer.disconnect();
      },
      { rootMargin: '0px 0px -10% 0px' },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [inView]);

  return { ref, inView };
}

export function riseClass(inView: boolean): string {
  return inView ? 'motion-safe:animate-rise' : 'motion-safe:opacity-0';
}

export function useFrameWhileVisible(ref: RefObject<Element | null>, step: () => void, enabled: boolean) {
  const stepRef = useRef(step);
  useEffect(() => {
    stepRef.current = step;
  });

  useEffect(() => {
    const element = ref.current;
    if (!element || !enabled) return;
    let frame = 0;
    const loop = () => {
      stepRef.current();
      frame = requestAnimationFrame(loop);
    };
    const observer = new IntersectionObserver(
      ([entry]) => {
        cancelAnimationFrame(frame);
        if (entry?.isIntersecting) frame = requestAnimationFrame(loop);
      },
      { rootMargin: '200px 0px 200px 0px' },
    );
    observer.observe(element);
    return () => {
      observer.disconnect();
      cancelAnimationFrame(frame);
    };
  }, [ref, enabled]);
}

export function approach(current: number, target: number, rate: number): number {
  const next = current + (target - current) * rate;
  return Math.abs(target - next) < 0.0005 ? target : next;
}

export function useRise<T extends Element>() {
  const { ref, inView } = useInView<T>();
  const rise = (index: number): { className: string; style: CSSProperties } => ({
    className: riseClass(inView),
    style: { animationDelay: `${index * 80}ms` },
  });
  return { ref, rise };
}
