import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { login } from '../../api/auth';
import { ApiError } from '../../api/client';
import { serverTraceId, toUserMessage } from '../../api/errors';
import type { LoginResponse, Me } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { EmailField } from '../../components/EmailField';
import { composeEmail, EMPTY_EMAIL } from '../../components/emailValue';
import type { EmailValue } from '../../components/emailValue';
import { ErrorNotice } from '../../components/ErrorNotice';
import { TextField } from '../../components/TextField';
import { formatKstDateTime } from '../../lib/datetime';
import { AuthLayout } from './AuthLayout';
import { enterAfterAuth } from './enterAfterAuth';
import { safeNextPath, withNext } from './nextPath';
import { emailRequiredMessage, PASSWORD_REQUIRED } from './requiredMessages';
import { useSession } from './session';
import { useRetryCountdown } from './useRetryCountdown';
import { useDocumentTitle } from '../../components/useDocumentTitle';

type LoginField = 'email' | 'password';

type FormError = { message: string; detail?: string; traceId?: string };

const FOOTER_LINK_CLASS =
  'inline-flex min-h-11 items-center font-semibold text-accent underline underline-offset-4 hover:text-accent-strong';

function destinationAfterLogin(me: Pick<Me, 'status'>, next: string | null): string {
  if (me.status !== 'UNVERIFIED') return next ?? '/map';
  return next?.startsWith('/verify-email') ? next : withNext('/verify-email', next);
}

export function LoginPage() {
  const { search } = useLocation();
  const next = safeNextPath(search);
  const session = useSession();
  const lock = useRetryCountdown();

  const [email, setEmail] = useState<EmailValue>(EMPTY_EMAIL);
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<LoginField, string>>>({});
  const [formError, setFormError] = useState<FormError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useDocumentTitle('로그인 · 뫼와들');

  useEffect(() => {
    if (session.status === 'authenticated' && session.me && !submitting) {
      navigate(destinationAfterLogin(session.me, next), { replace: true });
    }
  }, [session.status, session.me, next, submitting]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || lock.secondsLeft > 0) return;

    const emptyErrors: Partial<Record<LoginField, string>> = {};
    const emailMissing = emailRequiredMessage(email);
    if (emailMissing) emptyErrors.email = emailMissing;
    if (password === '') emptyErrors.password = PASSWORD_REQUIRED;
    setFieldErrors(emptyErrors);
    setFormError(null);
    if (Object.keys(emptyErrors).length > 0) return;

    setSubmitting(true);
    let result: LoginResponse;
    try {
      result = await login({ email: composeEmail(email), password });
    } catch (error) {
      setSubmitting(false);
      handleError(error);
      return;
    }
    await enterAfterAuth(session, (me) => destinationAfterLogin(me ?? result, next));
  }

  function handleError(error: unknown) {
    if (!(error instanceof ApiError)) {
      setFormError({ message: toUserMessage(error), traceId: serverTraceId(error) });
      return;
    }
    if (error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const errors: Partial<Record<LoginField, string>> = {};
      const others: string[] = [];
      for (const { field, reason } of error.fieldErrors) {
        if (field === 'email' || field === 'password') errors[field] = reason;
        else others.push(reason);
      }
      setFieldErrors(errors);
      if (others.length > 0) setFormError({ message: others.join(' ') });
      return;
    }
    if (error.code === 'LOGIN_LOCKED' && error.retryAfterSeconds !== undefined) {
      lock.start(error.retryAfterSeconds);
    }
    const suspendedUntil = error.code === 'MEMBER_SUSPENDED' ? error.body?.suspendedUntil : undefined;
    setFormError({
      message: error.message,
      detail: suspendedUntil ? `${formatKstDateTime(suspendedUntil)}까지 이용이 제한돼요.` : undefined,
      traceId: serverTraceId(error),
    });
  }

  return (
    <AuthLayout
      title="로그인"
      footer={
        <>
          <span>아직 계정이 없나요?</span>
          <Link to={withNext('/signup', next)} className={FOOTER_LINK_CLASS}>
            회원가입
          </Link>
        </>
      }
    >
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <EmailField autoFocus autoComplete="username" value={email} onChange={setEmail} error={fieldErrors.email} />
        <TextField
          label="비밀번호"
          type="password"
          name="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={fieldErrors.password}
        />
        <Link to="/password-reset" className={`${FOOTER_LINK_CLASS} -my-2 self-end text-sm`}>
          비밀번호를 잊었나요?
        </Link>
        {formError && (
          <div role="alert">
            <ErrorNotice title="로그인하지 못했어요" error={formError.message} traceId={formError.traceId}>
              {formError.detail && <p className="mt-1">{formError.detail}</p>}
            </ErrorNotice>
          </div>
        )}
        <Button
          type="submit"
          fullWidth
          loading={submitting}
          disabled={lock.secondsLeft > 0}
          disabledReason={lock.secondsLeft > 0 ? `${lock.secondsLeft}초 뒤에 다시 시도할 수 있어요` : undefined}
        >
          로그인
        </Button>
      </form>
    </AuthLayout>
  );
}
