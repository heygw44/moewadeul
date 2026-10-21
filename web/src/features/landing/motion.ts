import { useEffect, useRef, useState, type CSSProperties, type RefObject } from 'react';

// 미디어 쿼리가 지금 맞는지 알려 주고, 창 크기나 설정이 바뀌면 다시 알려 준다.
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

// 운영체제에서 동작 줄이기를 켰는지 알려 준다. 켜면 랜딩은 움직이지 않고 모든 내용을 처음부터 보여 준다.
export function usePrefersReducedMotion(): boolean {
  return useMediaQuery('(prefers-reduced-motion: reduce)');
}

// 요소가 화면에 처음 들어왔는지 알려 준다. 한 번 들어오면 다시 숨기지 않는다.
// IntersectionObserver가 없는 브라우저에서는 처음부터 들어온 것으로 본다.
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

// 화면에 들어오기 전에는 숨겨 두고, 들어오면 아래에서 떠오르게 하는 클래스다.
// 움직임 줄이기를 켠 사용자에게는 숨기지도 움직이지도 않는다.
export function riseClass(inView: boolean): string {
  return inView ? 'motion-safe:animate-rise' : 'motion-safe:opacity-0';
}

// 요소가 화면 근처에 있는 동안만 매 프레임 step을 부른다. 스크롤에 묶인 움직임이 화면 밖에서 계산을 계속하지 않게 한다.
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

// 목표값으로 바로 뛰지 않고 남은 거리의 일부만 옮긴다. 매 프레임 부르면 스크롤이 멈출 때 부드럽게 따라붙는다.
export function approach(current: number, target: number, rate: number): number {
  const next = current + (target - current) * rate;
  return Math.abs(target - next) < 0.0005 ? target : next;
}

// 섹션이 화면에 들어오면 자식들을 순서대로 떠오르게 한다. i번째 자식은 i × 80ms 늦게 시작한다.
export function useRise<T extends Element>() {
  const { ref, inView } = useInView<T>();
  const rise = (index: number): { className: string; style: CSSProperties } => ({
    className: riseClass(inView),
    style: { animationDelay: `${index * 80}ms` },
  });
  return { ref, rise };
}
