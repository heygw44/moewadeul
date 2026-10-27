import { Icon } from '../../components/icons';
import { useSession } from '../../features/member/session';
import { Link, useLocation } from '../router';
import { activeNavKey, NAV_ITEMS, navTarget } from './navItems';

export function BottomTabBar() {
  const { pathname } = useLocation();
  const session = useSession();
  const signedIn = session.status === 'authenticated' && session.me !== null;
  const active = activeNavKey(pathname);

  return (
    <nav
      aria-label="하단 메뉴"
      className="fixed inset-x-0 bottom-0 z-20 h-(--bottom-nav-h) border-t border-line bg-surface pb-[env(safe-area-inset-bottom)] lg:hidden"
    >
      <ul className="grid h-full grid-cols-5">
        {NAV_ITEMS.map((item) => {
          const current = active === item.key;
          return (
            <li key={item.key}>
              <Link
                to={navTarget(item, signedIn)}
                aria-current={current ? 'page' : undefined}
                className={[
                  'relative flex h-full flex-col items-center justify-center gap-1 text-xs font-semibold',
                  current ? 'text-ink' : 'text-ink-muted',
                ].join(' ')}
              >
                <Icon name={item.icon} size={22} className={current ? 'text-brand' : undefined} />
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
