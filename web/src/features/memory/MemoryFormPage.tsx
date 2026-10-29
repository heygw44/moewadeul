import { useEffect, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { createIdempotentRunner } from '../../api/idempotency';
import { createMemory, getMemory, requestMemoryImageUpload, updateMemory } from '../../api/memories';
import { fetchSpotDetail } from '../../api/spots';
import type { MemoryDetail, MemoryUpdateRequest, MemoryVisibility } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import { EmptyState } from '../../components/EmptyState';
import { AsideSection, HubLayout } from '../../components/HubLayout';
import { Icon } from '../../components/icons';
import { ImageUploadField } from '../../components/ImageUploadField';
import { Notice } from '../../components/Notice';
import { SelectField } from '../../components/SelectField';
import { Skeleton } from '../../components/Skeleton';
import { TextArea } from '../../components/TextArea';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { restorableImages, useImageUploads } from '../../components/useImageUploads';
import type { UploadImageItem } from '../../components/useImageUploads';
import { todayKst } from '../../lib/datetime';
import type { LatLng } from '../../map/types';
import { SECONDARY_LINK_CLASS } from '../community/communityLabels';
import { useDraftCapture, useRestoredDraft } from '../member/formDraft';
import { useSession } from '../member/session';
import { useRequireAuth } from '../member/useRequireAuth';
import { LocationLabel } from '../spot/LocationLabel';
import {
  MEMORY_CONTENT_MAX,
  MEMORY_MAX_IMAGES,
  NIGHTS_OPTIONS,
  parseLatLng,
  parsePositiveId,
  VISIBILITY_OPTIONS,
} from './memoryLabels';
import { useDocumentTitle } from '../../components/useDocumentTitle';

type MemoryFormPageProps = { mode: 'create' } | { mode: 'edit'; memoryId: number };

const CRUMB_LINK = 'inline-flex min-h-11 items-center gap-1 text-accent underline underline-offset-2';

export function MemoryFormPage(props: MemoryFormPageProps) {
  const session = useRequireAuth();
  const showSkeleton = useDelayedFlag(session.status === 'loading');
  const editMemoryId = props.mode === 'edit' ? props.memoryId : null;

  useDocumentTitle(props.mode === 'create' ? '기록 남기기 · 뫼와들' : '기록 수정 · 뫼와들');

  const layout = (children: ReactNode) => (
    <HubLayout
      title={props.mode === 'create' ? '기록 남기기' : '기록 수정'}
      description={props.mode === 'create' ? '다녀온 곳을 사진과 글로 남겨 두세요.' : undefined}
      breadcrumb={
        <Link to={editMemoryId === null ? '/map' : `/memories/${editMemoryId}`} className={CRUMB_LINK}>
          <Icon name="chevronLeft" size={16} />
          {editMemoryId === null ? '지도' : '기록으로 돌아가기'}
        </Link>
      }
      aside={<MemoryAside />}
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
        <Notice tone="warning" title="이메일 인증을 마치면 기록을 남길 수 있어요">
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
    editMemoryId === null ? <CreateForm /> : <EditLoader memoryId={editMemoryId} myMemberId={session.me.memberId} />,
  );
}

function MemoryAside() {
  return (
    <>
      <AsideSection title="공개 범위">
        <ul className="list-disc pl-5">
          <li>처음에는 나만 보기로 남아요. 공개로는 언제든 바꿀 수 있어요.</li>
          <li>전화번호나 주소 같은 개인정보는 적지 말아 주세요.</li>
        </ul>
      </AsideSection>
      <AsideSection title="사진">
        <ul className="list-disc pl-5">
          <li>JPEG, PNG, WebP 파일을 {MEMORY_MAX_IMAGES}장까지, 장당 5MB까지 올릴 수 있어요.</li>
          <li>맨 앞 사진이 대표 사진이 돼요. 순서는 &apos;앞으로&apos; 버튼으로 바꿀 수 있어요.</li>
        </ul>
      </AsideSection>
    </>
  );
}

function CreateForm() {
  const { search } = useLocation();
  const params = new URLSearchParams(search);
  const position = parseLatLng(params.get('lat'), params.get('lng'));
  const spotId = parsePositiveId(params.get('spotId'));
  if (position === null) {
    return (
      <EmptyState
        framed
        title="어디를 다녀왔는지 골라 주세요"
        description="지도에서 다녀온 곳을 길게 누르거나 우클릭한 뒤 '여기에 기록 남기기'를 눌러 주세요."
        action={
          <Link to="/map" className={SECONDARY_LINK_CLASS}>
            지도로 가기
          </Link>
        }
      />
    );
  }
  return <MemoryForm mode="create" position={position} initialSpot={spotId === null ? null : { spotId, name: null }} />;
}

type EditLoadResult = { memory: MemoryDetail } | { error: unknown };

function EditLoader({ memoryId, myMemberId }: { memoryId: number; myMemberId: number }) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<EditLoadResult | null>(null);
  const showSkeleton = useDelayedFlag(result === null);

  useEffect(() => {
    const controller = new AbortController();
    getMemory(memoryId, controller.signal).then(
      (memory) => {
        if (!controller.signal.aborted) setResult({ memory });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ error });
      },
    );
    return () => controller.abort();
  }, [memoryId, attempt]);

  const notMine = result !== null && 'memory' in result && result.memory.author.memberId !== myMemberId;
  useEffect(() => {
    if (notMine) navigate(`/memories/${memoryId}`, { replace: true });
  }, [notMine, memoryId]);

  if (result === null || notMine) {
    return showSkeleton ? (
      <div className="rounded-sheet bg-fill p-4 sm:p-5">
        <Skeleton className="h-64 w-full" />
      </div>
    ) : null;
  }
  if ('error' in result) {
    if (result.error instanceof ApiError && result.error.code === 'NOT_FOUND') {
      return <EmptyState framed title="기록을 찾을 수 없어요" description="삭제됐거나 볼 수 없는 기록이에요." />;
    }
    return (
      <div className="flex flex-col gap-3">
        <Notice tone="danger" title="기록을 불러오지 못했어요">
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
  const { memory } = result;
  const position = memory.lat !== undefined && memory.lng !== undefined ? { lat: memory.lat, lng: memory.lng } : null;
  return <MemoryForm mode="edit" memory={memory} position={position} initialSpot={memory.spot ?? null} />;
}

type LinkedSpot = { spotId: number; name: string | null };

type FormField = 'visitedDate' | 'nights' | 'content' | 'visibility' | 'imageIds';
type FormFieldErrors = Partial<Record<FormField, string>>;

function isFormField(field: string): field is FormField {
  return ['visitedDate', 'nights', 'content', 'visibility', 'imageIds'].includes(field);
}

type MemoryFormProps = { position: LatLng | null; initialSpot: LinkedSpot | null } & (
  { mode: 'create' } | { mode: 'edit'; memory: MemoryDetail }
);

type MemoryDraft = {
  visitedDate: string;
  nights: string;
  content: string;
  visibility: MemoryVisibility;
  spot: LinkedSpot | null;
  images: UploadImageItem[];
};

function MemoryForm(props: MemoryFormProps) {
  const initial = props.mode === 'edit' ? props.memory : null;
  const today = todayKst();
  const { pathname, search } = useLocation();
  const memberId = useSession().me?.memberId;
  const draft = useRestoredDraft<MemoryDraft>(pathname + search, memberId);
  const [visitedDate, setVisitedDate] = useState(draft?.visitedDate ?? initial?.visitedDate ?? today);
  const [nights, setNights] = useState(draft?.nights ?? String(initial?.nights ?? 1));
  const [content, setContent] = useState(draft?.content ?? initial?.content ?? '');
  const [visibility, setVisibility] = useState<MemoryVisibility>(draft?.visibility ?? initial?.visibility ?? 'PRIVATE');
  const [spot, setSpot] = useState<LinkedSpot | null>(draft ? draft.spot : props.initialSpot);
  const uploads = useImageUploads({
    max: MEMORY_MAX_IMAGES,
    initial: initial?.images,
    restored: draft?.images,
    requestUpload: requestMemoryImageUpload,
    thumbnail: true,
  });
  const [fieldErrors, setFieldErrors] = useState<FormFieldErrors>({});
  const [formError, setFormError] = useState<ApiError | string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [idempotentRunner] = useState(createIdempotentRunner);
  useDraftCapture<MemoryDraft>(pathname + search, memberId, {
    visitedDate,
    nights,
    content,
    visibility,
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
    if (visitedDate === '') errors.visitedDate = '다녀온 날을 골라 주세요.';
    else if (visitedDate > today) errors.visitedDate = '오늘이나 그 전 날짜를 골라 주세요.';
    if (content.length > MEMORY_CONTENT_MAX)
      errors.content = `${MEMORY_CONTENT_MAX.toLocaleString()}자 이하로 적어 주세요.`;
    return errors;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || uploading || failed) return;
    setFormError(null);
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    const trimmed = content.trim();
    const imageIds = uploads.imageIds;
    setSubmitting(true);
    try {
      if (props.mode === 'create') {
        if (props.position === null) return;
        const request = {
          lat: props.position.lat,
          lng: props.position.lng,
          visitedDate,
          nights: Number(nights),
          content: trimmed === '' ? undefined : trimmed,
          visibility,
          spotId: spot?.spotId,
          imageIds,
        };
        const created = await idempotentRunner.run(request, (key) => createMemory(request, key));
        navigate(`/memories/${created.memoryId}`);
        return;
      }
      const memory = props.memory;
      const changes = collectChanges(memory, {
        visitedDate,
        nights: Number(nights),
        content: trimmed,
        visibility,
        spot,
        imageIds,
      });
      if (Object.keys(changes).length > 0) await updateMemory(memory.memoryId, changes);
      navigate(`/memories/${memory.memoryId}`);
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

  const spotLabel = spot === null ? null : (spot.name ?? '선택한 장소');
  const cancelTarget = props.mode === 'edit' ? `/memories/${props.memory.memoryId}` : '/map';

  return (
    <form
      noValidate
      onSubmit={handleSubmit}
      aria-label={props.mode === 'create' ? '기록 남기기' : '기록 수정'}
      className="divide-y-4 divide-canvas overflow-hidden rounded-sheet bg-fill"
    >
      {formError && (
        <div role="alert" className="px-5 py-5">
          <Notice tone="danger" title={props.mode === 'create' ? '기록을 남기지 못했어요' : '기록을 수정하지 못했어요'}>
            <p>{typeof formError === 'string' ? formError : formError.message}</p>
            {typeof formError !== 'string' && formError.status >= 500 && formError.traceId && (
              <p className="mt-1 font-mono text-xs text-ink-muted">문의 번호 {formError.traceId}</p>
            )}
          </Notice>
        </div>
      )}

      <div className="flex flex-col gap-3 px-5 py-5">
        <p className="text-sm font-medium text-ink">다녀온 곳</p>
        {props.position !== null && <LocationLabel position={props.position} />}
        {spotLabel !== null && (
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

      <div className="grid grid-cols-2 gap-3 px-5 py-5">
        <TextField
          label="다녀온 날"
          name="visitedDate"
          type="date"
          max={today}
          value={visitedDate}
          disabled={submitting}
          onChange={(event) => setVisitedDate(event.target.value)}
          error={fieldErrors.visitedDate}
        />
        <SelectField
          label="머문 기간"
          name="nights"
          options={NIGHTS_OPTIONS}
          value={nights}
          disabled={submitting}
          onChange={(event) => setNights(event.target.value)}
          error={fieldErrors.nights}
        />
      </div>

      <div className="px-5 py-5">
        <TextArea
          label="글 (선택)"
          name="content"
          rows={8}
          maxLength={MEMORY_CONTENT_MAX}
          value={content}
          disabled={submitting}
          onChange={(event) => setContent(event.target.value)}
          error={fieldErrors.content}
        />
      </div>

      <ImageUploadField
        uploads={uploads}
        inputId="memory-image-input"
        error={fieldErrors.imageIds}
        disabled={submitting}
        className="px-5 py-5"
      />

      <div className="px-5 py-5">
        <ChoiceGroup
          legend="공개 범위"
          name="visibility"
          options={VISIBILITY_OPTIONS}
          value={visibility}
          onChange={setVisibility}
          hint="공개해도 정확한 위치는 나만 봐요."
          error={fieldErrors.visibility}
          disabled={submitting}
        />
      </div>

      <div className="sticky bottom-(--bottom-nav-h) flex flex-wrap items-start justify-end gap-2 bg-surface px-5 py-4 lg:static">
        <Link to={cancelTarget} className={SECONDARY_LINK_CLASS}>
          취소
        </Link>
        <Button type="submit" loading={submitting} disabled={uploading || failed} disabledReason={submitReason}>
          {props.mode === 'create' ? '남기기' : '수정하기'}
        </Button>
      </div>
    </form>
  );
}

type FormValues = {
  visitedDate: string;
  nights: number;
  content: string;
  visibility: MemoryVisibility;
  spot: LinkedSpot | null;
  imageIds: number[];
};

function collectChanges(memory: MemoryDetail, values: FormValues): MemoryUpdateRequest {
  const changes: MemoryUpdateRequest = {};
  if (values.visitedDate !== memory.visitedDate) changes.visitedDate = values.visitedDate;
  if (values.nights !== memory.nights) changes.nights = values.nights;
  if (values.content !== (memory.content ?? '')) changes.content = values.content === '' ? null : values.content;
  if (values.visibility !== memory.visibility) changes.visibility = values.visibility;
  if ((values.spot?.spotId ?? null) !== (memory.spot?.spotId ?? null)) changes.spotId = values.spot?.spotId ?? null;
  const savedIds = memory.images.map((image) => image.imageId);
  if (values.imageIds.length !== savedIds.length || values.imageIds.some((id, index) => id !== savedIds[index])) {
    changes.imageIds = values.imageIds;
  }
  return changes;
}
