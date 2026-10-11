import { useEffect, useState } from 'react';

import { Icon } from '../../components/icons';
import { AccountChip } from '../../features/member/AccountChip';
import { withNext } from '../../features/member/nextPath';
import { useSession } from '../../features/member/session';
import { NotificationBellLink, UnreadBadge } from '../../features/notification/NotificationLink';
import { notificationLabel, useUnreadCount } from '../../features/notification/unreadCount';
import { Link, useLocation } from '../router';
import { activeNavKey, NAV_ITEMS } from './navItems';

type SiteHeaderProps = {
  // 하단 탭이 없는 화면(랜딩)에서 켠다. 모바일에서도 계정 표시를 머리글에 둔다.
  mobileAccount?: boolean;
  // 로그인·가입 화면처럼 계정 표시가 그 화면의 할 일과 겹치는 곳에서 켠다. 알림·계정 자리를 비운다.
  minimal?: boolean;
  // 지도 화면처럼 본문이 화면 끝까지 차는 곳에서 켠다. 가운데 최대 폭을 두지 않아 로고가 왼쪽 패널과 줄을 맞춘다.
  fluid?: boolean;
  className?: string;
};

const PRIMARY_KEYS = new Set(['map', 'basecamps', 'community', 'programs']);
const PRIMARY_ITEMS = NAV_ITEMS.filter((item) => PRIMARY_KEYS.has(item.key));

// 지도가 없는 화면과 데스크톱 지도 화면 위에 붙는 머리글이다.
// 모바일에서는 메뉴를 접어 두고 메뉴 버튼으로 펼친다. 하단 탭이 없는 랜딩·404에서도 다른 화면으로 갈 수 있게 하기 위해서다.
export function SiteHeader({ mobileAccount = false, minimal = false, fluid = false, className = '' }: SiteHeaderProps) {
  const { pathname, search } = useLocation();
  const session = useSession();
  const count = useUnreadCount();
  const signedIn = session.status === 'authenticated' && session.me !== null;
  const active = activeNavKey(pathname);
  // 메뉴를 펼친 경로를 기억한다. 메뉴를 눌러 다른 화면으로 가면 경로가 달라지므로 메뉴가 닫힌 것으로 본다.
  const [openedAt, setOpenedAt] = useState<string | null>(null);
  const menuOpen = openedAt === pathname;

  useEffect(() => {
    if (!menuOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpenedAt(null);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [menuOpen]);

  return (
    <header className={`sticky top-0 z-20 border-b border-contour bg-paper ${className}`}>
      <div className={`mx-auto flex min-h-14 items-center gap-6 px-4 ${fluid ? '' : 'max-w-screen-xl'}`}>
        <Link to="/" className="font-serif text-xl font-semibold text-forest-deep">
          피치맵
        </Link>

        <nav aria-label="주 메뉴" className="hidden h-14 items-stretch gap-1 lg:flex">
          {PRIMARY_ITEMS.map((item) => {
            const current = active === item.key;
            return (
              <Link
                key={item.key}
                to={item.path}
                aria-current={current ? 'page' : undefined}
                className={[
                  'inline-flex items-center border-b-2 px-3 text-base font-semibold',
                  current ? 'border-forest text-forest-deep' : 'border-transparent text-ink-muted hover:text-ink',
                ].join(' ')}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        {!minimal && (
          <div className="ml-auto flex min-w-0 items-center gap-2">
            {signedIn && (
              <Link
                to="/me/notifications"
                aria-label={notificationLabel(count)}
                aria-current={pathname === '/me/notifications' ? 'page' : undefined}
                className="hidden min-h-11 items-center gap-2 rounded-control px-3 text-ink hover:bg-paper-deep lg:inline-flex"
              >
                <Icon name="bell" size={20} />
                <UnreadBadge count={count} />
              </Link>
            )}
            <NotificationBellLink className="inline-flex min-h-11 min-w-11 items-center justify-center gap-1 rounded-control px-2 text-ink hover:bg-paper-deep lg:hidden" />
            <div className={mobileAccount ? 'flex min-w-0' : 'hidden min-w-0 lg:flex'}>
              <AccountChip />
            </div>
            {!mobileAccount && !signedIn && session.status !== 'loading' && (
              <div className="inline-flex items-center lg:hidden">
                <Link
                  to={withNext('/login', pathname + search)}
                  className="inline-flex min-h-11 items-center px-2 font-semibold text-forest"
                >
                  로그인
                </Link>
                <Link
                  to={withNext('/signup', pathname + search)}
                  className="inline-flex min-h-11 items-center px-2 font-semibold text-ink"
                >
                  회원가입
                </Link>
              </div>
            )}
            <button
              type="button"
              aria-expanded={menuOpen}
              aria-controls="mobile-nav"
              aria-label={menuOpen ? '메뉴 닫기' : '메뉴 열기'}
              onClick={() => setOpenedAt(menuOpen ? null : pathname)}
              className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-control text-ink hover:bg-paper-deep lg:hidden"
            >
              <Icon name={menuOpen ? 'close' : 'menu'} size={22} />
            </button>
          </div>
        )}
      </div>

      {!minimal && menuOpen && (
        <nav id="mobile-nav" aria-label="주 메뉴" className="border-t border-contour bg-paper lg:hidden">
          <ul className="mx-auto flex max-w-screen-xl flex-col py-1">
            {PRIMARY_ITEMS.map((item) => {
              const current = active === item.key;
              return (
                <li key={item.key}>
                  <Link
                    to={item.path}
                    aria-current={current ? 'page' : undefined}
                    className={[
                      'flex min-h-12 items-center gap-3 border-l-2 px-4 text-base font-semibold',
                      current ? 'border-forest text-forest-deep' : 'border-transparent text-ink-muted hover:text-ink',
                    ].join(' ')}
                  >
                    <Icon name={item.icon} size={20} />
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
