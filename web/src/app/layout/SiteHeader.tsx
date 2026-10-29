import { useEffect, useState } from 'react';

import { BrandMark } from '../../components/BrandMark';
import { buttonClassName } from '../../components/buttonClass';
import { Icon } from '../../components/icons';
import { AccountChip } from '../../features/member/AccountChip';
import { withNext } from '../session/nextPath';
import { useSession } from '../session/session';
import { NotificationBellLink, UnreadBadge } from '../../features/notification/NotificationLink';
import { notificationLabel, useUnreadCount } from '../../features/notification/unreadCount';
import { Link, useLocation } from '../router';
import { activeNavKey, NAV_ITEMS } from './navItems';

type SiteHeaderProps = {
  mobileAccount?: boolean;
  minimal?: boolean;
  className?: string;
};

const PRIMARY_KEYS = new Set(['map', 'basecamps', 'footprints', 'community', 'programs']);
const PRIMARY_ITEMS = NAV_ITEMS.filter((item) => PRIMARY_KEYS.has(item.key));

export function SiteHeader({ mobileAccount = false, minimal = false, className = '' }: SiteHeaderProps) {
  const { pathname, search } = useLocation();
  const session = useSession();
  const count = useUnreadCount();
  const signedIn = session.status === 'authenticated' && session.me !== null;
  const active = activeNavKey(pathname);
  const [openedAt, setOpenedAt] = useState<string | null>(null);
  const menuOpen = openedAt === pathname;
  const scrolled = useScrolled();
  const showLine = scrolled || menuOpen;

  useEffect(() => {
    if (!menuOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpenedAt(null);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [menuOpen]);

  return (
    <header
      className={`sticky top-0 z-20 border-b bg-canvas text-ink transition-colors ${showLine ? 'border-line' : 'border-transparent'} ${className}`}
    >
      <div className="mx-auto flex min-h-14 max-w-screen-xl items-center gap-3 px-4 lg:min-h-16 lg:gap-10">
        <Link to="/" className="shrink-0 text-ink">
          <BrandMark className="text-[1.3125rem]" />
        </Link>

        {!minimal && (
          <nav aria-label="주 메뉴" className="hidden items-center gap-1.5 lg:flex">
            {PRIMARY_ITEMS.map((item) => {
              const current = active === item.key;
              return (
                <Link
                  key={item.key}
                  to={item.path}
                  aria-current={current ? 'page' : undefined}
                  className={[
                    'inline-flex min-h-[2.375rem] items-center rounded-control px-3.5 text-[0.9375rem] font-semibold transition-colors',
                    current ? 'bg-ink text-white' : 'text-ink-muted hover:bg-fill hover:text-ink',
                  ].join(' ')}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>
        )}

        {!minimal && (
          <div className="ml-auto flex min-w-0 items-center gap-2">
            {signedIn && (
              <Link
                to="/me/notifications"
                aria-label={notificationLabel(count)}
                aria-current={pathname === '/me/notifications' ? 'page' : undefined}
                className="hidden min-h-11 items-center gap-2 rounded-control px-3 text-ink hover:bg-fill lg:inline-flex"
              >
                <Icon name="bell" size={20} />
                <UnreadBadge count={count} />
              </Link>
            )}
            <NotificationBellLink className="inline-flex min-h-11 min-w-11 items-center justify-center gap-1 rounded-control px-2 text-ink hover:bg-fill lg:hidden" />
            {!mobileAccount && signedIn && session.me !== null && (
              <Link
                to="/me"
                aria-label={`내 정보: ${session.me.nickname}`}
                aria-current={pathname === '/me' ? 'page' : undefined}
                className="inline-flex min-h-11 min-w-11 items-center justify-center lg:hidden"
              >
                <span
                  aria-hidden="true"
                  className="grid size-9 place-items-center rounded-full bg-fill-strong text-sm font-bold text-brand-strong"
                >
                  {Array.from(session.me.nickname)[0]}
                </span>
              </Link>
            )}
            <div className={mobileAccount ? 'flex min-w-0' : 'hidden min-w-0 lg:flex'}>
              <AccountChip />
            </div>
            {!mobileAccount && !signedIn && session.status !== 'loading' && (
              <div className="inline-flex items-center gap-1 lg:hidden">
                <Link
                  to={withNext('/login', pathname + search)}
                  className="inline-flex min-h-11 items-center px-2 font-semibold whitespace-nowrap text-ink"
                >
                  로그인
                </Link>
                <Link
                  to={withNext('/signup', pathname + search)}
                  className={`${buttonClassName('signup')} whitespace-nowrap`}
                >
                  회원가입
                </Link>
              </div>
            )}
            {mobileAccount && (
              <button
                type="button"
                aria-expanded={menuOpen}
                aria-controls="mobile-nav"
                aria-label={menuOpen ? '메뉴 닫기' : '메뉴 열기'}
                onClick={() => setOpenedAt(menuOpen ? null : pathname)}
                className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-control text-ink hover:bg-fill lg:hidden"
              >
                <Icon name={menuOpen ? 'close' : 'menu'} size={22} />
              </button>
            )}
          </div>
        )}
      </div>

      {!minimal && menuOpen && (
        <nav id="mobile-nav" aria-label="주 메뉴" className="bg-canvas lg:hidden">
          <ul className="mx-auto flex max-w-screen-xl flex-col py-3">
            {PRIMARY_ITEMS.map((item) => {
              const current = active === item.key;
              return (
                <li key={item.key}>
                  <Link
                    to={item.path}
                    aria-current={current ? 'page' : undefined}
                    className={[
                      'flex min-h-14 items-center px-4 text-2xl font-bold tracking-tight',
                      current ? 'text-ink' : 'text-ink-muted hover:bg-fill hover:text-ink',
                    ].join(' ')}
                  >
                    {item.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
      )}
    </header>
  );
}

function useScrolled(): boolean {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 0);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return scrolled;
}
