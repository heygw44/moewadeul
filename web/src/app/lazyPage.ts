import { lazy } from 'react';
import type { ComponentType, LazyExoticComponent } from 'react';

export type LazyPage<P extends object> = LazyExoticComponent<ComponentType<P>> & { preload: () => void };

export function lazyPage<K extends string, P extends object>(
  load: () => Promise<Record<K, ComponentType<P>>>,
  name: K,
): LazyPage<P> {
  const component = lazy(() => load().then((module) => ({ default: module[name] })));
  return Object.assign(component, {
    preload: () => {
      load().catch(() => undefined);
    },
  });
}
