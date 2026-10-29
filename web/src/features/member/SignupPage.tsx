import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { login, signUp } from '../../api/auth';
import { ApiError } from '../../api/client';
import { serverTraceId, toUserMessage } from '../../api/errors';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { Checkbox } from '../../components/Checkbox';
import { EmailField } from '../../components/EmailField';
import { composeEmail, EMPTY_EMAIL } from '../../components/emailValue';
import type { EmailValue } from '../../components/emailValue';
import { ErrorNotice } from '../../components/ErrorNotice';
import { PasswordField } from '../../components/PasswordField';
import { TextField } from '../../components/TextField';
import { AuthLayout } from './AuthLayout';
import { safeNextPath, withNext } from './nextPath';
import {
  AGE_CONFIRM_REQUIRED,
  emailRequiredMessage,
  NICKNAME_REQUIRED,
  PASSWORD_CONFIRM_MISMATCH,
  PASSWORD_CONFIRM_REQUIRED,
  PASSWORD_REQUIRED,
} from './requiredMessages';
import { useSession } from './session';

type SignupField = 'email' | 'password' | 'passwordConfirm' | 'nickname' | 'ageConfirmed';

const FOOTER_LINK_CLASS =
  'inline-flex min-h-11 items-center font-semibold text-accent underline underline-offset-4 hover:text-accent-strong';

const NOTICE_LINK_CLASS = 'font-semibold text-accent underline underline-offset-4 hover:text-accent-strong';

const FIELD_BY_CODE: Record<string, SignupField> = {
  MEMBER_EMAIL_DUPLICATED: 'email',
  MEMBER_DISPOSABLE_EMAIL: 'email',
  MEMBER_PASSWORD_POLICY: 'password',
  MEMBER_NICKNAME_DUPLICATED: 'nickname',
};

function isSignupField(field: string): field is SignupField {
  return (
    field === 'email' ||
    field === 'password' ||
    field === 'passwordConfirm' ||
    field === 'nickname' ||
    field === 'ageConfirmed'
  );
}

export function SignupPage() {
  const { search } = useLocation();
  const next = safeNextPath(search);
  const session = useSession();

  const [email, setEmail] = useState<EmailValue>(EMPTY_EMAIL);
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [nickname, setNickname] = useState('');
  const [ageConfirmed, setAgeConfirmed] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<SignupField, string>>>({});
  const [formError, setFormError] = useState<{ message: string; traceId?: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    document.title = '회원가입 · 뫼와들';
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const emptyErrors: Partial<Record<SignupField, string>> = {};
    const emailMissing = emailRequiredMessage(email);
    if (emailMissing) emptyErrors.email = emailMissing;
    if (password === '') emptyErrors.password = PASSWORD_REQUIRED;
    if (passwordConfirm === '') emptyErrors.passwordConfirm = PASSWORD_CONFIRM_REQUIRED;
    else if (passwordConfirm !== password) emptyErrors.passwordConfirm = PASSWORD_CONFIRM_MISMATCH;
    if (nickname === '') emptyErrors.nickname = NICKNAME_REQUIRED;
    if (!ageConfirmed) emptyErrors.ageConfirmed = AGE_CONFIRM_REQUIRED;
    setFieldErrors(emptyErrors);
    setFormError(null);
    if (Object.keys(emptyErrors).length > 0) return;

    setSubmitting(true);
    const credentials = { email: composeEmail(email), password };
    try {
      await signUp({ ...credentials, passwordConfirm, nickname, ageConfirmed });
    } catch (error) {
      setSubmitting(false);
      handleError(error);
      return;
    }

    try {
      await login(credentials);
      await session.refresh();
      navigate(withNext('/verify-email', next), { replace: true });
    } catch {
      navigate(withNext('/login', next), { replace: true });
    }
  }

  function handlePasswordConfirmBlur() {
    if (passwordConfirm !== '' && passwordConfirm !== password) {
      setFieldErrors((current) => ({ ...current, passwordConfirm: PASSWORD_CONFIRM_MISMATCH }));
    }
  }

  function handlePasswordConfirmChange(value: string) {
    setPasswordConfirm(value);
    if (fieldErrors.passwordConfirm && value === password) {
      setFieldErrors((current) => ({ ...current, passwordConfirm: undefined }));
    }
  }

  function handleError(error: unknown) {
    if (!(error instanceof ApiError)) {
      setFormError({ message: toUserMessage(error) });
      return;
    }
    const field = FIELD_BY_CODE[error.code];
    if (field) {
      setFieldErrors({ [field]: error.message });
      return;
    }
    if (error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const errors: Partial<Record<SignupField, string>> = {};
      const others: string[] = [];
      for (const item of error.fieldErrors) {
        if (isSignupField(item.field)) errors[item.field] = item.reason;
        else others.push(item.reason);
      }
      setFieldErrors(errors);
      if (others.length > 0) setFormError({ message: others.join(' ') });
      return;
    }
    setFormError({ message: error.message, traceId: serverTraceId(error) });
  }

  return (
    <AuthLayout
      title="회원가입"
      description="가입하면 이메일로 인증 코드를 보내요."
      footer={
        <>
          <span>이미 계정이 있나요?</span>
          <Link to={withNext('/login', next)} className={FOOTER_LINK_CLASS}>
            로그인
          </Link>
        </>
      }
    >
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <EmailField autoFocus value={email} onChange={setEmail} error={fieldErrors.email} />
        <PasswordField
          label="비밀번호"
          name="password"
          autoComplete="new-password"
          hint="10~64자로, 영문·숫자·특수문자를 하나 이상씩 넣어 주세요. 공백은 쓸 수 없어요."
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={fieldErrors.password}
        />
        <PasswordField
          label="비밀번호 확인"
          name="passwordConfirm"
          autoComplete="new-password"
          value={passwordConfirm}
          onChange={(event) => handlePasswordConfirmChange(event.target.value)}
          onBlur={handlePasswordConfirmBlur}
          error={fieldErrors.passwordConfirm}
        />
        <TextField
          label="닉네임"
          type="text"
          name="nickname"
          autoComplete="nickname"
          hint="2~20자로 정해 주세요. 앞뒤 공백이나 두 칸 이어진 공백은 쓸 수 없어요."
          value={nickname}
          onChange={(event) => setNickname(event.target.value)}
          error={fieldErrors.nickname}
        />
        <Checkbox
          label="만 14세 이상이에요"
          name="ageConfirmed"
          checked={ageConfirmed}
          onChange={(event) => setAgeConfirmed(event.target.checked)}
          error={fieldErrors.ageConfirmed}
        />
        {formError && (
          <div role="alert">
            <ErrorNotice title="가입하지 못했어요" error={formError.message} traceId={formError.traceId} />
          </div>
        )}
        <p className="text-sm text-ink-muted">
          가입하면{' '}
          <Link to="/terms" className={NOTICE_LINK_CLASS}>
            이용약관
          </Link>
          과{' '}
          <Link to="/privacy" className={NOTICE_LINK_CLASS}>
            개인정보 처리방침
          </Link>
          에 동의하게 돼요.
        </p>
        <Button type="submit" fullWidth loading={submitting}>
          가입하기
        </Button>
      </form>
    </AuthLayout>
  );
}
