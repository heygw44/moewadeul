import { useEffect, useRef, useState, type ReactNode } from 'react';
import { deletePost, getPost, likePost, unlikePost } from '../../api/community';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import type { CommunityLikeResponse, CommunityPostDetail } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { AsideSection, HubLayout } from '../../components/HubLayout';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { Icon } from '../../components/icons';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { useDocumentTitle } from '../../components/useDocumentTitle';
import { formatKstDateTime, formatRelativeKst } from '../../lib/datetime';
import { withNext } from '../../app/session/nextPath';
import { useSession } from '../../app/session/session';
import { useViewerEpoch } from '../../app/session/useViewerEpoch';
import { CommunityComments } from './CommunityComments';
import { CommunityReportDialog } from './CommunityReportDialog';
import { SECONDARY_LINK_CLASS } from '../../components/buttonClass';

type LoadResult = { postId: number; post: CommunityPostDetail } | { postId: number; error: unknown };

export function CommunityPostDetailPage({ postId }: { postId: number }) {
  const viewerEpoch = useViewerEpoch();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<LoadResult | null>(null);
  const imageRetried = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    getPost(postId, controller.signal).then(
      (post) => {
        if (!controller.signal.aborted) setResult({ postId, post });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ postId, error });
      },
    );
    return () => controller.abort();
  }, [postId, attempt, viewerEpoch]);

  const current = result?.postId === postId ? result : null;
  const showSkeleton = useDelayedFlag(current === null);
  const loadedTitle = current !== null && 'post' in current ? current.post.title : null;

  useDocumentTitle(loadedTitle ? `${loadedTitle} · 뫼와들` : '커뮤니티 · 뫼와들');

  const reload = () => setAttempt((value) => value + 1);

  function handleImageError() {
    if (imageRetried.current) return;
    imageRetried.current = true;
    reload();
  }

  function applyLike(response: CommunityLikeResponse) {
    setResult((previous) =>
      previous !== null && 'post' in previous
        ? {
            ...previous,
            post: { ...previous.post, likedByMe: response.liked, likeCount: response.likeCount },
          }
        : previous,
    );
  }

  const asideNode = <PostAside post={current !== null && 'post' in current ? current.post : null} />;

  if (current === null) {
    return (
      <HubLayout title="커뮤니티" breadcrumb={<BackToCommunity />} aside={asideNode}>
        <div aria-busy="true">
          {showSkeleton && (
            <div className="flex flex-col gap-3 p-1">
              <Skeleton className="h-8 w-2/3" />
              <Skeleton className="h-40 w-full" />
            </div>
          )}
        </div>
      </HubLayout>
    );
  }

  if ('post' in current) {
    const post = current.post;
    return (
      <HubLayout title={post.title} breadcrumb={<BackToCommunity />} aside={asideNode} titleInContent>
        <PostContent
          post={post}
          meta={<PostMeta post={post} />}
          actions={<PostActions post={post} />}
          onLikeChanged={applyLike}
          onImageError={handleImageError}
        />
        <CommunityComments postId={postId} count={post.commentCount} onChanged={reload} />
        <div>
          <Link to="/community" className={SECONDARY_LINK_CLASS}>
            목록으로
          </Link>
        </div>
      </HubLayout>
    );
  }

  if (current.error instanceof ApiError && current.error.code === 'NOT_FOUND') {
    return (
      <HubLayout title="글을 찾을 수 없어요" breadcrumb={<BackToCommunity />} aside={asideNode}>
        <EmptyState framed title="삭제됐거나 볼 수 없는 글이에요" />
      </HubLayout>
    );
  }

  return (
    <HubLayout title="커뮤니티" breadcrumb={<BackToCommunity />} aside={asideNode}>
      <div className="flex flex-col gap-3">
        <ErrorNotice title="글을 불러오지 못했어요" error={current.error} />
        <div>
          <Button variant="secondary" onClick={reload}>
            다시 불러오기
          </Button>
        </div>
      </div>
    </HubLayout>
  );
}

const CRUMB_LINK = 'inline-flex min-h-11 items-center gap-1 text-accent underline underline-offset-2';

function BackToCommunity() {
  return (
    <Link to="/community" className={CRUMB_LINK}>
      <Icon name="chevronLeft" size={16} />
      커뮤니티
    </Link>
  );
}

function PostMeta({ post }: { post: CommunityPostDetail }) {
  const edited = post.updatedAt !== post.createdAt;
  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ink-muted">
      <Link
        to={`/members/${post.author.memberId}`}
        className="font-semibold text-ink underline-offset-2 hover:underline"
      >
        {post.author.nickname}
      </Link>
      <span aria-hidden="true">·</span>
      <time dateTime={post.createdAt} title={formatKstDateTime(post.createdAt)}>
        {formatRelativeKst(post.createdAt)}
      </time>
      <span aria-hidden="true">·</span>
      <span className="tabular-nums">조회 {post.viewCount}</span>
      {edited && (
        <>
          <span aria-hidden="true">·</span>
          <span>수정됨</span>
        </>
      )}
      {post.spot && (
        <>
          <span aria-hidden="true">·</span>
          <Link
            to={`/spots/${post.spot.spotId}`}
            className="inline-flex items-center gap-1 text-accent underline underline-offset-2"
          >
            <Icon name="map" size={14} />
            {post.spot.name}
          </Link>
        </>
      )}
    </div>
  );
}

function PostActions({ post }: { post: CommunityPostDetail }) {
  const session = useSession();
  const signedIn = session.status === 'authenticated' && session.me !== null;
  const unverified = signedIn && session.me?.status === 'UNVERIFIED';
  const mine = signedIn && session.me?.memberId === post.author.memberId;
  const [deleting, setDeleting] = useState(false);
  const [reporting, setReporting] = useState(false);

  if (!signedIn) return null;
  return (
    <>
      {mine ? (
        <>
          <Link to={`/community/${post.postId}/edit`} className={SECONDARY_LINK_CLASS}>
            수정
          </Link>
          <Button variant="danger-outline" onClick={() => setDeleting(true)}>
            삭제
          </Button>
        </>
      ) : (
        <Button variant="ghost" onClick={() => setReporting(true)} disabled={unverified}>
          신고
        </Button>
      )}
      {deleting && <DeletePostDialog postId={post.postId} onClose={() => setDeleting(false)} />}
      {reporting && (
        <CommunityReportDialog target={{ type: 'post', id: post.postId }} onClose={() => setReporting(false)} />
      )}
    </>
  );
}

function PostAside({ post }: { post: CommunityPostDetail | null }) {
  return (
    <>
      {post?.spot && (
        <AsideSection title="같은 장소의 글">
          <Link to={`/community?spotId=${post.spot.spotId}`} className={ASIDE_LINK}>
            {post.spot.name} 글 모아 보기
          </Link>
        </AsideSection>
      )}
      <AsideSection title="커뮤니티 안내">
        <ul className="list-disc pl-5">
          <li>
            동행은{' '}
            <Link to="/basecamps" className="font-semibold text-accent underline underline-offset-2">
              베이스캠프
            </Link>
            에서 구해요.
          </li>
          <li>금전을 요구하는 글은 신고해 주세요.</li>
          <li>공원 안에서 야영을 권하는 글은 신고 대상이에요.</li>
        </ul>
      </AsideSection>
    </>
  );
}

const ASIDE_LINK = 'inline-flex min-h-11 items-center text-base font-semibold text-accent underline underline-offset-2';

type PostContentProps = {
  post: CommunityPostDetail;
  meta: ReactNode;
  actions: ReactNode;
  onLikeChanged: (response: CommunityLikeResponse) => void;
  onImageError: () => void;
};

function PostContent({ post, meta, actions, onLikeChanged, onImageError }: PostContentProps) {
  return (
    <article className="flex flex-col">
      <header className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2 pb-5">
        <div className="flex min-w-0 flex-col gap-1.5">
          <p
            aria-hidden="true"
            className="text-2xl leading-tight font-extrabold tracking-[-0.03em] break-words text-ink sm:text-[1.75rem]"
          >
            {post.title}
          </p>
          {meta}
        </div>
        <div className="flex flex-wrap items-center gap-2">{actions}</div>
      </header>
      <p className="whitespace-pre-wrap break-words pb-6 text-base leading-7 text-ink">{post.content}</p>

      {post.images.length > 0 && (
        <ul className="flex flex-col gap-3 pb-6">
          {post.images.map((image, index) => (
            <li key={image.imageId}>
              <img
                src={image.url}
                alt={`글 사진 ${post.images.length}장 중 ${index + 1}번째`}
                loading="lazy"
                onError={onImageError}
                className="max-h-[32rem] w-full rounded-sheet bg-fill object-contain"
              />
            </li>
          ))}
        </ul>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2 rounded-sheet bg-fill px-3 py-1.5">
        <LikeButton post={post} onChanged={onLikeChanged} />
        <a href="#comments" className="inline-flex min-h-11 items-center gap-1 text-base text-ink-muted hover:text-ink">
          <Icon name="chat" size={18} />
          댓글 <span className="font-mono tabular-nums">{post.commentCount}</span>
        </a>
      </div>
    </article>
  );
}

function LikeButton({
  post,
  onChanged,
}: {
  post: CommunityPostDetail;
  onChanged: (response: CommunityLikeResponse) => void;
}) {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const liked = post.likedByMe === true;

  const content = (
    <>
      <Icon name="heart" size={20} fill={liked ? 'currentColor' : 'none'} />
      <span>좋아요</span>
      <span className="font-mono tabular-nums">{post.likeCount}</span>
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
      onChanged(liked ? await unlikePost(post.postId) : await likePost(post.postId));
    } catch (caught) {
      setError(toUserMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-1">
      <Button
        variant={liked ? 'primary' : 'secondary'}
        aria-pressed={liked}
        loading={busy}
        onClick={() => void toggle()}
      >
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

function DeletePostDialog({ postId, onClose }: { postId: number; onClose: () => void }) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<{ cause: unknown } | null>(null);

  async function handleDelete() {
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await deletePost(postId);
      navigate('/community');
    } catch (cause) {
      setSubmitting(false);
      setError({ cause });
    }
  }

  return (
    <Dialog title="글을 삭제할까요?" onClose={() => !submitting && onClose()}>
      <div className="flex flex-col gap-4">
        <p className="text-base text-ink">삭제한 글은 되돌릴 수 없어요. 댓글도 함께 사라져요.</p>
        {error && (
          <div role="alert">
            <ErrorNotice title="삭제하지 못했어요" error={error.cause} />
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
