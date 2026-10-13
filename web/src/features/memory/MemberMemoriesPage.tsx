import { useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { fetchMemberProfile } from '../../api/members';
import { fetchMemberMemories } from '../../api/memories';
import type { MemberMemorySummary } from '../../api/types';
import { Link } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { HubLayout } from '../../components/HubLayout';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { SECONDARY_LINK_CLASS } from '../community/communityLabels';
import { useSession } from '../member/session';
import type { MemoryCellItem } from './memoryItems';
import { MemoryMapGrid } from './MemoryMapGrid';

type LoadResult =
  | { memberId: number; attempt: number; memories: MemberMemorySummary[] }
  | { memberId: number; attempt: number; error: unknown };

// 다른 회원이 공개한 기록만 약 1km로 줄인 좌표로 지도에 보여 준다.
export function MemberMemoriesPage({ memberId }: { memberId: number }) {
  const session = useSession();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<LoadResult | null>(null);
  const [nickname, setNickname] = useState<{ memberId: number; value: string } | null>(null);
  const sessionReady = session.status !== 'loading';

  useEffect(() => {
    if (!sessionReady) return;
    const controller = new AbortController();
    fetchMemberMemories(memberId, controller.signal).then(
      (memories) => {
        if (!controller.signal.aborted) setResult({ memberId, attempt, memories });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ memberId, attempt, error });
      },
    );
    return () => controller.abort();
  }, [memberId, attempt, sessionReady]);

  // 제목에 쓸 닉네임이다. 못 받아도 기록 보기에는 영향이 없어서 오류는 무시한다.
  useEffect(() => {
    const controller = new AbortController();
    fetchMemberProfile(memberId, controller.signal).then(
      (profile) => {
        if (!controller.signal.aborted) setNickname({ memberId, value: profile.nickname });
      },
      () => {},
    );
    return () => controller.abort();
  }, [memberId]);

  const current = result?.memberId === memberId && result.attempt === attempt ? result : null;
  const memories = current && 'memories' in current ? current.memories : null;
  const showSkeleton = useDelayedFlag(current === null);
  const name = nickname?.memberId === memberId ? nickname.value : null;
  const title = name ? `${name}님의 발자국` : '회원의 발자국';

  useEffect(() => {
    document.title = `${title} · 뫼와들`;
  }, [title]);

  const items = useMemo<MemoryCellItem[]>(
    () =>
      (memories ?? []).map((memory) => ({
        id: memory.memoryId,
        position: { lat: memory.approxLat, lng: memory.approxLng },
        thumbnailUrl: memory.thumbnailUrl,
        visitedDate: memory.visitedDate,
        nights: memory.nights,
        spotName: memory.spot?.name,
        imageCount: memory.imageCount,
      })),
    [memories],
  );

  const actions = (
    <>
      <Link to={`/members/${memberId}`} className={SECONDARY_LINK_CLASS}>
        프로필
      </Link>
    </>
  );

  let body;
  if (current === null) {
    body = (
      <div aria-busy="true">
        {showSkeleton && (
          <div className="flex flex-col gap-3">
            <Skeleton className="h-72 w-full" />
            <Skeleton className="h-32 w-full" />
          </div>
        )}
      </div>
    );
  } else if ('error' in current) {
    if (current.error instanceof ApiError && current.error.code === 'NOT_FOUND') {
      return (
        <HubLayout title="회원을 찾을 수 없어요" width="narrow">
          <EmptyState framed title="탈퇴했거나 주소가 잘못됐어요" description="발자국 메뉴에서 다른 회원의 발자국을 볼 수 있어요." />
        </HubLayout>
      );
    }
    body = (
      <div role="alert" className="flex flex-col gap-3">
        <Notice tone="danger" title="발자국을 불러오지 못했어요">
          {toUserMessage(current.error)}
        </Notice>
        <div>
          <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
            다시 불러오기
          </Button>
        </div>
      </div>
    );
  } else if (items.length === 0) {
    body = <EmptyState framed title="공개한 발자국이 아직 없어요" description="이 회원이 발자국을 공개하면 여기에 보여요." />;
  } else {
    body = (
      <>
        <p className="text-sm text-ink-muted">위치는 정확한 곳 대신 약 1km 범위의 대략 위치만 보여요.</p>
        <MemoryMapGrid items={items} />
      </>
    );
  }

  return (
    <HubLayout title={title} width="narrow" actions={actions}>
      {body}
    </HubLayout>
  );
}
