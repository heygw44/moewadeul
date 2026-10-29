import { useEffect } from 'react';
import { navigate, useLocation } from '../../app/router';
import { withNext } from './nextPath';
import { useSession } from './session';
import type { Session } from './session';

export function useRequireAuth(returnTo?: string): Session {
  const session = useSession();
  const { pathname, search } = useLocation();
  const target = returnTo ?? pathname + search;
  const anonymous = session.status === 'anonymous';

  useEffect(() => {
    if (anonymous) navigate(withNext('/login', target), { replace: true });
  }, [anonymous, target]);

  return session;
}
