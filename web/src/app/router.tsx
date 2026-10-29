import { createContext, Suspense, useContext, useSyncExternalStore } from 'react';
import type { AnchorHTMLAttributes, FocusEvent, MouseEvent, PointerEvent, ReactNode } from 'react';
import { RouteBoundary } from './RouteBoundary';
import { RouteFallback } from './RouteFallback';

export type RouteParams = Record<string, string>;

export interface RouteDef {
  path: string;
  render: (params: RouteParams) => ReactNode;
  preload?: () => void;
  opensMap?: boolean;
}

const listeners = new Set<() => void>();

function notify() {
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  window.addEventListener('popstate', listener);
  return () => {
    listeners.delete(listener);
    window.removeEventListener('popstate', listener);
  };
}

let cachedHref = '';
let cachedLocation = { pathname: '/', search: '', hash: '' };

function getSnapshot() {
  const { href, pathname, search, hash } = window.location;
  if (href !== cachedHref) {
    cachedHref = href;
    cachedLocation = { pathname, search, hash };
  }
  return cachedLocation;
}

export function navigate(to: string, options?: { replace?: boolean }): void {
  const target = new URL(to, window.location.href);
  if (target.href === window.location.href) return;
  if (options?.replace) {
    window.history.replaceState(null, '', target.href);
  } else {
    window.history.pushState(null, '', target.href);
    window.scrollTo(0, 0);
  }
  notify();
}

// navigate와 달리 앱의 메모리 상태를 버리고 그 주소를 처음부터 다시 연다.
export function loadPage(to: string): void {
  window.location.replace(to);
}

export function useLocation(): { pathname: string; search: string; hash: string } {
  return useSyncExternalStore(subscribe, getSnapshot);
}

function splitPath(path: string): string[] {
  return path.split('/').filter((segment) => segment !== '');
}

export function matchPath(pattern: string, pathname: string): RouteParams | null {
  const patternSegments = splitPath(pattern);
  const pathSegments = splitPath(pathname);
  if (patternSegments.length !== pathSegments.length) return null;

  const params: RouteParams = {};
  for (let i = 0; i < patternSegments.length; i++) {
    const patternSegment = patternSegments[i]!;
    const pathSegment = pathSegments[i]!;
    if (patternSegment.startsWith(':')) {
      try {
        params[patternSegment.slice(1)] = decodeURIComponent(pathSegment);
      } catch {
        return null;
      }
    } else if (patternSegment !== pathSegment) {
      return null;
    }
  }
  return params;
}

export function findRoute(routes: RouteDef[], pathname: string): RouteDef | null {
  return routes.find((route) => matchPath(route.path, pathname) !== null) ?? null;
}

const RoutesContext = createContext<RouteDef[]>([]);

type LinkProps = AnchorHTMLAttributes<HTMLAnchorElement> & { to: string; replace?: boolean };

export function Link({ to, replace, onClick, onPointerEnter, onFocus, target, ...anchorProps }: LinkProps) {
  const routes = useContext(RoutesContext);

  function preload() {
    const pathname = new URL(to, window.location.href).pathname;
    findRoute(routes, pathname)?.preload?.();
  }

  function handlePointerEnter(event: PointerEvent<HTMLAnchorElement>) {
    onPointerEnter?.(event);
    preload();
  }

  function handleFocus(event: FocusEvent<HTMLAnchorElement>) {
    onFocus?.(event);
    preload();
  }

  function handleClick(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event);
    if (
      event.defaultPrevented ||
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey ||
      target
    ) {
      return;
    }
    event.preventDefault();
    navigate(to, { replace });
  }

  return (
    <a
      {...anchorProps}
      href={to}
      target={target}
      onClick={handleClick}
      onPointerEnter={handlePointerEnter}
      onFocus={handleFocus}
    />
  );
}

export function Router({ routes, fallback }: { routes: RouteDef[]; fallback: ReactNode }) {
  const { pathname } = useLocation();
  let content = fallback;
  for (const route of routes) {
    const params = matchPath(route.path, pathname);
    if (params) {
      content = route.render(params);
      break;
    }
  }
  return (
    <RoutesContext value={routes}>
      <RouteBoundary resetKey={pathname}>
        <Suspense fallback={<RouteFallback />}>{content}</Suspense>
      </RouteBoundary>
    </RoutesContext>
  );
}
