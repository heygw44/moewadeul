import { lazy } from 'react';
import type { ComponentType, LazyExoticComponent } from 'react';

export type LazyPage<P extends object> = LazyExoticComponent<ComponentType<P>> & { preload: () => void };

// 화면 코드를 따로 받는 화면이다. 화면 컴포넌트는 모듈의 이름 있는 export라서 이름을 함께 받는다.
// preload는 같은 import()를 미리 불러서 파일을 받아 둔다. React.lazy가 나중에 같은 import()를 부르면 받아 둔 모듈을 쓴다.
export function lazyPage<K extends string, P extends object>(
  load: () => Promise<Record<K, ComponentType<P>>>,
  name: K,
): LazyPage<P> {
  const component = lazy(() => load().then((module) => ({ default: module[name] })));
  return Object.assign(component, {
    preload: () => {
      // 미리 받기가 실패해도 화면이 실제로 필요할 때 다시 시도하고 거기서 오류를 다루므로 여기서는 삼킨다.
      load().catch(() => undefined);
    },
  });
}
