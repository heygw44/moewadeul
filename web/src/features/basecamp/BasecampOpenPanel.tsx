import { useEffect, useRef, useState } from 'react';
import { buttonClassName } from '../../components/buttonClass';
import type { FormEvent, ReactNode } from 'react';
import { openBasecamp } from '../../api/basecamps';
import { ApiError } from '../../api/client';
import { serverTraceId, toUserMessage } from '../../api/errors';
import { createIdempotentRunner } from '../../api/idempotency';
import { fetchSpotDetail } from '../../api/spots';
import type { BasecampOpenRequest, JoinCondition, SpotDetail } from '../../api/types';
import { Link, navigate } from '../../app/router';
import { Button } from '../../components/Button';
import { Checkbox } from '../../components/Checkbox';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { EmptyState } from '../../components/EmptyState';
import { Icon } from '../../components/icons';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Notice } from '../../components/Notice';
import { SelectField } from '../../components/SelectField';
import { Skeleton } from '../../components/Skeleton';
import { TextArea } from '../../components/TextArea';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { addDaysToLocalDate, todayKst } from '../../lib/datetime';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { ParkWarningNotice } from '../spot/ParkWarningNotice';
import { AGE_GROUP_STEPS, ageGroupStepLabel, OFF_PLATFORM_PAYMENT_REMINDER } from './basecampLabels';

type BasecampOpenPanelProps = {
  spotId: number;
  onBack: () => void;
};

type SpotResult = { spotId: number; detail: SpotDetail } | { spotId: number; error: unknown };

type FormField =
  'title' | 'description' | 'startDate' | 'endDate' | 'capacity' | 'minTrustLevel' | 'ageGroup' | 'sameGenderOnly';

type FieldErrors = Partial<Record<FormField, string>>;

type TrustChoice = 'NONE' | '1' | '2';

const TITLE_MAX = 100;
const DESCRIPTION_MAX = 2000;

const MAX_DAYS_AHEAD = 60;
const MIN_NIGHTS = 1;
const MAX_NIGHTS = 3;

const CAPACITY_OPTIONS: ChoiceOption<string>[] = [2, 3, 4, 5, 6].map((value) => ({
  value: String(value),
  label: `${value}명`,
}));

const TRUST_OPTIONS: ChoiceOption<TrustChoice>[] = [
  { value: 'NONE', label: '없음' },
  { value: '1', label: '신뢰 단계 1 이상' },
  { value: '2', label: '신뢰 단계 2 이상' },
];

const AGE_OPTIONS = [
  { value: '', label: '제한 없음' },
  ...AGE_GROUP_STEPS.map((step) => ({ value: String(step), label: ageGroupStepLabel(step) })),
];

const SERVER_FIELD_MAP: Record<string, FormField> = {
  title: 'title',
  description: 'description',
  startDate: 'startDate',
  endDate: 'endDate',
  capacity: 'capacity',
  'joinCondition.minTrustLevel': 'minTrustLevel',
  'joinCondition.ageGroupMin': 'ageGroup',
  'joinCondition.ageGroupMax': 'ageGroup',
  'joinCondition.sameGenderOnly': 'sameGenderOnly',
};

const PRIMARY_LINK_CLASS = buttonClassName('primary');

function mergeReasons(reasons: string[]): string {
  return [...new Set(reasons)].join(' ');
}

export function BasecampOpenPanel({ spotId, onBack }: BasecampOpenPanelProps) {
  const session = useSession();
  const showSessionSkeleton = useDelayedFlag(session.status === 'loading');
  const [result, setResult] = useState<SpotResult | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchSpotDetail(spotId, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setResult({ spotId, detail });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ spotId, error });
      },
    );
    return () => controller.abort();
  }, [spotId]);

  const current = result && result.spotId === spotId ? result : null;
  const spotLoading = current === null && session.status !== 'loading';
  const showSpotSkeleton = useDelayedFlag(spotLoading);

  let body: ReactNode;
  if (session.status === 'loading' || current === null) {
    body = showSessionSkeleton || showSpotSkeleton ? <FormSkeleton /> : null;
  } else if ('error' in current) {
    body =
      current.error instanceof ApiError && current.error.code === 'NOT_FOUND' ? (
        <EmptyState title="장소를 찾을 수 없어요" description="삭제됐거나 숨겨진 장소예요." />
      ) : (
        <ErrorNotice title="장소 정보를 불러오지 못했어요" error={current.error} />
      );
  } else if (current.detail.parkWarning.warned) {
    body = (
      <div className="flex flex-col gap-4">
        <h2 className="text-xl font-bold tracking-tight text-ink">{current.detail.name}에서 베이스캠프 열기</h2>
        <ParkWarningNotice warning={current.detail.parkWarning} />
        <p className="text-base font-semibold text-danger">
          이 박지에서는 베이스캠프를 열 수 없어요. 다른 장소를 골라 주세요.
        </p>
      </div>
    );
  } else if (session.status === 'anonymous' || session.me === null) {
    body = (
      <EmptyState
        title="로그인이 필요해요"
        description="베이스캠프는 본인확인을 마친 회원만 열 수 있어요."
        action={
          <Link to={withNext('/login', `/basecamps/new?spotId=${spotId}`)} className={PRIMARY_LINK_CLASS}>
            로그인
          </Link>
        }
      />
    );
  } else if (session.me.status === 'UNVERIFIED') {
    body = (
      <Notice tone="warning" title="이메일 인증을 마치면 베이스캠프를 열 수 있어요">
        <Link to="/verify-email" className="inline-flex min-h-11 items-center text-accent underline underline-offset-2">
          이메일 인증하기
        </Link>
      </Notice>
    );
  } else if (session.me.trustLevel < 1) {
    body = (
      <Notice tone="warning" title="본인확인이 필요해요">
        <p>베이스캠프는 본인확인을 마친 성인만 열 수 있어요.</p>
        <Link
          to="/identity-verification"
          className="inline-flex min-h-11 items-center text-accent underline underline-offset-2"
        >
          본인확인하기
        </Link>
      </Notice>
    );
  } else {
    body = <OpenForm spotId={spotId} spotName={current.detail.name} />;
  }

  return (
    <div className="flex flex-col">
      <div className="sticky top-0 z-10 flex items-center bg-surface px-2 lg:px-4">
        <button
          type="button"
          aria-label="장소로 돌아가기"
          onClick={onBack}
          className="flex size-11 items-center justify-center rounded-control text-ink hover:bg-fill"
        >
          <Icon name="chevronLeft" size={24} />
        </button>
      </div>
      <div aria-busy={body === null} className="px-4 py-4">
        {body}
      </div>
    </div>
  );
}

function FormSkeleton() {
  return (
    <div className="flex flex-col gap-3">
      <Skeleton className="h-7 w-1/2" />
      <Skeleton className="h-11 w-full" />
      <Skeleton className="h-24 w-full" />
    </div>
  );
}

function OpenForm({ spotId, spotName }: { spotId: number; spotName: string }) {
  const today = todayKst();
  const minStart = addDaysToLocalDate(today, 1);
  const maxStart = addDaysToLocalDate(today, MAX_DAYS_AHEAD);

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [capacity, setCapacity] = useState<string | null>(null);
  const [minTrust, setMinTrust] = useState<TrustChoice>('NONE');
  const [ageMin, setAgeMin] = useState('');
  const [ageMax, setAgeMax] = useState('');
  const [sameGenderOnly, setSameGenderOnly] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<{ message: string; traceId?: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [idempotentRunner] = useState(createIdempotentRunner);
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => () => controllerRef.current?.abort(), []);

  const endMin = startDate === '' ? undefined : addDaysToLocalDate(startDate, MIN_NIGHTS);
  const endMax = startDate === '' ? undefined : addDaysToLocalDate(startDate, MAX_NIGHTS);

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (title.trim() === '') errors.title = '제목을 적어 주세요.';
    if (description.trim() === '') errors.description = '설명을 적어 주세요.';
    if (startDate === '') errors.startDate = '출발일을 골라 주세요.';
    else if (startDate < minStart || startDate > maxStart)
      errors.startDate = '출발일은 내일부터 60일 안에서 골라 주세요.';
    if (endDate === '') errors.endDate = '돌아오는 날을 골라 주세요.';
    else if (endMin !== undefined && endMax !== undefined && (endDate < endMin || endDate > endMax)) {
      errors.endDate = '돌아오는 날은 출발일에서 1~3박 뒤로 골라 주세요.';
    }
    if (capacity === null) errors.capacity = '정원을 골라 주세요.';
    if ((ageMin === '') !== (ageMax === '')) {
      errors.ageGroup = '연령대는 시작과 끝을 모두 골라 주세요.';
    } else if (ageMin !== '' && Number(ageMin) > Number(ageMax)) {
      errors.ageGroup = '시작 연령대가 끝 연령대보다 높아요. 다시 골라 주세요.';
    }
    return errors;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const errors = validate();
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0 || capacity === null) return;

    const hasCondition = minTrust !== 'NONE' || ageMin !== '' || sameGenderOnly;
    const joinCondition: JoinCondition = {
      minTrustLevel: minTrust === 'NONE' ? null : Number(minTrust),
      ageGroupMin: ageMin === '' ? null : Number(ageMin),
      ageGroupMax: ageMax === '' ? null : Number(ageMax),
      sameGenderOnly,
    };
    const request: BasecampOpenRequest = {
      spotId,
      title: title.trim(),
      description: description.trim(),
      startDate,
      endDate,
      capacity: Number(capacity),
    };
    if (hasCondition) request.joinCondition = joinCondition;

    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setSubmitting(true);
    try {
      const response = await idempotentRunner.run(request, (key) => openBasecamp(request, key, controller.signal));
      if (controller.signal.aborted) return;
      navigate(`/basecamps/${response.basecampId}`);
    } catch (error) {
      if (controller.signal.aborted) return;
      setSubmitting(false);
      handleError(error);
    }
  }

  function handleError(error: unknown) {
    if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const reasons: Partial<Record<FormField, string[]>> = {};
      const others: string[] = [];
      for (const { field, reason } of error.fieldErrors) {
        const target = SERVER_FIELD_MAP[field];
        if (target) (reasons[target] ??= []).push(reason);
        else others.push(reason);
      }
      const errors: FieldErrors = {};
      for (const [field, list] of Object.entries(reasons)) errors[field as FormField] = mergeReasons(list);
      setFieldErrors(errors);
      if (others.length > 0) setFormError({ message: others.join(' ') });
      return;
    }
    if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.serverMessage !== '') {
      setFormError({ message: error.serverMessage });
      return;
    }
    setFormError({ message: toUserMessage(error), traceId: serverTraceId(error) });
  }

  return (
    <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h2 className="text-xl font-bold tracking-tight text-ink">베이스캠프 열기</h2>
        <p className="text-sm text-ink-muted">장소: {spotName}</p>
        <p className="text-sm text-ink-muted">{OFF_PLATFORM_PAYMENT_REMINDER}</p>
      </div>

      {formError && (
        <div role="alert">
          <ErrorNotice title="베이스캠프를 열지 못했어요" error={formError.message} traceId={formError.traceId} />
        </div>
      )}

      <TextField
        label="제목"
        name="title"
        maxLength={TITLE_MAX}
        value={title}
        onChange={(event) => setTitle(event.target.value)}
        error={fieldErrors.title}
      />
      <TextArea
        label="설명"
        name="description"
        maxLength={DESCRIPTION_MAX}
        value={description}
        onChange={(event) => setDescription(event.target.value)}
        error={fieldErrors.description}
      />
      <div className="grid grid-cols-2 gap-2">
        <TextField
          label="출발일"
          name="startDate"
          type="date"
          min={minStart}
          max={maxStart}
          value={startDate}
          onChange={(event) => setStartDate(event.target.value)}
          error={fieldErrors.startDate}
        />
        <TextField
          label="돌아오는 날"
          name="endDate"
          type="date"
          min={endMin}
          max={endMax}
          value={endDate}
          onChange={(event) => setEndDate(event.target.value)}
          error={fieldErrors.endDate}
        />
      </div>
      <p className="-mt-2 text-sm text-ink-muted">출발일은 내일부터 60일 안에서 골라요. 일정은 1~3박이에요.</p>
      <ChoiceGroup
        legend="정원 (캠프 리더 포함)"
        name="capacity"
        options={CAPACITY_OPTIONS}
        value={capacity}
        onChange={setCapacity}
        error={fieldErrors.capacity}
      />

      <fieldset className="flex min-w-0 flex-col gap-3 rounded-sheet bg-fill p-4">
        <legend className="px-1 text-sm font-medium text-ink">합류 조건 (선택)</legend>
        <ChoiceGroup
          legend="최소 신뢰 단계"
          name="minTrustLevel"
          options={TRUST_OPTIONS}
          value={minTrust}
          onChange={setMinTrust}
          hint="신뢰 단계를 '없음'으로 둬도 합류하려면 본인확인이 필요해요."
          error={fieldErrors.minTrustLevel}
        />
        <div className="grid grid-cols-2 gap-2">
          <SelectField
            label="연령대 시작"
            name="ageGroupMin"
            options={AGE_OPTIONS}
            value={ageMin}
            onChange={(event) => setAgeMin(event.target.value)}
            error={fieldErrors.ageGroup}
          />
          <SelectField
            label="연령대 끝"
            name="ageGroupMax"
            options={AGE_OPTIONS}
            value={ageMax}
            onChange={(event) => setAgeMax(event.target.value)}
          />
        </div>
        <Checkbox
          label="같은 성별만 받기"
          name="sameGenderOnly"
          checked={sameGenderOnly}
          onChange={(event) => setSameGenderOnly(event.target.checked)}
          hint="연령대와 성별은 본인확인한 정보로만 따져요."
          error={fieldErrors.sameGenderOnly}
        />
      </fieldset>

      <Button type="submit" fullWidth loading={submitting}>
        베이스캠프 열기
      </Button>
    </form>
  );
}
