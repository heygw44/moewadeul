import { useEffect, useMemo, useRef, useState } from 'react';
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
import { StatusText } from '../../components/StatusText';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatLocalDate } from '../../lib/datetime';
import type { LatLng } from '../../map/types';
import { SECONDARY_LINK_CLASS } from '../community/communityLabels';
import { useSession } from '../member/session';
import { LocationLabel } from '../spot/LocationLabel';
import { ApproxLocationLabel } from './ApproxLocationLabel';
import { MEMORY_STATUS_LABEL, nightsLabel, VISIBILITY_LABEL } from './memoryLabels';
import { MemoryLikeButton } from './MemoryLikeButton';
import { MemoryMap } from './MemoryMap';
import { MemoryPhotoCarousel } from './MemoryPhotoCarousel';
import { MemoryReportDialog } from './MemoryReportDialog';

type LoadResult = { memoryId: number; memory: MemoryDetail } | { memoryId: number; error: unknown };

const CRUMB_LINK = 'inline-flex min-h-11 items-center gap-1 text-ink underline underline-offset-2';

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
  const title = current !== null && 'memory' in current ? titleOf(current.memory) : '기록';

  useEffect(() => {
    document.title = `${title} · 뫼와들`;
  }, [title]);

  const reload = () => setAttempt((value) => value + 1);
  // 작성자는 내 기록 지도로, 그 밖의 사람은 피드로 돌아간다. 아직 모르거나 기록을 못 받았을 때는 피드로 둔다.
  const authorId = current !== null && 'memory' in current ? current.memory.author.memberId : null;
  const viewerIsAuthor = authorId !== null && session.status === 'authenticated' && session.me?.memberId === authorId;
  const breadcrumb = (
    <Link to={viewerIsAuthor ? '/me/memories' : '/memories'} className={CRUMB_LINK}>
      <Icon name="chevronLeft" size={16} />
      {viewerIsAuthor ? '내 기록 지도' : '피드'}
    </Link>
  );

  if (current === null) {
    return (
      <HubLayout title="기록" breadcrumb={breadcrumb} width="narrow">
        <div aria-busy="true">
          {showSkeleton && (
            <div className="flex flex-col gap-3">
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
        actions={mine ? <MemoryActions memoryId={memory.memoryId} /> : <ReportAction memory={memory} />}
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
        <EmptyState framed title="삭제됐거나 볼 수 없는 기록이에요" description="나만 보기 기록은 남긴 사람만 열 수 있어요." />
      </HubLayout>
    );
  }

  return (
    <HubLayout title="기록" breadcrumb={breadcrumb} width="narrow">
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
  return memory.spot ? memory.spot.name : `${formatLocalDate(memory.visitedDate)} 기록`;
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
      {memory.status !== undefined && memory.status !== 'ACTIVE' && (
        <>
          <span aria-hidden="true">·</span>
          <StatusText tone="warning">{MEMORY_STATUS_LABEL[memory.status]}</StatusText>
        </>
      )}
      {memory.spot && (
        <>
          <span aria-hidden="true">·</span>
          <Link
            to={`/spots/${memory.spot.spotId}`}
            className="inline-flex items-center gap-1 text-ink underline underline-offset-2"
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

// 로그인한 회원이 남의 공개 기록을 볼 때만 신고를 보여 준다. 이메일 인증 여부와 신고할 수 있는지는 서버가 다시 확인한다.
function ReportAction({ memory }: { memory: MemoryDetail }) {
  const session = useSession();
  const [reporting, setReporting] = useState(false);
  const signedIn = session.status === 'authenticated' && session.me !== null;
  if (!signedIn || memory.visibility !== 'PUBLIC') return null;
  const unverified = session.me?.status === 'UNVERIFIED';
  return (
    <>
      <Button variant="ghost" onClick={() => setReporting(true)} disabled={unverified}>
        신고
      </Button>
      {reporting && <MemoryReportDialog memoryId={memory.memoryId} onClose={() => setReporting(false)} />}
    </>
  );
}

// 신고가 쌓여 검토 중이거나 관리자가 숨긴 기록은 작성자만 본다. 작성자에게 왜 다른 사람에게 보이지 않는지 알린다.
function HiddenStatusNotice({ status }: { status: MemoryDetail['status'] }) {
  if (status === undefined || status === 'ACTIVE') return null;
  const reviewing = status === 'PENDING_REVIEW';
  return (
    <Notice tone="warning" title={reviewing ? '신고가 쌓여 검토 중인 기록이에요' : '관리자가 숨긴 기록이에요'}>
      <p>
        {reviewing
          ? '관리자가 검토하는 동안 다른 사람에게 보이지 않아요. 수정과 삭제는 할 수 있어요.'
          : '다른 사람에게 보이지 않아요. 공개로 바꿔도 마찬가지예요. 수정과 삭제는 할 수 있어요.'}
      </p>
    </Notice>
  );
}

// 데스크톱은 왼쪽에 글·사진·좋아요, 오른쪽에 따라 내려오는 위치 칸을 둔다. 폰에서는 위치 칸이 아래로 간다.
function MemoryContent({ memory, onImageError }: { memory: MemoryDetail; onImageError: () => void }) {
  const position = memory.lat !== undefined && memory.lng !== undefined ? { lat: memory.lat, lng: memory.lng } : null;
  // 작성자가 아닌 사람에게는 정확한 좌표 대신 약 1km로 줄인 좌표만 온다.
  const approxPosition =
    memory.approxLat !== undefined && memory.approxLng !== undefined
      ? { lat: memory.approxLat, lng: memory.approxLng }
      : null;
  // 검토 중이거나 숨긴 기록은 서버가 좋아요를 받지 않는다.
  const likeable = memory.visibility === 'PUBLIC' && (memory.status === undefined || memory.status === 'ACTIVE');
  return (
    <div className="flex flex-col gap-8 lg:grid lg:grid-cols-[7fr_4fr] lg:items-start lg:gap-16">
      <article className="flex min-w-0 flex-col gap-8">
        <HiddenStatusNotice status={memory.status} />
        {memory.images.length > 0 && (
          <MemoryPhotoCarousel images={memory.images} owner={memory.author.nickname} onImageError={onImageError} />
        )}
        {memory.content && (
          <p className="max-w-[40em] whitespace-pre-wrap break-words text-[18px] leading-[1.75] text-ink">{memory.content}</p>
        )}
        {!memory.content && memory.images.length === 0 && (
          <p className="text-sm text-ink-muted">사진도 글도 없는 기록이에요.</p>
        )}
        {likeable && (
          <div>
            <MemoryLikeButton memoryId={memory.memoryId} likedByMe={memory.likedByMe === true} likeCount={memory.likeCount} />
          </div>
        )}
      </article>
      {(position !== null || approxPosition !== null) && (
        <LocationAside memory={memory} position={position} approxPosition={approxPosition} />
      )}
    </div>
  );
}

// 다녀온 곳을 작은 지도와 근거 표로 보여 준다. 작성자는 정확한 위치를, 다른 사람은 약 1km로 줄인 위치를 본다.
function LocationAside({
  memory,
  position,
  approxPosition,
}: {
  memory: MemoryDetail;
  position: LatLng | null;
  approxPosition: LatLng | null;
}) {
  const shown = position ?? approxPosition;
  const pins = useMemo(
    () => (shown ? [{ id: memory.memoryId, position: shown, thumbnailUrl: memory.images[0]?.url, label: titleOf(memory) }] : []),
    [memory, shown],
  );
  return (
    <aside aria-label="다녀온 곳" className="flex flex-col gap-4 lg:sticky lg:top-24">
      <MemoryMap pins={pins} selectedId={null} onSelect={() => {}} heightClass="h-56" />
      {position !== null ? (
        <div className="flex flex-col gap-1">
          <p className="text-sm text-ink-muted">다녀온 곳 (정확한 위치, 나만 봐요)</p>
          <LocationLabel position={position} />
        </div>
      ) : (
        approxPosition !== null && (
          <>
            <div className="flex flex-col gap-1">
              <p className="text-sm text-ink-muted">다녀온 곳 (대략 위치)</p>
              <ApproxLocationLabel position={approxPosition} />
            </div>
            <p className="text-sm text-ink-muted">공개 기록은 위치를 약 1km 범위로만 보여 줘요. 정확한 자리는 쓴 사람만 봐요.</p>
          </>
        )
      )}
    </aside>
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
