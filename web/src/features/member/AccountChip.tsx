import { useState } from 'react';
import { Link, useLocation } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { withNext } from './nextPath';
import { useSession } from './session';

const CHIP_CLASS =
  'pointer-events-auto inline-flex min-h-11 items-center gap-2 whitespace-nowrap rounded-control border border-contour bg-card px-2 sm:px-3';

// 머리글과 지도 위에서 함께 쓰는 계정 표시다. 비회원은 로그인과 회원가입, 미인증 회원은 이메일 인증 안내, 회원은 닉네임과 로그아웃을 본다.
// 비회원의 두 칩은 모양을 같게 두고, 자주 누르는 로그인만 숲색 글자로 눈에 띄게 한다.
export function AccountChip() {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [loggingOut, setLoggingOut] = useState(false);

  if (session.status === 'loading') return null;

  if (session.status === 'anonymous' || session.me === null) {
    const here = pathname + search;
    return (
      <div className="inline-flex items-center gap-2">
        <Link to={withNext('/login', here)} className={`${CHIP_CLASS} text-sm font-semibold sm:text-base text-forest`}>
          로그인
        </Link>
        <Link to={withNext('/signup', here)} className={`${CHIP_CLASS} text-sm font-semibold sm:text-base text-ink`}>
          회원가입
        </Link>
      </div>
    );
  }

  if (session.me.status === 'UNVERIFIED') {
    return (
      <Link to="/verify-email" className={CHIP_CLASS}>
        <Badge tone="warning" icon="alert">
          이메일 인증 필요
        </Badge>
      </Link>
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
    <div className="pointer-events-auto inline-flex min-w-0 items-center gap-2 rounded-control border border-contour bg-card py-0.5 pl-3 pr-0.5">
      <Link
        to="/me"
        aria-label={`내 정보: ${session.me.nickname}`}
        className="max-w-32 min-h-11 content-center truncate text-sm text-ink underline-offset-2 hover:underline"
      >
        {session.me.nickname}
      </Link>
      {session.me.identityVerified ? (
        <span>
          <Badge tone="sea" icon="check">
            본인확인
          </Badge>
        </span>
      ) : (
        <Link to="/identity-verification" className="min-h-11 content-center text-sm font-semibold text-forest">
          본인확인
        </Link>
      )}
      <Button variant="ghost" loading={loggingOut} onClick={handleLogout}>
        로그아웃
      </Button>
    </div>
  );
}
