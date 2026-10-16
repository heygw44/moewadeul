import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { confirmPasswordReset, requestPasswordReset } from '../../api/auth';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { EmailField } from '../../components/EmailField';
import { composeEmail, EMPTY_EMAIL } from '../../components/emailValue';
import type { EmailValue } from '../../components/emailValue';
import { Notice } from '../../components/Notice';
import { PasswordField } from '../../components/PasswordField';
import { AuthLayout } from './AuthLayout';
import {
  emailRequiredMessage,
  NEW_PASSWORD_REQUIRED,
  PASSWORD_CONFIRM_MISMATCH,
  PASSWORD_CONFIRM_REQUIRED,
} from './requiredMessages';
import { useSession } from './session';
import { useRetryCountdown } from './useRetryCountdown';

const FOOTER_LINK_CLASS =
  'inline-flex min-h-11 items-center font-semibold text-accent underline underline-offset-4 hover:text-accent-strong';

// 재설정 메일의 링크는 `/password-reset#token=...`이다. 브라우저가 # 뒤를 서버로 보내지 않아서 접근 로그에 토큰이 남지 않는다.
function tokenFromHash(hash: string): string | null {
  const token = new URLSearchParams(hash.slice(1)).get('token');
  return token && token.trim() !== '' ? token : null;
}

export function PasswordResetPage() {
  const { pathname, hash } = useLocation();
  // 주소의 해시를 지운 뒤에도 토큰은 이 상태로 남는다.
  const [token, setToken] = useState<string | null>(() => tokenFromHash(hash));
  // 요청 화면을 연 탭에서 메일 링크를 열면 해시만 바뀌고 화면은 그대로라서, 해시가 바뀔 때마다 토큰을 다시 읽는다.
  const [readHash, setReadHash] = useState(hash);
  if (hash !== readHash) {
    setReadHash(hash);
    const fromHash = tokenFromHash(hash);
    if (fromHash !== null) setToken(fromHash);
  }

  useEffect(() => {
    document.title = '비밀번호 재설정 · 뫼와들';
  }, []);

  // 토큰을 읽은 뒤에는 주소창과 방문 기록에 남지 않게 해시를 지운다.
  useEffect(() => {
    if (hash !== '') navigate(pathname, { replace: true });
  }, [pathname, hash]);

  if (token === null) return <RequestStep />;
  return <ConfirmStep key={token} token={token} onTokenInvalid={() => setToken(null)} />;
}

function BackToLogin() {
  return (
    <Link to="/login" className={FOOTER_LINK_CLASS}>
      로그인으로 돌아가기
    </Link>
  );
}

// 가입 여부와 상관없이 서버가 같은 응답을 주므로, 화면도 가입된 이메일일 때만 메일이 간다고 적는다.
function RequestStep() {
  const [email, setEmail] = useState<EmailValue>(EMPTY_EMAIL);
  const [emailError, setEmailError] = useState<string | undefined>();
  const [formError, setFormError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const wait = useRetryCountdown();

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || wait.secondsLeft > 0) return;
    setFormError(null);
    setSent(false);
    const emailMissing = emailRequiredMessage(email);
    if (emailMissing) {
      setEmailError(emailMissing);
      return;
    }
    setEmailError(undefined);

    setSubmitting(true);
    try {
      await requestPasswordReset(composeEmail(email));
      setSent(true);
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
      const emailReason = error.fieldErrors.find(({ field }) => field === 'email')?.reason;
      const others = error.fieldErrors.filter(({ field }) => field !== 'email').map(({ reason }) => reason);
      setEmailError(emailReason);
      if (others.length > 0) setFormError(others.join(' '));
      return;
    }
    if (error.code === 'PASSWORD_RESET_LIMITED' && error.retryAfterSeconds !== undefined) {
      wait.start(error.retryAfterSeconds);
    }
    setFormError(toUserMessage(error));
  }

  return (
    <AuthLayout
      title="비밀번호 재설정"
      description="가입한 이메일을 입력하면 새 비밀번호를 정하는 링크를 메일로 보내요."
      footer={<BackToLogin />}
    >
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <EmailField autoFocus value={email} onChange={setEmail} error={emailError} />
        {formError && (
          <div role="alert">
            <Notice tone="danger" title="재설정 링크를 보내지 못했어요">
              <p>{formError}</p>
            </Notice>
          </div>
        )}
        {sent && (
          <p role="status" className="text-sm text-accent-deep">
            입력한 이메일로 가입돼 있다면 재설정 링크를 보냈어요. 30분 안에 메일의 링크를 열어 주세요. 메일이 보이지
            않으면 스팸함을 살펴봐 주세요.
          </p>
        )}
        <Button
          type="submit"
          fullWidth
          loading={submitting}
          disabled={wait.secondsLeft > 0}
          disabledReason={wait.secondsLeft > 0 ? `${wait.secondsLeft}초 뒤에 다시 요청할 수 있어요` : undefined}
        >
          {sent ? '재설정 링크 다시 받기' : '재설정 링크 받기'}
        </Button>
      </form>
    </AuthLayout>
  );
}

type ConfirmField = 'newPassword' | 'newPasswordConfirm';
type ConfirmErrors = Partial<Record<ConfirmField, string>>;

function isConfirmField(field: string): field is ConfirmField {
  return field === 'newPassword' || field === 'newPasswordConfirm';
}

function ConfirmStep({ token, onTokenInvalid }: { token: string; onTokenInvalid: () => void }) {
  const session = useSession();
  const [newPassword, setNewPassword] = useState('');
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('');
  const [fieldErrors, setFieldErrors] = useState<ConfirmErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [tokenInvalidMessage, setTokenInvalidMessage] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    setFormError(null);

    const emptyErrors: ConfirmErrors = {};
    if (newPassword === '') emptyErrors.newPassword = NEW_PASSWORD_REQUIRED;
    if (newPasswordConfirm === '') emptyErrors.newPasswordConfirm = PASSWORD_CONFIRM_REQUIRED;
    else if (newPasswordConfirm !== newPassword) emptyErrors.newPasswordConfirm = PASSWORD_CONFIRM_MISMATCH;
    setFieldErrors(emptyErrors);
    if (Object.keys(emptyErrors).length > 0) return;

    setSubmitting(true);
    try {
      await confirmPasswordReset({ token, newPassword, newPasswordConfirm });
      setNewPassword('');
      setNewPasswordConfirm('');
      setDone(true);
      // 서버가 이 회원의 세션을 모두 지웠으므로, 이 브라우저에서 로그인돼 있었다면 화면의 로그인 상태도 비운다.
      void session.refresh();
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
      const errors: ConfirmErrors = {};
      const others: string[] = [];
      for (const { field, reason } of error.fieldErrors) {
        if (isConfirmField(field)) errors[field] = reason;
        else others.push(reason);
      }
      setFieldErrors(errors);
      if (others.length > 0) setFormError(others.join(' '));
      return;
    }
    if (error.code === 'MEMBER_PASSWORD_POLICY') {
      setFieldErrors({ newPassword: toUserMessage(error) });
      return;
    }
    if (error.code === 'PASSWORD_RESET_TOKEN_INVALID') {
      setTokenInvalidMessage(toUserMessage(error));
      return;
    }
    setFormError(toUserMessage(error));
  }

  if (done) {
    return (
      <AuthLayout title="비밀번호를 바꿨어요">
        <p role="status" className="text-ink-muted">
          모든 기기에서 로그아웃됐어요. 새 비밀번호로 다시 로그인해 주세요.
        </p>
        <Button type="button" fullWidth onClick={() => navigate('/login', { replace: true })}>
          로그인하기
        </Button>
      </AuthLayout>
    );
  }

  if (tokenInvalidMessage !== null) {
    return (
      <AuthLayout title="비밀번호 재설정" footer={<BackToLogin />}>
        <div role="alert">
          <Notice tone="danger" title="이 링크로는 비밀번호를 바꿀 수 없어요">
            <p>{tokenInvalidMessage}</p>
          </Notice>
        </div>
        <Button type="button" fullWidth onClick={onTokenInvalid}>
          재설정 링크 다시 받기
        </Button>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="새 비밀번호 정하기" footer={<BackToLogin />}>
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <PasswordField
          label="새 비밀번호"
          name="newPassword"
          autoComplete="new-password"
          autoFocus
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
          onChange={(event) => {
            const value = event.target.value;
            setNewPasswordConfirm(value);
            if (fieldErrors.newPasswordConfirm && value === newPassword) {
              setFieldErrors((current) => ({ ...current, newPasswordConfirm: undefined }));
            }
          }}
          onBlur={() => {
            if (newPasswordConfirm !== '' && newPasswordConfirm !== newPassword) {
              setFieldErrors((current) => ({ ...current, newPasswordConfirm: PASSWORD_CONFIRM_MISMATCH }));
            }
          }}
          error={fieldErrors.newPasswordConfirm}
        />
        {formError && (
          <div role="alert">
            <Notice tone="danger" title="비밀번호를 바꾸지 못했어요">
              <p>{formError}</p>
            </Notice>
          </div>
        )}
        <Button type="submit" fullWidth loading={submitting}>
          비밀번호 바꾸기
        </Button>
      </form>
    </AuthLayout>
  );
}
