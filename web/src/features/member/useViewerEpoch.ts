import { useState } from 'react';
import { useSession } from './session';

// 내용 요청을 세션 확인(/api/me)과 함께 보내는 화면이 쓰는 값이다. 로그인한 회원이 바뀌었을 때만 1씩 늘어난다.
// 내용 요청에도 세션 쿠키가 붙고 서버가 그 쿠키로 likedByMe 같은 로그인 의존 값을 정한다. 그래서 화면은 세션 확인을 기다리지 않고 요청한다.
// 세션이 처음 정해질 때(loading → 로그인함·로그인하지 않음)는 값을 올리지 않아서 이미 보낸 요청을 다시 보내지 않는다.
// 정해진 뒤 로그인, 로그아웃, 다른 회원으로 로그인, 세션 만료(401)로 바뀌면 올라가서, 화면이 이전 로그인 상태로 받은 응답을 버리고 다시 받는다.
// 요청을 보낸 시각과 세션이 정해진 시각 사이에 세션이 끝난 경우(두 요청이 서로 다른 로그인 상태를 본 경우)는 구분하지 못한다. 그 틈은 두 요청의 간격(수십 ms)이다.
export function useViewerEpoch(): number {
  const session = useSession();
  const viewer: number | 'anonymous' | null =
    session.status === 'loading' ? null : (session.me?.memberId ?? 'anonymous');
  const [state, setState] = useState<{ viewer: number | 'anonymous' | null; epoch: number }>({ viewer, epoch: 0 });

  // 렌더 중에 상태를 맞추는 패턴이다. 이 렌더의 결과는 버려지고 바로 다시 그린다.
  if (viewer !== null && viewer !== state.viewer) {
    setState({ viewer, epoch: state.viewer === null ? state.epoch : state.epoch + 1 });
  }
  return state.epoch;
}
