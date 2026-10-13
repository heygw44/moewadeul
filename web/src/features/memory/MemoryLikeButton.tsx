import { useState } from 'react';
import { toUserMessage } from '../../api/errors';
import { likeMemory, unlikeMemory } from '../../api/memories';
import type { MemoryLikeResponse } from '../../api/types';
import { Link, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { Icon } from '../../components/icons';
import { SECONDARY_LINK_CLASS } from '../community/communityLabels';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';

// 추억 기록의 좋아요 버튼이다. 비회원은 로그인 화면으로 보내고, 이메일 인증 전 회원은 누를 수 없다.
// 누르면 서버가 돌려준 값으로 상태와 개수를 바꾼다. 응답을 기다리는 동안 낙관적으로 바꾸지 않는다.
export function MemoryLikeButton({
  memoryId,
  likedByMe,
  likeCount,
}: {
  memoryId: number;
  likedByMe: boolean;
  likeCount: number;
}) {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [confirmed, setConfirmed] = useState<MemoryLikeResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const liked = confirmed ? confirmed.liked : likedByMe;
  const count = confirmed ? confirmed.likeCount : likeCount;

  const content = (
    <>
      <Icon name="heart" size={20} fill={liked ? 'currentColor' : 'none'} />
      <span>좋아요</span>
      <span className="font-mono tabular-nums">{count}</span>
    </>
  );

  if (session.status === 'loading') return null;
  if (session.status === 'anonymous' || session.me === null) {
    return (
      <Link to={withNext('/login', pathname + search)} className={SECONDARY_LINK_CLASS}>
        <span className="inline-flex items-center gap-2">{content}</span>
      </Link>
    );
  }
  if (session.me.status === 'UNVERIFIED') {
    return (
      <Button variant="secondary" disabled>
        {content}
      </Button>
    );
  }

  async function toggle() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      setConfirmed(liked ? await unlikeMemory(memoryId) : await likeMemory(memoryId));
    } catch (caught) {
      setError(toUserMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-1">
      <Button variant={liked ? 'primary' : 'secondary'} aria-pressed={liked} loading={busy} onClick={() => void toggle()}>
        {content}
      </Button>
      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
