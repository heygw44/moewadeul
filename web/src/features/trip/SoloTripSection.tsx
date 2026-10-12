import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { createSoloTrip } from '../../api/soloTrips';
import { Link, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { Notice } from '../../components/Notice';
import { TextField } from '../../components/TextField';
import { addDaysToLocalDate, todayKst } from '../../lib/datetime';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';

// 서버와 같은 규칙이다. 출발일은 내일부터 60일 안이고, 일정은 1~3박이다. 서버가 다시 검사하므로 여기서는 입력을 돕는 데만 쓴다.
const MAX_DAYS_UNTIL_START = 60;
const MAX_NIGHTS = 3;

const UNVERIFIED_REASON = '이메일 인증을 마치면 등록할 수 있어요';

const SECTION_CLASS = 'mt-4 border-t border-contour pt-4';
const SECTION_TITLE_CLASS = 'text-sm font-semibold text-ink-muted';

type SoloTripSectionProps = {
  spotId: number;
  // 등록한 일정이 예상 인원에 들어가므로, 부모가 장소 상세를 다시 받아 예상 인원을 바꾼다.
  onRegistered: () => void;
};

export function SoloTripSection({ spotId, onRegistered }: SoloTripSectionProps) {
  const [writing, setWriting] = useState(false);
  const [done, setDone] = useState(false);

  return (
    <section className={SECTION_CLASS} aria-labelledby="spot-solo-trip-title">
      <h3 id="spot-solo-trip-title" className={SECTION_TITLE_CLASS}>
        혼자 가는 일정
      </h3>
      <p className="mt-2 text-sm text-ink-muted">
        일정은 나만 봐요. 다른 사람에게는 예상 인원 1명으로만 보여요.
      </p>
      {done && (
        <div role="status" className="mt-2">
          <Notice tone="info" title="일정을 등록했어요">
            <p>
              등록한 일정은{' '}
              <Link to="/me" className="font-semibold text-forest underline underline-offset-2">
                내 정보
              </Link>
              에서 보고 지울 수 있어요.
            </p>
          </Notice>
        </div>
      )}
      <div className="mt-2">
        {writing ? (
          <SoloTripForm
            spotId={spotId}
            onCancel={() => setWriting(false)}
            onRegistered={() => {
              setWriting(false);
              setDone(true);
              onRegistered();
            }}
          />
        ) : (
          <RegisterAction
            onStart={() => {
              setDone(false);
              setWriting(true);
            }}
          />
        )}
      </div>
    </section>
  );
}

function RegisterAction({ onStart }: { onStart: () => void }) {
  const session = useSession();
  const { pathname, search } = useLocation();

  if (session.status === 'loading') return null;
  if (session.status === 'anonymous' || session.me === null) {
    return (
      <Link
        to={withNext('/login', pathname + search)}
        className="inline-flex min-h-11 items-center text-sm font-semibold text-forest underline underline-offset-2"
      >
        로그인하고 일정 등록하기
      </Link>
    );
  }
  const unverified = session.me.status === 'UNVERIFIED';
  return (
    <Button
      variant="secondary"
      disabled={unverified}
      disabledReason={unverified ? UNVERIFIED_REASON : undefined}
      onClick={onStart}
    >
      혼자 가는 일정 등록
    </Button>
  );
}

type TripFieldErrors = Partial<Record<'startDate' | 'endDate', string>>;

function SoloTripForm({
  spotId,
  onCancel,
  onRegistered,
}: {
  spotId: number;
  onCancel: () => void;
  onRegistered: () => void;
}) {
  const today = todayKst();
  const minStart = addDaysToLocalDate(today, 1);
  const maxStart = addDaysToLocalDate(today, MAX_DAYS_UNTIL_START);
  const [startDate, setStartDate] = useState(minStart);
  const [endDate, setEndDate] = useState(addDaysToLocalDate(minStart, 1));
  const [fieldErrors, setFieldErrors] = useState<TripFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function changeStart(value: string) {
    setStartDate(value);
    // 출발일을 옮기면 종료일이 1~3박 범위를 벗어나기 쉬워서, 범위 밖이면 1박 뒤로 맞춘다.
    if (endDate <= value || endDate > addDaysToLocalDate(value, MAX_NIGHTS)) {
      setEndDate(addDaysToLocalDate(value, 1));
    }
  }

  function validate(): TripFieldErrors {
    const errors: TripFieldErrors = {};
    if (!/^\d{4}-\d{2}-\d{2}$/.test(startDate)) errors.startDate = '출발일을 골라 주세요.';
    else if (startDate < minStart || startDate > maxStart) errors.startDate = '출발일은 내일부터 60일 안으로 골라 주세요.';
    if (!/^\d{4}-\d{2}-\d{2}$/.test(endDate)) errors.endDate = '돌아오는 날을 골라 주세요.';
    else if (endDate <= startDate || endDate > addDaysToLocalDate(startDate, MAX_NIGHTS))
      errors.endDate = '일정은 1~3박으로 골라 주세요.';
    return errors;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    setFormError(null);
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      await createSoloTrip({ spotId, startDate, endDate });
      onRegistered();
    } catch (error) {
      setSubmitting(false);
      if (error instanceof ApiError && error.code === 'SOLO_TRIP_OVERLAP') {
        setFieldErrors({ startDate: toUserMessage(error) });
        return;
      }
      setFormError(toUserMessage(error));
    }
  }

  return (
    <form
      noValidate
      onSubmit={handleSubmit}
      aria-label="혼자 가는 일정 등록"
      className="flex flex-col gap-4 rounded-control border border-contour bg-paper p-4"
    >
      {formError && (
        <div role="alert">
          <Notice tone="danger" title="일정을 등록하지 못했어요">
            <p>{formError}</p>
          </Notice>
        </div>
      )}
      <TextField
        label="출발일"
        name="startDate"
        type="date"
        min={minStart}
        max={maxStart}
        value={startDate}
        onChange={(event) => changeStart(event.target.value)}
        error={fieldErrors.startDate}
        hint="내일부터 60일 안으로 고를 수 있어요."
      />
      <TextField
        label="돌아오는 날"
        name="endDate"
        type="date"
        min={addDaysToLocalDate(startDate, 1)}
        max={addDaysToLocalDate(startDate, MAX_NIGHTS)}
        value={endDate}
        onChange={(event) => setEndDate(event.target.value)}
        error={fieldErrors.endDate}
        hint="1박부터 3박까지예요. 돌아오는 날 밤은 예상 인원에 세지 않아요."
      />
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="secondary" onClick={onCancel} disabled={submitting}>
          취소
        </Button>
        <Button type="submit" loading={submitting}>
          등록하기
        </Button>
      </div>
    </form>
  );
}
