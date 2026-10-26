import { createContext, Suspense, useContext, useSyncExternalStore } from 'react';
import type { AnchorHTMLAttributes, FocusEvent, MouseEvent, PointerEvent, ReactNode } from 'react';
import { RouteBoundary } from './RouteBoundary';
import { RouteFallback } from './RouteFallback';

export type RouteParams = Record<string, string>;

export interface RouteDef {
  path: string;
  render: (params: RouteParams) => ReactNode;
  // 이 경로의 화면 코드를 미리 받는다. 화면 코드를 나눈 경로만 갖고, 링크에 포인터를 올리거나 초점을 둘 때 부른다.
  preload?: () => void;
  // 이 경로가 지도 화면을 연다. 처음 방문한 주소가 지도면 지도 SDK를 먼저 받는다.
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

// useSyncExternalStore는 같은 값을 돌려받아야 다시 그리지 않으므로, 주소가 바뀔 때만 새 객체를 만든다.
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
        // 잘못 인코딩된 주소는 어떤 경로와도 맞지 않는 것으로 본다.
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

// Link가 어느 경로 표로 화면 코드를 미리 받을지 알도록 Router가 내려 준다. Router 밖의 Link는 미리 받지 않는다.
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
    // 새 탭 열기처럼 브라우저가 처리해야 하는 클릭은 가로채지 않는다.
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
  // 화면 코드를 나눈 경로는 코드가 도착할 때까지 Suspense가 받는다. 코드를 받지 못하면 RouteBoundary가 안내를 보여 준다.
  // key를 주지 않아서 /map과 /spots/1처럼 같은 화면을 쓰는 경로 사이를 옮겨도 화면 상태가 그대로 남는다.
  return (
    <RoutesContext value={routes}>
      <RouteBoundary resetKey={pathname}>
        <Suspense fallback={<RouteFallback />}>{content}</Suspense>
      </RouteBoundary>
    </RoutesContext>
  );
}
