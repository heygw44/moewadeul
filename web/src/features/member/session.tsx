import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { logout as logoutRequest, fetchMe } from '../../api/auth';
import { setUnauthorizedHandler } from '../../api/client';
import type { Me } from '../../api/types';
import { navigate } from '../../app/router';
import { forgetMapScreen } from '../spot/mapScreenMemory';
import { fetchMeWithRetry } from './fetchMeWithRetry';
import { forgetFormDraft, keepFormDraft } from './formDraft';
import { SessionUnavailable } from './SessionUnavailable';

export type SessionStatus = 'loading' | 'anonymous' | 'authenticated';

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
  // 로그인 상태를 확인하지 못한 채 끝났는지, 그리고 다시 확인하는 중인지. 확인하지 못한 것과 로그인하지 않은 것은 다르다.
  const [unavailable, setUnavailable] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [attempt, setAttempt] = useState(0);

  // 앱을 열 때 서버에 지금 세션이 누구인지 한 번 묻는다. 서버가 401로 답할 때만 로그인하지 않은 상태다.
  // 네트워크 오류나 5xx가 계속되면 로그인 화면으로 보내지 않고 안내 화면을 보여 준다. 세션이 멀쩡한 회원이 다시 로그인하게 되기 때문이다.
  useEffect(() => {
    const controller = new AbortController();
    fetchMeWithRetry(controller.signal).then(
      (me) => {
        setState(me ? { status: 'authenticated', me } : ANONYMOUS);
        setUnavailable(false);
        setRetrying(false);
      },
      () => {
        if (controller.signal.aborted) return;
        setUnavailable(true);
        setRetrying(false);
      },
    );
    return () => controller.abort();
  }, [attempt]);

  const retry = useCallback(() => {
    setRetrying(true);
    setAttempt((value) => value + 1);
  }, []);

  // 세션이 끊기면(401) 로그인 화면으로 보내고, 로그인한 뒤 지금 보던 화면으로 돌아오도록 경로를 next에 담는다.
  // 로그인·가입 화면에서는 이미 그 화면에 있으므로 옮기지 않는다.
  // 쓰던 폼이 있으면 상태를 지우기 전에 값을 보관한다. 상태를 지우면 폼이 사라져서 값을 읽을 수 없다.
  // 지도 화면 기억은 비운다. 다음에 로그인하는 사람이 다른 회원일 수 있어서 앞사람의 조건과 AI 요약을 남기지 않는다.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      keepFormDraft();
      setState(ANONYMOUS);
      forgetMapScreen();
      const { pathname, search } = window.location;
      if (pathname === '/login' || pathname === '/signup') return;
      navigate(`/login?next=${encodeURIComponent(pathname + search)}`, { replace: true });
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  // 탈퇴처럼 서버가 세션을 지운 뒤 다시 확인했을 때 로그인하지 않은 상태로 돌아오면 지도 화면 기억도 함께 비운다.
  const refresh = useCallback(async () => {
    const me = await fetchMe();
    setState(me ? { status: 'authenticated', me } : ANONYMOUS);
    if (!me) forgetMapScreen();
    return me;
  }, []);

  const logout = useCallback(async () => {
    // 서버 세션을 지우지 못해도(이미 만료 등) 화면에서는 로그아웃한 상태로 돌린다.
    try {
      await logoutRequest();
    } catch {
      // 무시한다.
    }
    setState(ANONYMOUS);
    forgetMapScreen();
    forgetFormDraft();
    navigate('/');
  }, []);

  const value = useMemo<Session>(
    () => ({ status: state.status, me: state.me, refresh, logout }),
    [state, refresh, logout],
  );

  // 로그인 상태를 확인하지 못했으면 앱 화면을 그리지 않는다. 화면마다 '로그인하지 않음'으로 읽고 로그인 화면으로 보내기 때문이다.
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
