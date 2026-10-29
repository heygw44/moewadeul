import type { ReactNode } from 'react';
import { Link, useLocation } from '../router';
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
  unverifiedReason?: string;
  fullWidth?: boolean;
  children: ReactNode;
};

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
