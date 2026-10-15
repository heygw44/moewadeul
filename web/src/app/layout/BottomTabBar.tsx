import { Icon } from '../../components/icons';
import { useSession } from '../../features/member/session';
import { Link, useLocation } from '../router';
import { activeNavKey, NAV_ITEMS, navTarget } from './navItems';

// 모바일 화면 맨 아래에 붙는 다섯 칸 메뉴다. lg부터는 머리글이 메뉴를 맡으므로 숨긴다.
// 높이는 theme.css의 --bottom-nav-h와 같다(56px + 아이폰 홈 막대 여백).
// 화면 아래에 붙어 떠 있는 막대라 위로 옅은 그림자를 띄워 본문과 나눈다.
const RAISED_SHADOW = 'shadow-[0_-6px_16px_-12px_rgb(27_38_32/0.35)]'; // taste-skill-ignore: 붙어 다니는 막대

export function BottomTabBar() {
  const { pathname } = useLocation();
  const session = useSession();
  const signedIn = session.status === 'authenticated' && session.me !== null;
  const active = activeNavKey(pathname);

  return (
    <nav
      aria-label="하단 메뉴"
      className={`fixed inset-x-0 bottom-0 z-20 bg-surface pb-[env(safe-area-inset-bottom)] lg:hidden ${RAISED_SHADOW}`}
    >
      <ul className="grid grid-cols-5">
        {NAV_ITEMS.map((item) => {
          const current = active === item.key;
          return (
            <li key={item.key}>
              <Link
                to={navTarget(item, signedIn)}
                aria-current={current ? 'page' : undefined}
                className={[
                  'relative flex h-14 flex-col items-center justify-center gap-0.5 text-xs',
                  current ? 'font-bold text-ink' : 'text-ink-muted',
                ].join(' ')}
              >
                <Icon name={item.icon} size={22} />
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
