import { useEffect, useMemo, useState } from 'react';
import { toUserMessage } from '../../api/errors';
import { fetchMyMemories } from '../../api/memories';
import type { MemorySummary } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { HubLayout } from '../../components/HubLayout';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { SECONDARY_LINK_CLASS } from '../community/communityLabels';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import type { MemoryCellItem } from './memoryItems';
import { MEMORY_STATUS_LABEL } from './memoryLabels';
import { MemoryMapGrid } from './MemoryMapGrid';

type LoadResult = { attempt: number; memories: MemorySummary[] } | { attempt: number; error: unknown };

// 내가 남긴 기록을 공개 범위와 상관없이 정확한 좌표로 지도에 보여 준다. 좌표는 작성자인 나에게만 간다.
export function MyMemoriesPage() {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<LoadResult | null>(null);
  const authenticated = session.status === 'authenticated';

  useEffect(() => {
    document.title = '내 기록 지도 · 뫼와들';
  }, []);

  // 내 기록은 로그인한 세션으로만 볼 수 있다. 로그인한 뒤 이 화면으로 돌아오게 한다.
  useEffect(() => {
    if (session.status === 'anonymous') navigate(withNext('/login', pathname + search), { replace: true });
  }, [session.status, pathname, search]);

  useEffect(() => {
    if (!authenticated) return;
    const controller = new AbortController();
    fetchMyMemories(controller.signal).then(
      (memories) => {
        if (!controller.signal.aborted) setResult({ attempt, memories });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ attempt, error });
      },
    );
    return () => controller.abort();
  }, [authenticated, attempt]);

  const current = result?.attempt === attempt ? result : null;
  const memories = current && 'memories' in current ? current.memories : null;
  const showSkeleton = useDelayedFlag(authenticated && current === null);

  const items = useMemo<MemoryCellItem[]>(
    () =>
      (memories ?? []).map((memory) => ({
        id: memory.memoryId,
        position: { lat: memory.lat, lng: memory.lng },
        thumbnailUrl: memory.thumbnailUrl,
        visitedDate: memory.visitedDate,
        nights: memory.nights,
        spotName: memory.spot?.name,
        isPrivate: memory.visibility === 'PRIVATE',
        hiddenLabel: memory.status === 'ACTIVE' ? undefined : MEMORY_STATUS_LABEL[memory.status],
        imageCount: memory.imageCount,
      })),
    [memories],
  );

  if (!authenticated) {
    return <HubLayout title="내 기록 지도" width="narrow">{null}</HubLayout>;
  }

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
    body = (
      <div role="alert" className="flex flex-col gap-3">
        <Notice tone="danger" title="기록을 불러오지 못했어요">
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
    body = (
      <EmptyState
        framed
        title="아직 남긴 기록이 없어요"
        description="지도에서 다녀온 곳을 길게 누르거나(컴퓨터는 우클릭) 장소 상세에서 기록을 남길 수 있어요."
        action={
          <Link to="/map" className={SECONDARY_LINK_CLASS}>
            지도에서 기록 남기기
          </Link>
        }
      />
    );
  } else {
    body = <MemoryMapGrid items={items} />;
  }

  return (
    <HubLayout
      title="내 기록 지도"
      width="narrow"
      actions={
        <Link to="/memories" className={SECONDARY_LINK_CLASS}>
          피드
        </Link>
      }
    >
      {body}
    </HubLayout>
  );
}
