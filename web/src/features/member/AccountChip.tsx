import { useState } from 'react';
import { Link, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { Icon } from '../../components/icons';
import { withNext } from './nextPath';
import { useSession } from './session';

const TEXT_LINK_CLASS = 'inline-flex min-h-11 items-center whitespace-nowrap px-2 text-sm font-semibold sm:text-base';

// 머리글의 계정 자리다. 비회원은 로그인과 회원가입, 회원은 닉네임과 로그아웃을 본다.
// 상자나 칩으로 감싸지 않고 글 버튼으로 둔다. 이메일 인증 전인 회원에게만 인증 링크를 더하고,
// 본인확인 권유는 내 정보 화면과 막힌 행동 곁에서만 한다. 머리글까지 같은 권유를 두면 한 화면에 두 번 보이기 때문이다.
export function AccountChip() {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [loggingOut, setLoggingOut] = useState(false);

  if (session.status === 'loading') return null;

  if (session.status === 'anonymous' || session.me === null) {
    const here = pathname + search;
    return (
      <div className="pointer-events-auto inline-flex items-center">
        <Link to={withNext('/login', here)} className={`${TEXT_LINK_CLASS} text-accent`}>
          로그인
        </Link>
        <Link to={withNext('/signup', here)} className={`${TEXT_LINK_CLASS} text-ink`}>
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
        className="max-w-32 min-h-11 content-center truncate px-2 text-sm text-ink underline-offset-2 hover:underline"
      >
        {session.me.nickname}
      </Link>
      {session.me.status === 'UNVERIFIED' && (
        <Link to="/verify-email" className={`${TEXT_LINK_CLASS} gap-1 text-warning`}>
          <Icon name="alert" size={16} className="shrink-0" />
          이메일 인증
        </Link>
      )}
      <Button variant="ghost" loading={loggingOut} onClick={handleLogout}>
        로그아웃
      </Button>
    </div>
  );
}
