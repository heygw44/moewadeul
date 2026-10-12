import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { deleteMemory, getMemory } from '../../api/memories';
import type { MemoryDetail } from '../../api/types';
import { Link, navigate } from '../../app/router';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { HubLayout } from '../../components/HubLayout';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatLocalDate } from '../../lib/datetime';
import { SECONDARY_LINK_CLASS } from '../community/communityLabels';
import { useSession } from '../member/session';
import { LocationLabel } from '../spot/LocationLabel';
import { nightsLabel, VISIBILITY_LABEL } from './memoryLabels';
import { MemoryLikeButton } from './MemoryLikeButton';

type LoadResult = { memoryId: number; memory: MemoryDetail } | { memoryId: number; error: unknown };

const CRUMB_LINK = 'inline-flex min-h-11 items-center gap-1 text-forest underline underline-offset-2';

export function MemoryDetailPage({ memoryId }: { memoryId: number }) {
  const session = useSession();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<LoadResult | null>(null);
  // 사진 조회 URL은 1시간 뒤 만료된다. 열어 둔 화면에서 사진이 깨지면 기록을 한 번만 다시 받아 새 URL을 얻는다.
  const imageRetried = useRef(false);
  const sessionReady = session.status !== 'loading';

  // 작성자인지에 따라 좌표가 오고 비공개 기록이 보이므로 세션을 확인한 뒤에 받는다.
  useEffect(() => {
    if (!sessionReady) return;
    const controller = new AbortController();
    getMemory(memoryId, controller.signal).then(
      (memory) => {
        if (!controller.signal.aborted) setResult({ memoryId, memory });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ memoryId, error });
      },
    );
    return () => controller.abort();
  }, [memoryId, sessionReady, attempt, session.status]);

  const current = result?.memoryId === memoryId ? result : null;
  const showSkeleton = useDelayedFlag(current === null);
  const title = current !== null && 'memory' in current ? titleOf(current.memory) : '추억 기록';

  useEffect(() => {
    document.title = `${title} · 뫼와들`;
  }, [title]);

  const reload = () => setAttempt((value) => value + 1);
  // 작성자는 내 추억지도로, 그 밖의 사람은 모두의 추억으로 돌아간다. 아직 모르거나 기록을 못 받았을 때는 모두의 추억으로 둔다.
  const authorId = current !== null && 'memory' in current ? current.memory.author.memberId : null;
  const viewerIsAuthor = authorId !== null && session.status === 'authenticated' && session.me?.memberId === authorId;
  const breadcrumb = (
    <Link to={viewerIsAuthor ? '/me/memories' : '/memories'} className={CRUMB_LINK}>
      <Icon name="chevronLeft" size={16} />
      {viewerIsAuthor ? '내 추억지도' : '모두의 추억'}
    </Link>
  );

  if (current === null) {
    return (
      <HubLayout title="추억 기록" breadcrumb={breadcrumb} width="narrow">
        <div aria-busy="true">
          {showSkeleton && (
            <div className="flex flex-col gap-3 rounded-control border border-contour bg-card p-5">
              <Skeleton className="h-8 w-2/3" />
              <Skeleton className="h-40 w-full" />
            </div>
          )}
        </div>
      </HubLayout>
    );
  }

  if ('memory' in current) {
    const memory = current.memory;
    const mine = session.status === 'authenticated' && session.me?.memberId === memory.author.memberId;
    return (
      <HubLayout
        title={title}
        breadcrumb={breadcrumb}
        meta={<MemoryMeta memory={memory} />}
        actions={mine ? <MemoryActions memoryId={memory.memoryId} /> : undefined}
        width="narrow"
      >
        <MemoryContent
          memory={memory}
          onImageError={() => {
            if (imageRetried.current) return;
            imageRetried.current = true;
            reload();
          }}
        />
      </HubLayout>
    );
  }

  if (current.error instanceof ApiError && current.error.code === 'NOT_FOUND') {
    return (
      <HubLayout title="기록을 찾을 수 없어요" breadcrumb={breadcrumb} width="narrow">
        <EmptyState framed title="삭제됐거나 볼 수 없는 기록이에요" description="나만 보기로 남긴 기록은 작성자만 볼 수 있어요." />
      </HubLayout>
    );
  }

  return (
    <HubLayout title="추억 기록" breadcrumb={breadcrumb} width="narrow">
      <div className="flex flex-col gap-3">
        <Notice tone="danger" title="기록을 불러오지 못했어요">
          {toUserMessage(current.error)}
        </Notice>
        <div>
          <Button variant="secondary" onClick={reload}>
            다시 불러오기
          </Button>
        </div>
      </div>
    </HubLayout>
  );
}

function titleOf(memory: MemoryDetail): string {
  return memory.spot ? memory.spot.name : `${formatLocalDate(memory.visitedDate)}의 기록`;
}

function MemoryMeta({ memory }: { memory: MemoryDetail }) {
  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ink-muted">
      <Link to={`/members/${memory.author.memberId}`} className="font-semibold text-ink underline-offset-2 hover:underline">
        {memory.author.nickname}
      </Link>
      <span aria-hidden="true">·</span>
      <time dateTime={memory.visitedDate}>{formatLocalDate(memory.visitedDate)}</time>
      <span aria-hidden="true">·</span>
      <span>{nightsLabel(memory.nights)}</span>
      <span aria-hidden="true">·</span>
      <span>{VISIBILITY_LABEL[memory.visibility]}</span>
      {memory.spot && (
        <>
          <span aria-hidden="true">·</span>
          <Link
            to={`/spots/${memory.spot.spotId}`}
            className="inline-flex items-center gap-1 text-forest underline underline-offset-2"
          >
            <Icon name="map" size={14} />
            장소 보기
          </Link>
        </>
      )}
    </div>
  );
}

// 작성자에게만 수정·삭제를 보여 준다. 권한은 서버가 다시 확인한다.
function MemoryActions({ memoryId }: { memoryId: number }) {
  const [deleting, setDeleting] = useState(false);
  return (
    <>
      <Link to={`/memories/${memoryId}/edit`} className={SECONDARY_LINK_CLASS}>
        수정
      </Link>
      <Button variant="danger-outline" onClick={() => setDeleting(true)}>
        삭제
      </Button>
      {deleting && <DeleteMemoryDialog memoryId={memoryId} onClose={() => setDeleting(false)} />}
    </>
  );
}

function MemoryContent({ memory, onImageError }: { memory: MemoryDetail; onImageError: () => void }) {
  const position = memory.lat !== undefined && memory.lng !== undefined ? { lat: memory.lat, lng: memory.lng } : null;
  // 작성자가 아닌 사람에게는 정확한 좌표 대신 약 1km로 줄인 좌표만 온다.
  const approxPosition =
    memory.approxLat !== undefined && memory.approxLng !== undefined
      ? { lat: memory.approxLat, lng: memory.approxLng }
      : null;
  return (
    <article className="divide-y divide-contour rounded-control border border-contour bg-card">
      {position !== null && (
        <div className="flex flex-col gap-1 px-5 py-4">
          <p className="text-sm text-ink-muted">다녀온 곳 · 나만 보는 위치예요</p>
          <LocationLabel position={position} />
        </div>
      )}
      {position === null && approxPosition !== null && (
        <div className="flex flex-col gap-1 px-5 py-4">
          <p className="text-sm text-ink-muted">다녀온 곳 · 대략 위치예요</p>
          <LocationLabel position={approxPosition} />
        </div>
      )}
      {memory.content && (
        <p className="whitespace-pre-wrap break-words px-5 py-5 text-base leading-7 text-ink">{memory.content}</p>
      )}
      {memory.images.length > 0 && (
        <ul className="flex flex-col gap-3 px-5 py-5">
          {memory.images.map((image, index) => (
            <li key={image.imageId}>
              <img
                src={image.url}
                alt={`기록 사진 ${index + 1} / ${memory.images.length}`}
                loading="lazy"
                onError={onImageError}
                className="max-h-[32rem] w-full rounded-control border border-contour bg-paper-deep object-contain"
              />
            </li>
          ))}
        </ul>
      )}
      {!memory.content && memory.images.length === 0 && (
        <p className="px-5 py-5 text-sm text-ink-muted">사진과 글 없이 남긴 기록이에요.</p>
      )}
      {memory.visibility === 'PUBLIC' && (
        <div className="px-5 py-4">
          <MemoryLikeButton memoryId={memory.memoryId} likedByMe={memory.likedByMe === true} likeCount={memory.likeCount} />
        </div>
      )}
    </article>
  );
}

function DeleteMemoryDialog({ memoryId, onClose }: { memoryId: number; onClose: () => void }) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDelete() {
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await deleteMemory(memoryId);
      navigate('/me/memories');
    } catch (cause) {
      setSubmitting(false);
      setError(toUserMessage(cause));
    }
  }

  return (
    <Dialog title="기록을 삭제할까요?" onClose={() => !submitting && onClose()}>
      <div className="flex flex-col gap-4">
        <p className="text-base text-ink">삭제한 기록은 되돌릴 수 없어요. 사진도 함께 사라져요.</p>
        {error && (
          <div role="alert">
            <Notice tone="danger" title="삭제하지 못했어요">
              <p>{error}</p>
            </Notice>
          </div>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            취소
          </Button>
          <Button variant="danger" loading={submitting} onClick={() => void handleDelete()}>
            삭제
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
