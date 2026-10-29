import { useEffect, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { createPost, getPost, requestImageUpload, updatePost } from '../../api/community';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { createIdempotentRunner } from '../../api/idempotency';
import { fetchSpotDetail } from '../../api/spots';
import type { CommunityPostDetail, CommunityPostUpdateRequest } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { Icon } from '../../components/icons';
import { ImageUploadField } from '../../components/ImageUploadField';
import { Notice } from '../../components/Notice';
import { AsideSection, HubLayout } from '../../components/HubLayout';
import { Skeleton } from '../../components/Skeleton';
import { TextArea } from '../../components/TextArea';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { restorableImages, useImageUploads } from '../../components/useImageUploads';
import type { UploadImageItem } from '../../components/useImageUploads';
import { useDraftCapture, useRestoredDraft } from '../member/formDraft';
import { useSession } from '../member/session';
import { useRequireAuth } from '../member/useRequireAuth';
import { parseSpotId, SECONDARY_LINK_CLASS } from './communityLabels';

const TITLE_MAX = 100;
const CONTENT_MAX = 10000;
const MAX_IMAGES = 5;

type FormMode = 'create' | 'edit';

type CommunityPostFormPageProps = { mode: 'create' } | { mode: 'edit'; postId: number };

export function CommunityPostFormPage(props: CommunityPostFormPageProps) {
  const session = useRequireAuth();
  const showSkeleton = useDelayedFlag(session.status === 'loading');
  const editPostId = props.mode === 'edit' ? props.postId : null;

  useEffect(() => {
    document.title = props.mode === 'create' ? '글쓰기 · 뫼와들' : '글 수정 · 뫼와들';
  }, [props.mode]);

  const title = props.mode === 'create' ? '글쓰기' : '글 수정';
  const layout = (children: ReactNode) => (
    <HubLayout
      title={title}
      description={props.mode === 'create' ? '다녀온 이야기, 써 본 장비, 궁금한 점을 나눠 주세요.' : undefined}
      breadcrumb={
        <Link
          to={editPostId === null ? '/community' : `/community/${editPostId}`}
          className="inline-flex min-h-11 items-center gap-1 text-accent underline underline-offset-2"
        >
          <Icon name="chevronLeft" size={16} />
          {editPostId === null ? '커뮤니티' : '글로 돌아가기'}
        </Link>
      }
      aside={<WriteAside />}
    >
      {children}
    </HubLayout>
  );

  if (session.status !== 'authenticated' || !session.me) {
    return layout(
      showSkeleton ? (
        <div className="rounded-sheet bg-fill p-4 sm:p-5">
          <Skeleton className="h-40 w-full" />
        </div>
      ) : null,
    );
  }
  if (session.me.status === 'UNVERIFIED') {
    return layout(
      <div className="rounded-sheet bg-fill p-4 sm:p-5">
        <Notice tone="warning" title="이메일 인증을 마치면 글을 쓸 수 있어요">
          <div className="flex flex-col gap-3">
            <p>메일로 받은 인증 코드만 입력하면 돼요.</p>
            <div>
              <Link to="/verify-email" className={SECONDARY_LINK_CLASS}>
                이메일 인증하기
              </Link>
            </div>
          </div>
        </Notice>
      </div>,
    );
  }

  return layout(
    editPostId === null ? <CreateForm /> : <EditLoader postId={editPostId} myMemberId={session.me.memberId} />,
  );
}

function WriteAside() {
  return (
    <>
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
          <li>공원 내에서 야영을 권하는 글은 신고 대상이에요.</li>
          <li>전화번호나 주소 같은 개인정보는 적지 말아 주세요.</li>
        </ul>
      </AsideSection>
      <AsideSection title="사진">
        <ul className="list-disc pl-5">
          <li>JPEG, PNG, WebP 파일을 5장까지, 장당 5MB까지 올릴 수 있어요.</li>
          <li>맨 앞 사진이 대표 사진이 돼요. 순서는 &apos;앞으로&apos; 버튼으로 바꿀 수 있어요.</li>
        </ul>
      </AsideSection>
    </>
  );
}

function CreateForm() {
  const { search } = useLocation();
  const spotId = parseSpotId(new URLSearchParams(search).get('spotId'));
  return <PostForm mode="create" initialSpot={spotId === null ? null : { spotId, name: null }} />;
}

type EditLoadResult = { post: CommunityPostDetail } | { error: unknown };

function EditLoader({ postId, myMemberId }: { postId: number; myMemberId: number }) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<EditLoadResult | null>(null);
  const showSkeleton = useDelayedFlag(result === null);

  useEffect(() => {
    const controller = new AbortController();
    getPost(postId, controller.signal).then(
      (post) => {
        if (!controller.signal.aborted) setResult({ post });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ error });
      },
    );
    return () => controller.abort();
  }, [postId, attempt]);

  const notMine = result !== null && 'post' in result && result.post.author.memberId !== myMemberId;
  useEffect(() => {
    if (notMine) navigate(`/community/${postId}`, { replace: true });
  }, [notMine, postId]);

  if (result === null || notMine) {
    return showSkeleton ? (
      <div className="rounded-sheet bg-fill p-4 sm:p-5">
        <Skeleton className="h-64 w-full" />
      </div>
    ) : null;
  }
  if ('error' in result) {
    if (result.error instanceof ApiError && result.error.code === 'NOT_FOUND') {
      return <EmptyState framed title="글을 찾을 수 없어요" description="삭제됐거나 볼 수 없는 글이에요." />;
    }
    return (
      <div className="flex flex-col gap-3">
        <Notice tone="danger" title="글을 불러오지 못했어요">
          {toUserMessage(result.error)}
        </Notice>
        <div>
          <Button
            variant="secondary"
            onClick={() => {
              setResult(null);
              setAttempt((value) => value + 1);
            }}
          >
            다시 불러오기
          </Button>
        </div>
      </div>
    );
  }
  return <PostForm mode="edit" post={result.post} initialSpot={result.post.spot ?? null} />;
}

type LinkedSpot = { spotId: number; name: string | null };

type FormField = 'title' | 'content' | 'imageIds';
type FormFieldErrors = Partial<Record<FormField, string>>;

function isFormField(field: string): field is FormField {
  return field === 'title' || field === 'content' || field === 'imageIds';
}

type PostFormProps = { initialSpot: LinkedSpot | null } & (
  { mode: 'create' } | { mode: 'edit'; post: CommunityPostDetail }
);

type PostDraft = { title: string; content: string; spot: LinkedSpot | null; images: UploadImageItem[] };

function PostForm(props: PostFormProps) {
  const mode: FormMode = props.mode;
  const initialPost = props.mode === 'edit' ? props.post : null;
  const { pathname, search } = useLocation();
  const memberId = useSession().me?.memberId;
  const draft = useRestoredDraft<PostDraft>(pathname + search, memberId);
  const [title, setTitle] = useState(draft?.title ?? initialPost?.title ?? '');
  const [content, setContent] = useState(draft?.content ?? initialPost?.content ?? '');
  const [spot, setSpot] = useState<LinkedSpot | null>(draft ? draft.spot : props.initialSpot);
  const uploads = useImageUploads({
    max: MAX_IMAGES,
    initial: initialPost?.images,
    restored: draft?.images,
    requestUpload: requestImageUpload,
    thumbnail: true,
  });
  const [fieldErrors, setFieldErrors] = useState<FormFieldErrors>({});
  const [formError, setFormError] = useState<ApiError | string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [idempotentRunner] = useState(createIdempotentRunner);
  useDraftCapture<PostDraft>(pathname + search, memberId, {
    title,
    content,
    spot,
    images: restorableImages(uploads.images),
  });

  const unnamedSpotId = spot !== null && spot.name === null ? spot.spotId : null;
  useEffect(() => {
    if (unnamedSpotId === null) return;
    const controller = new AbortController();
    fetchSpotDetail(unnamedSpotId, controller.signal).then(
      (detail) => {
        if (controller.signal.aborted) return;
        setSpot((previous) =>
          previous !== null && previous.spotId === unnamedSpotId
            ? { spotId: unnamedSpotId, name: detail.name }
            : previous,
        );
      },
      () => undefined,
    );
    return () => controller.abort();
  }, [unnamedSpotId]);

  const { uploading, failed } = uploads;

  function validate(): FormFieldErrors {
    const errors: FormFieldErrors = {};
    if (title.trim() === '') errors.title = '제목을 적어 주세요.';
    else if (title.length > TITLE_MAX) errors.title = `${TITLE_MAX}자 이하로 적어 주세요.`;
    if (content.trim() === '') errors.content = '내용을 적어 주세요.';
    else if (content.length > CONTENT_MAX) errors.content = `${CONTENT_MAX.toLocaleString()}자 이하로 적어 주세요.`;
    return errors;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || uploading || failed) return;
    setFormError(null);
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    const imageIds = uploads.imageIds;
    setSubmitting(true);
    try {
      if (props.mode === 'create') {
        const request = {
          title: title.trim(),
          content: content.trim(),
          spotId: spot?.spotId,
          imageIds,
        };
        const created = await idempotentRunner.run(request, (key) => createPost(request, key));
        navigate(`/community/${created.postId}`);
        return;
      }
      const post = props.post;
      const changes: CommunityPostUpdateRequest = {};
      if (title.trim() !== post.title) changes.title = title.trim();
      if (content.trim() !== post.content) changes.content = content.trim();
      if ((spot?.spotId ?? null) !== (post.spot?.spotId ?? null)) changes.spotId = spot?.spotId ?? null;
      const savedIds = post.images.map((image) => image.imageId);
      if (imageIds.length !== savedIds.length || imageIds.some((id, index) => id !== savedIds[index])) {
        changes.imageIds = imageIds;
      }
      if (Object.keys(changes).length > 0) await updatePost(post.postId, changes);
      navigate(`/community/${post.postId}`);
    } catch (error) {
      setSubmitting(false);
      handleError(error);
    }
  }

  function handleError(error: unknown) {
    if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const errors: FormFieldErrors = {};
      const others: string[] = [];
      for (const { field, reason } of error.fieldErrors) {
        if (isFormField(field)) errors[field] = reason;
        else others.push(reason);
      }
      setFieldErrors(errors);
      if (others.length > 0) setFormError(others.join(' '));
      return;
    }
    setFormError(error instanceof ApiError ? error : toUserMessage(error));
  }

  let submitReason: string | undefined;
  if (uploading) submitReason = '사진을 올리는 중이에요. 다 올라가면 저장할 수 있어요.';
  else if (failed) submitReason = '올리지 못한 사진은 다시 올리거나 빼 주세요.';

  const spotLabel = spot === null ? null : spot.name === null ? '선택한 장소' : spot.name;
  const cancelTarget = props.mode === 'edit' ? `/community/${props.post.postId}` : '/community';

  return (
    <form
      noValidate
      onSubmit={handleSubmit}
      aria-label={mode === 'create' ? '글쓰기' : '글 수정'}
      className="divide-y-4 divide-canvas overflow-hidden rounded-sheet bg-fill"
    >
      {formError && (
        <div role="alert" className="px-5 py-5">
          <Notice tone="danger" title={mode === 'create' ? '글을 올리지 못했어요' : '글을 수정하지 못했어요'}>
            <p>{typeof formError === 'string' ? formError : formError.message}</p>
            {typeof formError !== 'string' && formError.status >= 500 && formError.traceId && (
              <p className="mt-1 font-mono text-xs text-ink-muted">문의 번호 {formError.traceId}</p>
            )}
          </Notice>
        </div>
      )}

      <div className="flex flex-col gap-3 px-5 py-5">
        <div className="flex flex-col gap-1">
          <TextField
            label="제목"
            name="title"
            maxLength={TITLE_MAX}
            value={title}
            disabled={submitting}
            onChange={(event) => setTitle(event.target.value)}
            error={fieldErrors.title}
          />
          <p className="text-right font-mono text-xs tabular-nums text-ink-muted">
            {title.length}/{TITLE_MAX}
          </p>
        </div>
      </div>

      <div className="flex flex-col gap-3 px-5 py-5">
        <TextArea
          label="내용"
          name="content"
          rows={12}
          maxLength={CONTENT_MAX}
          value={content}
          disabled={submitting}
          onChange={(event) => setContent(event.target.value)}
          error={fieldErrors.content}
        />
      </div>

      <ImageUploadField
        uploads={uploads}
        inputId="community-image-input"
        error={fieldErrors.imageIds}
        disabled={submitting}
        className="px-5 py-5"
      />

      <div className="flex flex-col gap-3 px-5 py-5">
        <p className="text-sm font-medium text-ink">연결한 장소</p>
        {spotLabel === null ? (
          <p className="text-sm text-ink-muted">
            지도에서 장소를 열고 &apos;이 장소로 글쓰기&apos;를 누르면 장소가 연결돼요.
          </p>
        ) : (
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="inline-flex min-h-11 items-center gap-2 text-base text-ink">
              <Icon name="map" size={18} className="shrink-0 text-ink-muted" />
              {spotLabel}
            </span>
            <Button
              variant="ghost"
              aria-label={`${spotLabel} 연결 해제`}
              disabled={submitting}
              onClick={() => setSpot(null)}
            >
              연결 해제
            </Button>
          </div>
        )}
      </div>

      <div className="sticky bottom-(--bottom-nav-h) flex flex-wrap items-start justify-end gap-2 bg-surface px-5 py-4 lg:static">
        <Link to={cancelTarget} className={SECONDARY_LINK_CLASS}>
          취소
        </Link>
        <Button type="submit" loading={submitting} disabled={uploading || failed} disabledReason={submitReason}>
          {mode === 'create' ? '올리기' : '수정하기'}
        </Button>
      </div>
    </form>
  );
}
