import { useEffect, useState } from 'react';

import { BrandMark } from '../../components/BrandMark';
import { buttonClassName } from '../../components/buttonClass';
import { Icon } from '../../components/icons';
import { AccountChip } from '../../features/member/AccountChip';
import { withNext } from '../../features/member/nextPath';
import { useSession } from '../../features/member/session';
import { NotificationBellLink, UnreadBadge } from '../../features/notification/NotificationLink';
import { notificationLabel, useUnreadCount } from '../../features/notification/unreadCount';
import { Link, useLocation } from '../router';
import { activeNavKey, NAV_ITEMS } from './navItems';

type SiteHeaderProps = {
  // 하단 탭이 없는 화면(랜딩·404)에서 켠다. 모바일에서도 계정 표시와 메뉴 버튼을 머리글에 둔다.
  mobileAccount?: boolean;
  // 로그인·가입 화면처럼 계정 표시가 그 화면의 할 일과 겹치는 곳에서 켠다. 알림·계정 자리를 비운다.
  minimal?: boolean;
  // 랜딩에서 켠다. 맨 위에서는 아래 선을 숨기고, 스크롤하면 판 아래에 한 단계 밝은 선을 보인다.
  lineOnScroll?: boolean;
  className?: string;
};

const PRIMARY_KEYS = new Set(['map', 'basecamps', 'footprints', 'community', 'programs']);
const PRIMARY_ITEMS = NAV_ITEMS.filter((item) => PRIMARY_KEYS.has(item.key));

// 지도가 없는 화면과 데스크톱 지도 화면 위에 붙는 한 줄 머리글이다. 탐방로 안내판처럼 숲색 판에 흰 글자를 쓴다.
// 왼쪽부터 서비스 이름, 메뉴, 오른쪽 끝에 알림·계정을 둔다.
// 지도 화면을 포함한 모든 화면에서 가운데 최대 폭(max-w-screen-xl) 안에 둔다. 그래서 메뉴로 화면을 오가도 로고와 메뉴 자리가 움직이지 않는다.
// 모바일에서는 메뉴를 접어 두고 메뉴 버튼으로 펼친다. 메뉴 버튼은 하단 탭이 없는 랜딩·404(mobileAccount)에만 둔다.
// 하단 탭이 있는 화면에서 메뉴 버튼까지 두면 같은 다섯 메뉴가 한 화면에 두 번 있게 된다.
export function SiteHeader({
  mobileAccount = false,
  minimal = false,
  lineOnScroll = false,
  className = '',
}: SiteHeaderProps) {
  const { pathname, search } = useLocation();
  const session = useSession();
  const count = useUnreadCount();
  const signedIn = session.status === 'authenticated' && session.me !== null;
  const active = activeNavKey(pathname);
  // 메뉴를 펼친 경로를 기억한다. 메뉴를 눌러 다른 화면으로 가면 경로가 달라지므로 메뉴가 닫힌 것으로 본다.
  const [openedAt, setOpenedAt] = useState<string | null>(null);
  const menuOpen = openedAt === pathname;
  const scrolled = useScrolled(lineOnScroll);
  // 메뉴를 펼치면 목록과 머리글을 나누려고 선을 보인다.
  const showLine = !lineOnScroll || scrolled || menuOpen;

  useEffect(() => {
    if (!menuOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpenedAt(null);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [menuOpen]);

  return (
    // 짙은 판이라 포커스 링을 흰색으로 바꾼다.
    <header
      className={`sticky top-0 z-20 border-b bg-brand text-white [&_:focus-visible]:outline-white ${showLine ? 'border-brand-raised' : 'border-transparent'} ${className}`}
    >
      <div className="mx-auto flex min-h-14 max-w-screen-xl items-center gap-3 px-4 lg:min-h-16 lg:gap-10">
        <Link to="/" className="shrink-0 text-white">
          <BrandMark className="text-xl" />
        </Link>

        {/* 데스크톱 메뉴는 서비스 이름 바로 옆에 둔다. 지금 메뉴는 흰 판으로 뒤집고, 나머지는 판 위 보조 글자색이다.
            굵기는 모두 같게 둔다. 지금 메뉴만 굵게 하면 메뉴를 옮길 때 글자 폭이 바뀌어 옆 메뉴가 밀린다. */}
        {!minimal && (
          <nav aria-label="주 메뉴" className="hidden items-center gap-1 lg:flex">
            {PRIMARY_ITEMS.map((item) => {
              const current = active === item.key;
              return (
                <Link
                  key={item.key}
                  to={item.path}
                  aria-current={current ? 'page' : undefined}
                  className={[
                    'inline-flex min-h-11 items-center rounded-control px-3.5 text-[0.9375rem] font-semibold',
                    current ? 'bg-surface text-brand' : 'text-brand-ink hover:bg-brand-raised hover:text-white',
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
                className="hidden min-h-11 items-center gap-2 rounded-control px-3 text-white hover:bg-brand-raised lg:inline-flex"
              >
                <Icon name="bell" size={20} />
                <UnreadBadge count={count} />
              </Link>
            )}
            <NotificationBellLink className="inline-flex min-h-11 min-w-11 items-center justify-center gap-1 rounded-control px-2 text-white hover:bg-brand-raised lg:hidden" />
            {!mobileAccount && signedIn && session.me !== null && (
              <Link
                to="/me"
                aria-label={`내 정보: ${session.me.nickname}`}
                aria-current={pathname === '/me' ? 'page' : undefined}
                className="inline-flex min-h-11 max-w-28 min-w-11 items-center px-2 text-sm text-white underline-offset-2 hover:underline lg:hidden"
              >
                <span className="truncate">{session.me.nickname}</span>
              </Link>
            )}
            <div className={mobileAccount ? 'flex min-w-0' : 'hidden min-w-0 lg:flex'}>
              <AccountChip />
            </div>
            {!mobileAccount && !signedIn && session.status !== 'loading' && (
              <div className="inline-flex items-center gap-1 lg:hidden">
                <Link
                  to={withNext('/login', pathname + search)}
                  className="inline-flex min-h-11 items-center px-2 font-semibold whitespace-nowrap text-white"
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
                className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-control text-white hover:bg-brand-raised lg:hidden"
              >
                <Icon name={menuOpen ? 'close' : 'menu'} size={22} />
              </button>
            )}
          </div>
        )}
      </div>

      {!minimal && menuOpen && (
        <nav id="mobile-nav" aria-label="주 메뉴" className="border-t border-brand-raised bg-brand lg:hidden">
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
                      current ? 'text-white' : 'text-brand-ink hover:bg-brand-raised hover:text-white',
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

// 창이 맨 위에서 내려왔는지 알려 준다. enabled가 거짓이면 스크롤을 듣지 않는다.
function useScrolled(enabled: boolean): boolean {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    if (!enabled) return;
    const onScroll = () => setScrolled(window.scrollY > 0);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [enabled]);

  return enabled && scrolled;
}
