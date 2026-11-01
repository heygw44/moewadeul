import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { logout as logoutRequest, fetchMe } from '../../api/auth';
import { setUnauthorizedHandler } from '../../api/client';
import type { Me } from '../../api/types';
import { navigate } from '../router';
import { fetchMeWithRetry } from './fetchMeWithRetry';
import { forgetFormDraft, keepFormDraft } from './formDraft';
import { notifySignedOut } from './signedOut';
import { SessionUnavailable } from './SessionUnavailable';

type SessionStatus = 'loading' | 'anonymous' | 'authenticated';

export interface Session {
  status: SessionStatus;
  me: Me | null;
  refresh(): Promise<Me | null>;
  logout(): Promise<void>;
}

interface SessionState {
  status: SessionStatus;
  me: Me | null;
}

const ANONYMOUS: SessionState = { status: 'anonymous', me: null };

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionState>({ status: 'loading', me: null });
  const [unavailable, setUnavailable] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    void fetchMeWithRetry(controller.signal).then((result) => {
      if (result.status === 'unavailable') {
        if (controller.signal.aborted) return;
        setUnavailable(true);
        setRetrying(false);
        return;
      }
      setState(result.status === 'authenticated' ? { status: 'authenticated', me: result.me } : ANONYMOUS);
      setUnavailable(false);
      setRetrying(false);
    });
    return () => controller.abort();
  }, [attempt]);

  const retry = useCallback(() => {
    setRetrying(true);
    setAttempt((value) => value + 1);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      keepFormDraft();
      setState(ANONYMOUS);
      notifySignedOut();
      const { pathname, search } = window.location;
      if (pathname === '/login' || pathname === '/signup') return;
      navigate(`/login?next=${encodeURIComponent(pathname + search)}`, { replace: true });
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  const refresh = useCallback(async () => {
    const me = await fetchMe();
    setState(me ? { status: 'authenticated', me } : ANONYMOUS);
    if (!me) notifySignedOut();
    return me;
  }, []);

  const logout = useCallback(async () => {
    await logoutRequest().catch(() => undefined);
    setState(ANONYMOUS);
    notifySignedOut();
    forgetFormDraft();
    navigate('/');
  }, []);

  const value = useMemo<Session>(
    () => ({ status: state.status, me: state.me, refresh, logout }),
    [state, refresh, logout],
  );

  if (unavailable) return <SessionUnavailable retrying={retrying} onRetry={retry} />;

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) {
    throw new Error('useSession은 SessionProvider 안에서만 쓸 수 있습니다.');
  }
  return session;
}
