import type { ReactNode } from 'react';
import { Link, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { buttonClassName } from '../../components/buttonClass';
import type { ButtonVariant } from '../../components/buttonClass';
import { withNext } from './nextPath';
import { useSession } from './session';

type GatedButtonProps = {
  variant?: ButtonVariant;
  onClick: () => void;
  loading?: boolean;
  disabled?: boolean;
  disabledReason?: string;
  // 이메일 인증 전인 회원에게 버튼 아래 보여 줄 이유다. 같은 묶음에 인증 안내(UnverifiedNote)를 이미 두었으면 비운다.
  unverifiedReason?: string;
  fullWidth?: boolean;
  children: ReactNode;
};

// 이메일 인증을 마친 회원만 할 수 있는 행동의 버튼이다. 비회원에게도 같은 이름의 버튼을 보여 주고, 누르면 로그인 화면으로
// 보낸 뒤 이 화면으로 돌아오게 한다. 그래서 화면 본문에 "로그인" 버튼을 따로 두지 않는다. 쓰기 권한은 서버가 다시 확인한다.
export function GatedButton({
  variant = 'secondary',
  onClick,
  loading,
  disabled = false,
  disabledReason,
  unverifiedReason,
  fullWidth = false,
  children,
}: GatedButtonProps) {
  const session = useSession();
  const { pathname, search } = useLocation();

  if (session.status === 'loading') return null;
  if (session.status === 'anonymous' || session.me === null) {
    return (
      <Link
        to={withNext('/login', pathname + search)}
        className={[buttonClassName(variant), fullWidth ? 'w-full' : ''].filter(Boolean).join(' ')}
      >
        {children}
      </Link>
    );
  }
  if (session.me.status === 'UNVERIFIED') {
    return (
      <Button variant={variant} disabled disabledReason={unverifiedReason} fullWidth={fullWidth}>
        {children}
      </Button>
    );
  }
  return (
    <Button
      variant={variant}
      loading={loading}
      disabled={disabled}
      disabledReason={disabledReason}
      fullWidth={fullWidth}
      onClick={onClick}
    >
      {children}
    </Button>
  );
}

// 이메일 인증 전인 회원에게 한 묶음(장소 상세, 댓글 등)마다 한 번만 보여 주는 안내다. 묶음 안의 버튼마다 이유를 되풀이하지 않는다.
export function UnverifiedNote({ action }: { action: string }) {
  const session = useSession();
  if (session.status !== 'authenticated' || session.me?.status !== 'UNVERIFIED') return null;
  return (
    <p className="text-sm text-ink-muted">
      이메일 인증을 마치면 {action}{' '}
      <Link to="/verify-email" className="font-semibold text-accent underline underline-offset-2">
        이메일 인증하기
      </Link>
    </p>
  );
}
