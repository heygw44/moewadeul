import { useState } from 'react';
import { Link, useLocation } from '../../app/router';
import { buttonClassName } from '../../components/buttonClass';
import { Icon } from '../../components/icons';
import { withNext } from './nextPath';
import { useSession } from './session';

const TEXT_LINK_CLASS = 'inline-flex min-h-11 items-center whitespace-nowrap px-2 text-sm font-semibold sm:text-base';

export function AccountChip() {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [loggingOut, setLoggingOut] = useState(false);

  if (session.status === 'loading') return null;

  if (session.status === 'anonymous' || session.me === null) {
    const here = pathname + search;
    return (
      <div className="pointer-events-auto inline-flex items-center gap-1">
        <Link to={withNext('/login', here)} className={`${TEXT_LINK_CLASS} text-ink`}>
          로그인
        </Link>
        <Link to={withNext('/signup', here)} className={`${buttonClassName('signup')} whitespace-nowrap`}>
          회원가입
        </Link>
      </div>
    );
  }

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await session.logout();
    } finally {
      setLoggingOut(false);
    }
  }

  return (
    <div className="pointer-events-auto inline-flex min-w-0 items-center gap-1">
      <Link
        to="/me"
        aria-label={`내 정보: ${session.me.nickname}`}
        className="max-w-32 min-h-11 content-center truncate px-2 text-sm font-semibold text-ink underline-offset-2 hover:underline"
      >
        {session.me.nickname}
      </Link>
      {session.me.status === 'UNVERIFIED' && (
        <Link to="/verify-email" className={`${TEXT_LINK_CLASS} gap-1 text-warning`}>
          <Icon name="alert" size={16} className="shrink-0" />
          이메일 인증
        </Link>
      )}
      <button
        type="button"
        disabled={loggingOut}
        aria-busy={loggingOut || undefined}
        onClick={handleLogout}
        className="inline-flex min-h-11 items-center rounded-control px-3 text-sm font-semibold text-ink-muted hover:bg-fill hover:text-ink disabled:cursor-progress"
      >
        로그아웃
      </button>
    </div>
  );
}
