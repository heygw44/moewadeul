import { useEffect, useRef, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { createBakji } from '../../api/bakjis';
import type { BakjiCreateRequest, BakjiSubmissionResponse } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { Icon } from '../../components/icons';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { roundCoordinate } from '../../lib/geo';
import type { LatLng } from '../../map/types';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { BakjiFields, BakjiResultView } from './BakjiFormParts';
import {
  GROUND_OPTIONS,
  PRIMARY_LINK_CLASS,
  requiredFieldErrors,
  submitErrorState,
  useLocationErrorReset,
} from './bakjiFormModel';
import type { BakjiFormValues, FieldErrors, FormError } from './bakjiFormModel';

type BakjiReportPanelProps = {
  draft: LatLng | null;
  onCancel: () => void;
  onSubmitted: (response: BakjiSubmissionResponse) => void;
  nextBasecamp?: boolean;
};

const EMPTY_VALUES: BakjiFormValues = {
  name: '',
  description: '',
  hasWater: null,
  hasToilet: null,
  signalLevel: 'UNKNOWN',
  groundType: 'UNKNOWN',
};

export function BakjiReportPanel({ draft, onCancel, onSubmitted, nextBasecamp = false }: BakjiReportPanelProps) {
  const session = useSession();
  const location = useLocation();
  const showSessionSkeleton = useDelayedFlag(session.status === 'loading');
  const [result, setResult] = useState<BakjiSubmissionResponse | null>(null);

  let body: ReactNode;
  if (result) {
    body = (
      <BakjiResultView
        title="제보를 등록했어요"
        linkLabel="제보한 박지 보기"
        result={result}
        warnedNote={
          nextBasecamp && (
            <p className="text-base font-semibold text-danger">
              이 박지에서는 베이스캠프를 열 수 없어요. 다른 장소를 골라 주세요.
            </p>
          )
        }
      />
    );
  } else if (session.status === 'loading') {
    body = showSessionSkeleton ? (
      <div aria-busy="true" className="flex flex-col gap-3">
        <Skeleton className="h-7 w-1/2" />
        <Skeleton className="h-11 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    ) : null;
  } else if (session.status === 'anonymous' || session.me === null) {
    body = (
      <EmptyState
        title="로그인하면 박지를 제보할 수 있어요"
        description="제보한 박지는 지도에 바로 올라가요. 다른 회원이 다녀와서 확인하거나 문제를 신고해요."
        action={
          <Link to={withNext('/login', `${location.pathname}${location.search}`)} className={PRIMARY_LINK_CLASS}>
            로그인
          </Link>
        }
      />
    );
  } else {
    body = (
      <ReportForm
        draft={draft}
        unverified={session.me.status === 'UNVERIFIED'}
        nextBasecamp={nextBasecamp}
        onSubmitted={(response) => {
          onSubmitted(response);
          if (nextBasecamp && !response.parkWarning.warned) {
            navigate(`/basecamps/new?spotId=${response.spotId}`, { replace: true });
            return;
          }
          setResult(response);
        }}
      />
    );
  }

  return (
    <div className="flex flex-col">
      <div className="sticky top-0 z-10 flex items-center bg-surface px-2 lg:px-4">
        <button
          type="button"
          aria-label="지도로 돌아가기"
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

function ReportForm({
  draft,
  unverified,
  nextBasecamp,
  onSubmitted,
}: {
  draft: LatLng | null;
  unverified: boolean;
  nextBasecamp: boolean;
  onSubmitted: (response: BakjiSubmissionResponse) => void;
}) {
  const [values, setValues] = useState(EMPTY_VALUES);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<FormError>(null);
  const [submitting, setSubmitting] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);
  useLocationErrorReset(draft, fieldErrors, setFieldErrors);

  useEffect(() => () => controllerRef.current?.abort(), []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || unverified) return;

    const errors = requiredFieldErrors(draft, values);
    setFieldErrors(errors);
    setFormError(null);
    const { hasWater, hasToilet, signalLevel, groundType } = values;
    if (draft === null || hasWater === null || hasToilet === null || Object.keys(errors).length > 0) return;

    const request: BakjiCreateRequest = {
      name: values.name.trim(),
      lat: roundCoordinate(draft.lat),
      lng: roundCoordinate(draft.lng),
      hasWater: hasWater === 'YES',
      hasToilet: hasToilet === 'YES',
    };
    if (values.description.trim() !== '') request.description = values.description.trim();
    if (signalLevel !== 'UNKNOWN') request.signalLevel = signalLevel;
    if (groundType !== 'UNKNOWN' && groundType !== 'KEEP') request.groundType = groundType;

    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setSubmitting(true);
    try {
      const response = await createBakji(request, controller.signal);
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
      <h2 className="text-xl font-bold tracking-tight text-ink">박지 제보</h2>

      {nextBasecamp && (
        <Notice tone="info" title="50m 안에 장소가 없어요">
          <p>이 위치를 박지로 먼저 제보해 주세요. 등록되면 이어서 이 박지에서 베이스캠프를 열 수 있어요.</p>
        </Notice>
      )}

      {unverified && (
        <Notice tone="warning" title="이메일 인증을 마치면 박지를 제보할 수 있어요">
          <Link
            to="/verify-email"
            className="inline-flex min-h-11 items-center text-accent underline underline-offset-2"
          >
            이메일 인증하기
          </Link>
        </Notice>
      )}

      {formError && (
        <div role="alert">
          <ErrorNotice title="제보하지 못했어요" error={formError.cause} />
        </div>
      )}

      <BakjiFields
        draft={draft}
        values={values}
        onChange={setValues}
        fieldErrors={fieldErrors}
        groundOptions={GROUND_OPTIONS}
      />

      <Button type="submit" fullWidth loading={submitting} disabled={unverified}>
        제보하기
      </Button>
    </form>
  );
}
