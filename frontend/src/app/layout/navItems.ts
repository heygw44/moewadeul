import type { IconName } from '../../components/iconPaths';
import { withNext } from '../session/nextPath';

export type NavKey = 'map' | 'basecamps' | 'footprints' | 'community' | 'programs';

export type NavItem = {
  key: NavKey;
  label: string;
  icon: IconName;
  needsLogin: boolean;
  path: string;
};

export const NAV_ITEMS: readonly NavItem[] = [
  { key: 'map', label: '지도', icon: 'map', needsLogin: false, path: '/map' },
  { key: 'basecamps', label: '베이스캠프', icon: 'tent', needsLogin: false, path: '/basecamps' },
  { key: 'footprints', label: '피드', icon: 'footprints', needsLogin: false, path: '/memories' },
  { key: 'community', label: '커뮤니티', icon: 'chat', needsLogin: false, path: '/community' },
  { key: 'programs', label: '행사', icon: 'calendar', needsLogin: false, path: '/programs' },
];

export function navTarget(item: NavItem, signedIn: boolean): string {
  return item.needsLogin && !signedIn ? withNext('/login', item.path) : item.path;
}

export function activeNavKey(pathname: string): NavKey | null {
  if (pathname === '/map' || pathname.startsWith('/spots/') || pathname.startsWith('/bakjis/')) return 'map';
  if (pathname === '/basecamps' || pathname.startsWith('/basecamps/')) return 'basecamps';
  if (
    pathname === '/memories' ||
    pathname.startsWith('/memories/') ||
    pathname === '/me/memories' ||
    /^\/members\/[^/]+\/memories$/.test(pathname)
  ) {
    return 'footprints';
  }
  if (pathname === '/community' || pathname.startsWith('/community/')) return 'community';
  if (pathname === '/programs' || pathname.startsWith('/programs/')) return 'programs';
  return null;
}
