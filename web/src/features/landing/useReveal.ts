import { useEffect, type RefObject } from 'react';

// 섹션이 화면에 들어올 때 안쪽의 [data-reveal] 요소를 차례로 떠오르게 한다.
// 숨기는 일은 스크립트가 돌고 나서, 아직 화면 아래에 있는 요소에만 한다. 그래서 스크립트가 실패해도 내용은 늘 보인다.
// 움직임 줄이기 설정이면 아무것도 숨기거나 움직이지 않는다.
// key가 바뀌면(나중에 데이터가 와서 요소가 늘면) 아직 등장하지 않은 요소를 다시 찾는다. 이미 보인 요소는 건드리지 않는다.
export function useReveal(rootRef: RefObject<HTMLElement | null>, key: unknown = null) {
  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const items = [...root.querySelectorAll<HTMLElement>('[data-reveal]')].filter((item) => item.dataset.reveal !== 'in');
    const below = items.filter((item) => item.getBoundingClientRect().top > window.innerHeight);
    for (const item of items) item.dataset.reveal = below.includes(item) ? 'wait' : 'in';

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          (entry.target as HTMLElement).dataset.reveal = 'in';
          observer.unobserve(entry.target);
        }
      },
      { rootMargin: '0px 0px -10% 0px' },
    );
    for (const item of below) observer.observe(item);
    return () => observer.disconnect();
  }, [rootRef, key]);
}
