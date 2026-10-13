import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { changePassword } from '../../api/members';
import { Button } from '../../components/Button';
import { Notice } from '../../components/Notice';
import { PageCard } from '../../components/PageLayout';
import { PasswordField } from '../../components/PasswordField';
import {
  CURRENT_PASSWORD_REQUIRED,
  NEW_PASSWORD_REQUIRED,
  PASSWORD_CONFIRM_MISMATCH,
  PASSWORD_CONFIRM_REQUIRED,
} from './requiredMessages';
import { useRetryCountdown } from './useRetryCountdown';

type ChangeField = 'currentPassword' | 'newPassword' | 'newPasswordConfirm';
type FormErrors = Partial<Record<ChangeField, string>>;

// 서버 오류 코드를 어느 입력란 아래에 보여 줄지 정한다. 표에 없는 코드는 폼 위의 안내로 보여 준다.
const FIELD_BY_CODE: Record<string, ChangeField> = {
  MEMBER_CURRENT_PASSWORD_MISMATCH: 'currentPassword',
  MEMBER_PASSWORD_POLICY: 'newPassword',
  MEMBER_PASSWORD_UNCHANGED: 'newPassword',
};

function isChangeField(field: string): field is ChangeField {
  return field === 'currentPassword' || field === 'newPassword' || field === 'newPasswordConfirm';
}

// 현재 비밀번호를 확인하고 새 비밀번호로 바꾼다. 비밀번호 규칙은 서버가 검사하고, 화면은 빈 칸과 확인 값만 미리 본다.
export function PasswordChangeForm() {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FormErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [changed, setChanged] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const lock = useRetryCountdown();

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || lock.secondsLeft > 0) return;
    setChanged(false);
    setFormError(null);

    const emptyErrors: FormErrors = {};
    if (currentPassword === '') emptyErrors.currentPassword = CURRENT_PASSWORD_REQUIRED;
    if (newPassword === '') emptyErrors.newPassword = NEW_PASSWORD_REQUIRED;
    if (newPasswordConfirm === '') emptyErrors.newPasswordConfirm = PASSWORD_CONFIRM_REQUIRED;
    else if (newPasswordConfirm !== newPassword) emptyErrors.newPasswordConfirm = PASSWORD_CONFIRM_MISMATCH;
    setFieldErrors(emptyErrors);
    if (Object.keys(emptyErrors).length > 0) return;

    setSubmitting(true);
    try {
      await changePassword({ currentPassword, newPassword, newPasswordConfirm });
      setCurrentPassword('');
      setNewPassword('');
      setNewPasswordConfirm('');
      setChanged(true);
    } catch (error) {
      handleError(error);
    } finally {
      setSubmitting(false);
    }
  }

  function handleError(error: unknown) {
    if (!(error instanceof ApiError)) {
      setFormError(toUserMessage(error));
      return;
    }
    if (error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const errors: FormErrors = {};
      const others: string[] = [];
      for (const { field, reason } of error.fieldErrors) {
        if (isChangeField(field)) errors[field] = reason;
        else others.push(reason);
      }
      setFieldErrors(errors);
      if (others.length > 0) setFormError(others.join(' '));
      return;
    }
    const field = FIELD_BY_CODE[error.code];
    if (field) {
      setFieldErrors({ [field]: toUserMessage(error) });
      return;
    }
    if (error.code === 'LOGIN_LOCKED' && error.retryAfterSeconds !== undefined) {
      lock.start(error.retryAfterSeconds);
    }
    setFormError(toUserMessage(error));
  }

  function handleConfirmBlur() {
    if (newPasswordConfirm !== '' && newPasswordConfirm !== newPassword) {
      setFieldErrors((current) => ({ ...current, newPasswordConfirm: PASSWORD_CONFIRM_MISMATCH }));
    }
  }

  function handleConfirmChange(value: string) {
    setNewPasswordConfirm(value);
    if (fieldErrors.newPasswordConfirm && value === newPassword) {
      setFieldErrors((current) => ({ ...current, newPasswordConfirm: undefined }));
    }
  }

  return (
    <PageCard title="비밀번호 변경">
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <p className="text-sm text-ink-muted">비밀번호를 바꾸면 이 기기 말고 다른 기기에서는 모두 로그아웃돼요.</p>
        {formError && (
          <div role="alert">
            <Notice tone="danger" title="비밀번호를 바꾸지 못했어요">
              <p>{formError}</p>
            </Notice>
          </div>
        )}
        <PasswordField
          label="현재 비밀번호"
          name="currentPassword"
          autoComplete="current-password"
          value={currentPassword}
          onChange={(event) => setCurrentPassword(event.target.value)}
          error={fieldErrors.currentPassword}
        />
        <PasswordField
          label="새 비밀번호"
          name="newPassword"
          autoComplete="new-password"
          value={newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
          error={fieldErrors.newPassword}
          hint="10~64자로, 영문·숫자·특수문자를 하나 이상씩 넣어 주세요. 공백은 쓸 수 없어요."
        />
        <PasswordField
          label="새 비밀번호 확인"
          name="newPasswordConfirm"
          autoComplete="new-password"
          value={newPasswordConfirm}
          onChange={(event) => handleConfirmChange(event.target.value)}
          onBlur={handleConfirmBlur}
          error={fieldErrors.newPasswordConfirm}
        />
        {changed && (
          <p role="status" className="text-sm text-accent-deep">
            비밀번호를 바꿨어요. 다른 기기에서는 새 비밀번호로 다시 로그인해 주세요.
          </p>
        )}
        <div className="flex justify-end">
          <Button
            type="submit"
            loading={submitting}
            disabled={lock.secondsLeft > 0}
            disabledReason={lock.secondsLeft > 0 ? `${lock.secondsLeft}초 뒤에 다시 시도할 수 있어요` : undefined}
          >
            비밀번호 바꾸기
          </Button>
        </div>
      </form>
    </PageCard>
  );
}
