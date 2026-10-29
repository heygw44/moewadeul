import type { Me } from '../../api/types';
import { loadPage, navigate } from '../../app/router';
import type { Session } from './session';

// 회원 정보를 읽지 못하면 세션 상태가 로그인 전 그대로 남는다. 페이지를 새로 열어 앱을 처음 열 때의 확인(재시도, 안내 화면)에 맡긴다.
export async function enterAfterAuth(session: Session, destination: (me: Me | null) => string): Promise<void> {
  const me = await session.refresh().catch(() => null);
  if (me) navigate(destination(me), { replace: true });
  else loadPage(destination(null));
}
