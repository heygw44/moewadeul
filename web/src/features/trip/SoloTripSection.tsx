import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { createIdempotentRunner } from '../../api/idempotency';
import { createSoloTrip } from '../../api/soloTrips';
import { Link } from '../../app/router';
import { Button } from '../../components/Button';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Notice } from '../../components/Notice';
import { TextField } from '../../components/TextField';
import { addDaysToLocalDate, todayKst } from '../../lib/datetime';
import { GatedButton } from '../member/GatedButton';

const MAX_DAYS_UNTIL_START = 60;
const MAX_NIGHTS = 3;

const SECTION_CLASS = 'mt-8';
const SECTION_TITLE_CLASS = 'text-[1.1875rem] font-extrabold tracking-[-0.02em] text-ink';

type SoloTripSectionProps = {
  spotId: number;
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
      <p className="mt-2 text-sm text-ink-muted">나만 보는 일정이에요. 다른 사람에게는 예상 인원 1명으로만 보여요.</p>
      {done && (
        <div role="status" className="mt-2">
          <Notice tone="info" title="일정을 등록했어요">
            <p>
              등록한 일정은{' '}
              <Link to="/me" className="font-semibold text-accent underline underline-offset-2">
                내 정보
              </Link>
              에서 보고 삭제할 수 있어요.
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
  return (
    <GatedButton variant="secondary" onClick={onStart}>
      혼자 가는 일정 등록
    </GatedButton>
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
  const [formError, setFormError] = useState<{ cause: unknown } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [idempotentRunner] = useState(createIdempotentRunner);

  function changeStart(value: string) {
    setStartDate(value);
    if (endDate <= value || endDate > addDaysToLocalDate(value, MAX_NIGHTS)) {
      setEndDate(addDaysToLocalDate(value, 1));
    }
  }

  function validate(): TripFieldErrors {
    const errors: TripFieldErrors = {};
    if (!/^\d{4}-\d{2}-\d{2}$/.test(startDate)) errors.startDate = '출발일을 골라 주세요.';
    else if (startDate < minStart || startDate > maxStart)
      errors.startDate = '출발일은 내일부터 60일 안에서 골라 주세요.';
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
      const request = { spotId, startDate, endDate };
      await idempotentRunner.run(request, (key) => createSoloTrip(request, key));
      onRegistered();
    } catch (error) {
      setSubmitting(false);
      if (error instanceof ApiError && error.code === 'SOLO_TRIP_OVERLAP') {
        setFieldErrors({ startDate: toUserMessage(error) });
        return;
      }
      setFormError({ cause: error });
    }
  }

  return (
    <form
      noValidate
      onSubmit={handleSubmit}
      aria-label="혼자 가는 일정 등록"
      className="flex flex-col gap-4 rounded-sheet bg-fill p-5"
    >
      {formError && (
        <div role="alert">
          <ErrorNotice title="일정을 등록하지 못했어요" error={formError.cause} />
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
        hint="내일부터 60일 안에서 골라요."
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
        hint="1~3박까지예요. 돌아오는 날 밤은 예상 인원에 세지 않아요."
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
