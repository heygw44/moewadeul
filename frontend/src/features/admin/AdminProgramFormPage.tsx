import { useState } from 'react';
import type { ChangeEvent, FormEvent, ReactNode } from 'react';
import { createProgram, reviseProgram } from '../../api/admin';
import { ApiError, serverNow } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { Link, navigate } from '../../app/router';
import { Button } from '../../components/Button';
import { buttonClassName } from '../../components/buttonClass';
import { Checkbox } from '../../components/Checkbox';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { TextArea } from '../../components/TextArea';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { AdminPage } from './AdminGate';
import {
  emptyProgramForm,
  formValuesOf,
  LIST_PATH,
  savedListPath,
  serverErrorsToFields,
  TEXT_MAX_LENGTH,
  toCreateRequest,
  toRevision,
  validate,
} from './adminProgramForm';
import type { ProgramFormErrors, ProgramFormMode, ProgramFormValues } from './adminProgramForm';
import { useProgramDetail } from './useProgramDetail';

const FIELD_ORDER: ReadonlyArray<keyof ProgramFormValues> = [
  'title',
  'description',
  'locationText',
  'spotId',
  'applyOpenAt',
  'applyCloseAt',
  'startAt',
  'endAt',
  'capacity',
  'fee',
  'paymentDeadlineMinutes',
  'overnight',
];

type TextValueField = Exclude<keyof ProgramFormValues, 'overnight'>;

const PAGE_TITLES: Record<ProgramFormMode, string> = { create: '행사 등록', edit: '행사 수정' };

type AdminProgramFormPageProps = { mode: 'create' } | { mode: 'edit'; programId: number };

export function AdminProgramFormPage(props: AdminProgramFormPageProps) {
  return (
    <AdminPage title={PAGE_TITLES[props.mode]} current={LIST_PATH}>
      {props.mode === 'create' ? (
        <ProgramForm mode="create" initial={emptyProgramForm()} />
      ) : (
        <EditLoader programId={props.programId} />
      )}
    </AdminPage>
  );
}

function ListLink() {
  return (
    <Link to={LIST_PATH} className={buttonClassName('secondary')}>
      행사 목록
    </Link>
  );
}

function EditLoader({ programId }: { programId: number }) {
  const { load, reload } = useProgramDetail(programId);
  const showSkeleton = useDelayedFlag(load.status === 'loading');

  if (load.status === 'loading') return showSkeleton ? <Skeleton className="h-60 w-full" /> : null;

  if (load.status === 'error') {
    return (
      <div role="alert" className="flex flex-col gap-3">
        <ErrorNotice title="행사를 불러오지 못했어요" error={load.error} />
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={reload}>
            다시 불러오기
          </Button>
          <ListLink />
        </div>
      </div>
    );
  }

  if (load.program.status === 'CANCELED') {
    return (
      <EmptyState
        framed
        title="취소한 행사는 고칠 수 없어요"
        description="이미 취소된 행사라서 내용을 바꿀 수 없어요."
        action={<ListLink />}
      />
    );
  }

  return <ProgramForm mode="edit" programId={programId} initial={formValuesOf(load.program)} />;
}

type ProgramFormProps = { initial: ProgramFormValues } & ({ mode: 'create' } | { mode: 'edit'; programId: number });

type SubmitFailure = { message: string; error: unknown };

function ProgramForm(props: ProgramFormProps) {
  const [original] = useState(props.initial);
  const [values, setValues] = useState(props.initial);
  const [errors, setErrors] = useState<ProgramFormErrors>({});
  const [failure, setFailure] = useState<SubmitFailure | null>(null);
  const [unchanged, setUnchanged] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const isCreate = props.mode === 'create';

  function change<K extends keyof ProgramFormValues>(key: K, value: ProgramFormValues[K]) {
    setValues((previous) => ({ ...previous, [key]: value }));
    setErrors((previous) => (previous[key] ? { ...previous, [key]: undefined } : previous));
    setUnchanged(false);
  }

  // disabled 입력란은 포커스를 받지 못해서, 서버 오류 뒤 오류 칸으로 포커스를 옮길 수 있게 읽기 전용으로 잠근다.
  function textProps(name: TextValueField) {
    return {
      name,
      value: values[name],
      error: errors[name],
      readOnly: submitting,
      onChange: (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => change(name, event.target.value),
    };
  }

  function focusFirstError(form: HTMLFormElement, found: ProgramFormErrors) {
    const first = FIELD_ORDER.find((field) => found[field]);
    const element = first === undefined ? null : form.elements.namedItem(first);
    if (element instanceof HTMLElement) element.focus();
  }

  function showServerError(form: HTMLFormElement, caught: unknown) {
    if (!(caught instanceof ApiError)) {
      setFailure({ message: toUserMessage(caught), error: caught });
      return;
    }
    const { fields, rest } = serverErrorsToFields(caught);
    setErrors(fields);
    if (rest.length > 0) setFailure({ message: rest.join(' '), error: caught });
    focusFirstError(form, fields);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const form = event.currentTarget;
    setFailure(null);
    setUnchanged(false);
    const found = validate(values, new Date(serverNow()), props.mode);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      focusFirstError(form, found);
      return;
    }
    setSubmitting(true);
    try {
      if (props.mode === 'create') {
        await createProgram(toCreateRequest(values));
        navigate(savedListPath('created'), { replace: true });
        return;
      }
      const revision = toRevision(original, values);
      if (Object.keys(revision).length === 0) {
        setUnchanged(true);
        setSubmitting(false);
        return;
      }
      await reviseProgram(props.programId, revision);
      navigate(savedListPath('revised'), { replace: true });
    } catch (caught) {
      showServerError(form, caught);
      setSubmitting(false);
    }
  }

  return (
    <form
      noValidate
      onSubmit={(event) => void submit(event)}
      aria-label={PAGE_TITLES[props.mode]}
      className="divide-y-4 divide-canvas overflow-hidden rounded-sheet bg-fill"
    >
      <Section title="기본 정보">
        <TextField
          label="제목"
          maxLength={TEXT_MAX_LENGTH.title}
          hint="100자까지 적을 수 있어요."
          {...textProps('title')}
        />
        <TextArea label="설명" rows={8} maxLength={TEXT_MAX_LENGTH.description} {...textProps('description')} />
        <TextField
          label="장소 설명"
          maxLength={TEXT_MAX_LENGTH.locationText}
          hint="참가자에게 보여 줄 장소 안내예요. 255자까지 적을 수 있어요."
          {...textProps('locationText')}
        />
        <TextField
          label="지도 장소 번호 (선택)"
          inputMode="numeric"
          autoComplete="off"
          hint="지도에서 장소를 열면 주소 끝에 있는 숫자예요. 비워 두면 연결하지 않아요."
          {...textProps('spotId')}
        />
      </Section>

      <Section title="일정" note="시각은 모두 한국 시간이에요.">
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="신청 시작" type="datetime-local" {...textProps('applyOpenAt')} />
          <TextField label="신청 마감" type="datetime-local" {...textProps('applyCloseAt')} />
          <TextField label="행사 시작" type="datetime-local" {...textProps('startAt')} />
          <TextField label="행사 종료" type="datetime-local" {...textProps('endAt')} />
        </div>
      </Section>

      <Section title="정원과 참가비">
        <TextField
          label="정원 (명)"
          inputMode="numeric"
          autoComplete="off"
          hint={isCreate ? undefined : '신청이 시작된 뒤에는 정원을 줄일 수 없어요. 늘리는 건 괜찮아요.'}
          {...textProps('capacity')}
        />
        <TextField
          label="참가비 (원)"
          inputMode="numeric"
          autoComplete="off"
          hint="0원이면 무료예요. 100만 원까지 적을 수 있어요."
          {...textProps('fee')}
        />
        <TextField
          label="결제 기한 (분)"
          inputMode="numeric"
          autoComplete="off"
          hint={
            isCreate
              ? '신청한 뒤 결제할 수 있는 시간이에요. 1~1440분이고, 비우면 15분이에요.'
              : '신청한 뒤 결제할 수 있는 시간이에요. 1~1440분 사이로 적어 주세요.'
          }
          {...textProps('paymentDeadlineMinutes')}
        />
        <Checkbox
          label="숙박 행사예요"
          name="overnight"
          checked={values.overnight}
          disabled={submitting}
          hint="숙박 행사는 본인확인을 마친 회원만 신청할 수 있어요."
          onChange={(event) => change('overnight', event.target.checked)}
        />
      </Section>

      {(failure !== null || unchanged) && (
        <div className="flex flex-col gap-3 px-5 py-5">
          {failure !== null && (
            <div role="alert">
              <ErrorNotice
                title={isCreate ? '행사를 등록하지 못했어요' : '행사를 고치지 못했어요'}
                error={failure.error}
                message={failure.message}
              />
            </div>
          )}
          {unchanged && (
            <div role="status">
              <Notice tone="info" title="바뀐 내용이 없어요">
                <p>고친 곳이 있는지 확인해 주세요.</p>
              </Notice>
            </div>
          )}
        </div>
      )}

      <div className="sticky bottom-(--bottom-nav-h) flex flex-wrap items-start justify-end gap-2 bg-surface px-5 py-4 lg:static">
        <Link to={LIST_PATH} className={buttonClassName('secondary')}>
          목록으로
        </Link>
        <Button type="submit" loading={submitting}>
          {isCreate ? '등록하기' : '저장하기'}
        </Button>
      </div>
    </form>
  );
}

function Section({ title, note, children }: { title: string; note?: string; children: ReactNode }) {
  return (
    <fieldset className="flex min-w-0 flex-col gap-3 px-5 py-5">
      <legend className="mb-3 p-0 text-[1.0625rem] font-extrabold text-ink">{title}</legend>
      {note && <p className="text-sm text-ink-muted">{note}</p>}
      {children}
    </fieldset>
  );
}
