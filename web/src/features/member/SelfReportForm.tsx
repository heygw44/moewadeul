import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { updateMe } from '../../api/members';
import type { Me, SelfAgeGroup, SelfGender } from '../../api/types';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { ErrorNotice } from '../../components/ErrorNotice';
import { PageCard } from '../../components/PageLayout';
import { TextField } from '../../components/TextField';
import { AGE_GROUP_LABELS, GENDER_LABELS } from './memberLabels';
import { NICKNAME_REQUIRED } from './requiredMessages';

// 직접 입력한 값을 지우는 선택지. 서버에는 null로 보낸다.
const NOT_SET = 'NOT_SET';
type AgeChoice = SelfAgeGroup | typeof NOT_SET;
type GenderChoice = SelfGender | typeof NOT_SET;

const AGE_OPTIONS: ReadonlyArray<ChoiceOption<AgeChoice>> = [
  ...(Object.keys(AGE_GROUP_LABELS) as SelfAgeGroup[]).map((value) => ({ value, label: AGE_GROUP_LABELS[value] })),
  { value: NOT_SET, label: '밝히지 않음' },
];

const GENDER_OPTIONS: ReadonlyArray<ChoiceOption<GenderChoice>> = [
  ...(Object.keys(GENDER_LABELS) as SelfGender[]).map((value) => ({ value, label: GENDER_LABELS[value] })),
  { value: NOT_SET, label: '밝히지 않음' },
];

const UNVERIFIED_REASON = '이메일 인증을 마치면 고칠 수 있어요';

type FormField = 'nickname' | 'selfAgeGroup' | 'selfGender';
type FormErrors = Partial<Record<FormField, string>>;

function isFormField(field: string): field is FormField {
  return field === 'nickname' || field === 'selfAgeGroup' || field === 'selfGender';
}

// 닉네임과 자기 신고 연령대·성별을 고친다. 서버는 보내지 않은 필드를 그대로 두므로, 바뀐 값만 보낸다.
export function SelfReportForm({ me, onSaved }: { me: Me; onSaved: () => void }) {
  const [nickname, setNickname] = useState(me.nickname);
  const [ageGroup, setAgeGroup] = useState<AgeChoice>(me.selfAgeGroup ?? NOT_SET);
  const [gender, setGender] = useState<GenderChoice>(me.selfGender ?? NOT_SET);
  const [fieldErrors, setFieldErrors] = useState<FormErrors>({});
  const [formError, setFormError] = useState<{ cause: unknown } | null>(null);
  const [saved, setSaved] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const unverified = me.status === 'UNVERIFIED';

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || unverified) return;
    setSaved(false);
    setFormError(null);
    setFieldErrors({});

    const trimmed = nickname.trim();
    if (trimmed === '') {
      setFieldErrors({ nickname: NICKNAME_REQUIRED });
      return;
    }
    const request: { nickname?: string; selfAgeGroup?: SelfAgeGroup | null; selfGender?: SelfGender | null } = {};
    if (trimmed !== me.nickname) request.nickname = trimmed;
    const nextAge = ageGroup === NOT_SET ? null : ageGroup;
    if (nextAge !== me.selfAgeGroup) request.selfAgeGroup = nextAge;
    const nextGender = gender === NOT_SET ? null : gender;
    if (nextGender !== me.selfGender) request.selfGender = nextGender;
    if (Object.keys(request).length === 0) {
      setSaved(true);
      return;
    }

    setSubmitting(true);
    try {
      await updateMe(request);
      setSaved(true);
      onSaved();
    } catch (error) {
      handleError(error);
    } finally {
      setSubmitting(false);
    }
  }

  function handleError(error: unknown) {
    if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const errors: FormErrors = {};
      const others: string[] = [];
      for (const { field, reason } of error.fieldErrors) {
        if (isFormField(field)) errors[field] = reason;
        else others.push(reason);
      }
      setFieldErrors(errors);
      if (others.length > 0) setFormError({ cause: others.join(' ') });
      return;
    }
    if (error instanceof ApiError && error.code === 'MEMBER_NICKNAME_DUPLICATED') {
      setFieldErrors({ nickname: toUserMessage(error) });
      return;
    }
    setFormError({ cause: error });
  }

  return (
    <PageCard title="프로필 정보">
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <p className="text-sm text-ink-muted">
          연령대와 성별은 직접 고르는 정보예요. 다른 회원에게는 '직접 입력'으로 표시돼요. 베이스캠프의 연령·성별 조건은
          본인확인한 정보로만 따져요.
        </p>
        {formError && (
          <div role="alert">
            <ErrorNotice title="저장하지 못했어요" error={formError.cause} />
          </div>
        )}
        <TextField
          label="닉네임"
          name="nickname"
          value={nickname}
          maxLength={20}
          disabled={unverified}
          onChange={(event) => setNickname(event.target.value)}
          error={fieldErrors.nickname}
          hint="2~20자로 정해 주세요. 앞뒤 공백이나 두 칸 이어진 공백은 쓸 수 없어요."
        />
        <ChoiceGroup
          legend="연령대"
          name="selfAgeGroup"
          options={AGE_OPTIONS}
          value={ageGroup}
          onChange={setAgeGroup}
          disabled={unverified}
          error={fieldErrors.selfAgeGroup}
        />
        <ChoiceGroup
          legend="성별"
          name="selfGender"
          options={GENDER_OPTIONS}
          value={gender}
          onChange={setGender}
          disabled={unverified}
          error={fieldErrors.selfGender}
        />
        {saved && (
          <p role="status" className="text-sm text-accent-deep">
            저장했어요.
          </p>
        )}
        <div className="flex justify-end">
          <Button
            type="submit"
            loading={submitting}
            disabled={unverified}
            disabledReason={unverified ? UNVERIFIED_REASON : undefined}
          >
            저장
          </Button>
        </div>
      </form>
    </PageCard>
  );
}
