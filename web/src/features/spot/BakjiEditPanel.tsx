import { useEffect, useEffectEvent, useRef, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { updateBakji } from '../../api/bakjis';
import { ApiError } from '../../api/client';
import { fetchSpotDetail } from '../../api/spots';
import type { BakjiDetail, BakjiSubmissionResponse, BakjiUpdateRequest, SpotDetail } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { Icon } from '../../components/icons';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import type { LatLng } from '../../map/types';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { BakjiFields, BakjiResultView } from './BakjiFormParts';
import {
  GROUND_OPTIONS_WITH_KEEP,
  PRIMARY_LINK_CLASS,
  requiredFieldErrors,
  roundCoordinate,
  submitErrorState,
  useLocationErrorReset,
} from './bakjiFormModel';
import type { BakjiFormValues, FieldErrors, FormError } from './bakjiFormModel';

type BakjiEditPanelProps = {
  spotId: number;
  draft: LatLng | null;
  // 고칠 박지를 불러오면 알린다. 지도 화면은 그 좌표에 위치 핀을 찍고 지도를 옮긴다.
  onLoaded: (detail: SpotDetail) => void;
  onCancel: () => void;
  onSubmitted: (response: BakjiSubmissionResponse) => void;
};

type Original = { name: string; lat: number; lng: number; bakji: BakjiDetail };

// 어느 시도의 결과인지 함께 기억해서, 다시 불러오면 이전 결과를 곧바로 버린다.
type LoadResult = { attempt: number; detail: SpotDetail } | { attempt: number; error: unknown };

// 제보한 회원이 자기 박지의 위치와 정보를 고친다. 권한은 서버가 다시 확인한다.
export function BakjiEditPanel({ spotId, draft, onLoaded, onCancel, onSubmitted }: BakjiEditPanelProps) {
  const session = useSession();
  const location = useLocation();
  const showSessionSkeleton = useDelayedFlag(session.status === 'loading');

  let body: ReactNode;
  if (session.status === 'loading') {
    body = showSessionSkeleton ? <FormSkeleton /> : null;
  } else if (session.status === 'anonymous' || session.me === null) {
    body = (
      <EmptyState
        title="로그인하면 제보한 박지를 고칠 수 있어요"
        action={
          <Link to={withNext('/login', `${location.pathname}${location.search}`)} className={PRIMARY_LINK_CLASS}>
            로그인
          </Link>
        }
      />
    );
  } else {
    body = (
      <EditLoader
        spotId={spotId}
        memberId={session.me.memberId}
        draft={draft}
        onLoaded={onLoaded}
        onSubmitted={onSubmitted}
      />
    );
  }

  return (
    <div className="flex flex-col">
      <div className="sticky top-0 z-10 flex items-center bg-surface px-2 lg:px-4">
        <button
          type="button"
          aria-label="장소 상세로 돌아가기"
          onClick={onCancel}
          className="flex size-11 items-center justify-center rounded-control text-ink hover:bg-fill"
        >
          <Icon name="chevronLeft" size={24} />
        </button>
      </div>
      <div className="px-4 py-4">{body}</div>
    </div>
  );
}

function FormSkeleton() {
  return (
    <div aria-busy="true" className="flex flex-col gap-3">
      <Skeleton className="h-7 w-1/2" />
      <Skeleton className="h-11 w-full" />
      <Skeleton className="h-24 w-full" />
    </div>
  );
}

function EditLoader({
  spotId,
  memberId,
  draft,
  onLoaded,
  onSubmitted,
}: {
  spotId: number;
  memberId: number;
  draft: LatLng | null;
  onLoaded: (detail: SpotDetail) => void;
  onSubmitted: (response: BakjiSubmissionResponse) => void;
}) {
  const [attempt, setAttempt] = useState(0);
  const [loaded, setLoaded] = useState<LoadResult | null>(null);
  const [result, setResult] = useState<BakjiSubmissionResponse | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchSpotDetail(spotId, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setLoaded({ attempt, detail });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setLoaded({ attempt, error });
      },
    );
    return () => controller.abort();
  }, [spotId, attempt]);

  const current = loaded && loaded.attempt === attempt ? loaded : null;
  const detail = current && 'detail' in current ? current.detail : null;
  const bakji = detail?.bakji ?? null;
  const notMine = bakji !== null && bakji.reporter.memberId !== memberId;
  const editable = detail !== null && bakji !== null && !notMine ? detail : null;
  const showSkeleton = useDelayedFlag(current === null);

  // 다른 회원의 박지는 고칠 수 없으므로 장소 상세로 돌려보낸다.
  useEffect(() => {
    if (notMine) navigate(`/spots/${spotId}`, { replace: true });
  }, [notMine, spotId]);

  const reportLoaded = useEffectEvent((value: SpotDetail) => onLoaded(value));
  useEffect(() => {
    if (editable !== null) reportLoaded(editable);
  }, [editable]);

  if (result) {
    return <BakjiResultView title="박지 정보를 고쳤어요" linkLabel="고친 박지 보기" result={result} />;
  }
  if (current === null || notMine) return showSkeleton ? <FormSkeleton /> : null;
  if (editable !== null && editable.bakji !== null) {
    return (
      <EditForm
        spotId={spotId}
        original={{ name: editable.name, lat: editable.lat, lng: editable.lng, bakji: editable.bakji }}
        draft={draft}
        onSubmitted={(response) => {
          onSubmitted(response);
          setResult(response);
        }}
      />
    );
  }
  // 장소는 있지만 박지가 아니면 고칠 수 있는 박지가 없는 것과 같다.
  if (
    detail !== null ||
    ('error' in current && current.error instanceof ApiError && current.error.code === 'NOT_FOUND')
  ) {
    return <EmptyState title="박지를 찾을 수 없어요" description="삭제됐거나 숨겨진 박지예요." />;
  }
  return (
    <div className="flex flex-col gap-3">
      <ErrorNotice title="박지 정보를 불러오지 못했어요" error={'error' in current ? current.error : null} />
      <div>
        <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
          다시 불러오기
        </Button>
      </div>
    </div>
  );
}

function initialValues(original: Original): BakjiFormValues {
  const { bakji } = original;
  return {
    name: original.name,
    description: bakji.description ?? '',
    hasWater: bakji.hasWater ? 'YES' : 'NO',
    hasToilet: bakji.hasToilet ? 'YES' : 'NO',
    signalLevel: bakji.signalLevel ?? 'UNKNOWN',
    groundType: 'KEEP',
  };
}

// 처음 값과 달라진 필드만 담는다. 서버는 빠진 필드를 그대로 두고, null로 보낸 설명·통신 상태·바닥은 지운다.
function changedFields(original: Original, draft: LatLng, values: BakjiFormValues): BakjiUpdateRequest {
  const { bakji } = original;
  const patch: BakjiUpdateRequest = {};
  const name = values.name.trim();
  if (name !== original.name) patch.name = name;
  const lat = roundCoordinate(draft.lat);
  const lng = roundCoordinate(draft.lng);
  // 좌표는 두 값을 함께 보내야 하므로 하나만 바뀌어도 둘 다 보낸다.
  if (lat !== roundCoordinate(original.lat) || lng !== roundCoordinate(original.lng)) {
    patch.lat = lat;
    patch.lng = lng;
  }
  const description = values.description.trim();
  if (description !== (bakji.description ?? '')) patch.description = description === '' ? null : description;
  const hasWater = values.hasWater === 'YES';
  if (hasWater !== bakji.hasWater) patch.hasWater = hasWater;
  const hasToilet = values.hasToilet === 'YES';
  if (hasToilet !== bakji.hasToilet) patch.hasToilet = hasToilet;
  const signalLevel = values.signalLevel === 'UNKNOWN' ? null : values.signalLevel;
  if (signalLevel !== bakji.signalLevel) patch.signalLevel = signalLevel;
  if (values.groundType !== 'KEEP') patch.groundType = values.groundType === 'UNKNOWN' ? null : values.groundType;
  return patch;
}

function EditForm({
  spotId,
  original,
  draft,
  onSubmitted,
}: {
  spotId: number;
  original: Original;
  draft: LatLng | null;
  onSubmitted: (response: BakjiSubmissionResponse) => void;
}) {
  const [values, setValues] = useState(() => initialValues(original));
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<FormError>(null);
  const [submitting, setSubmitting] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);
  useLocationErrorReset(draft, fieldErrors, setFieldErrors);

  // 요청 중에 화면을 벗어나면 응답을 기다리지 않는다.
  useEffect(() => () => controllerRef.current?.abort(), []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const errors = requiredFieldErrors(draft, values);
    setFieldErrors(errors);
    setFormError(null);
    if (draft === null || Object.keys(errors).length > 0) return;

    const patch = changedFields(original, draft, values);
    // 바꾼 것이 없으면 서버에 묻지 않고 상세로 돌아간다.
    if (Object.keys(patch).length === 0) {
      navigate(`/spots/${spotId}`);
      return;
    }

    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setSubmitting(true);
    try {
      const response = await updateBakji(spotId, patch, controller.signal);
      if (controller.signal.aborted) return;
      onSubmitted(response);
    } catch (error) {
      if (controller.signal.aborted) return;
      setSubmitting(false);
      const next = submitErrorState(error);
      setFieldErrors(next.fieldErrors);
      setFormError(next.formError);
    }
  }

  return (
    <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
      <h2 className="text-xl font-bold tracking-tight text-ink">박지 수정</h2>

      {formError && (
        <div role="alert">
          <ErrorNotice title="고치지 못했어요" error={formError.cause} />
        </div>
      )}

      <BakjiFields
        draft={draft}
        values={values}
        onChange={setValues}
        fieldErrors={fieldErrors}
        groundOptions={GROUND_OPTIONS_WITH_KEEP}
        groundHint="바닥을 바꿀 때만 골라 주세요."
      />

      <Button type="submit" fullWidth loading={submitting}>
        저장하기
      </Button>
    </form>
  );
}
