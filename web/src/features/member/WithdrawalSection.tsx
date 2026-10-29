import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { withdraw } from '../../api/members';
import { navigate } from '../../app/router';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Notice } from '../../components/Notice';
import { PageCard } from '../../components/PageLayout';
import { PasswordField } from '../../components/PasswordField';
import { PASSWORD_REQUIRED } from './requiredMessages';
import { useSession } from '../../app/session/session';
import { useRetryCountdown } from '../../components/useRetryCountdown';

const WRONG_PASSWORD = '비밀번호가 맞지 않아요. 다시 입력해 주세요.';

export function WithdrawalSection() {
  const [open, setOpen] = useState(false);

  return (
    <PageCard title="회원 탈퇴">
      <p className="text-sm text-ink-muted">탈퇴하면 계정 정보가 지워지고 되돌릴 수 없어요.</p>
      <div>
        <Button variant="danger-outline" onClick={() => setOpen(true)}>
          회원 탈퇴
        </Button>
      </div>
      {open && <WithdrawalDialog onClose={() => setOpen(false)} />}
    </PageCard>
  );
}

function WithdrawalDialog({ onClose }: { onClose: () => void }) {
  const session = useSession();
  const [password, setPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [formError, setFormError] = useState<{ cause: unknown } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const lock = useRetryCountdown();

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || lock.secondsLeft > 0) return;
    setFormError(null);
    if (password === '') {
      setPasswordError(PASSWORD_REQUIRED);
      return;
    }
    setPasswordError(null);

    setSubmitting(true);
    try {
      await withdraw(password);
    } catch (error) {
      setSubmitting(false);
      handleError(error);
      return;
    }
    navigate('/', { replace: true });
    void session.refresh().catch(() => null);
  }

  function handleError(error: unknown) {
    if (!(error instanceof ApiError)) {
      setFormError({ cause: error });
      return;
    }
    if (error.code === 'LOGIN_FAILED') {
      setPasswordError(WRONG_PASSWORD);
      return;
    }
    if (error.code === 'LOGIN_LOCKED' && error.retryAfterSeconds !== undefined) {
      lock.start(error.retryAfterSeconds);
    }
    if (error.code === 'INVALID_INPUT') {
      setPasswordError(error.fieldErrors.find(({ field }) => field === 'password')?.reason ?? PASSWORD_REQUIRED);
      return;
    }
    if (error.code === 'AUTHENTICATION_REQUIRED') {
      void session.refresh().catch(() => null);
      return;
    }
    setFormError({ cause: error });
  }

  return (
    <Dialog title="회원 탈퇴" onClose={() => !submitting && onClose()}>
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Notice tone="warning" title="탈퇴하기 전에 확인해 주세요">
          <ul className="list-disc pl-5">
            <li>닉네임과 이메일이 지워지고 되돌릴 수 없어요.</li>
            <li>합류한 베이스캠프에서 빠지고 캠프 리더라면 그 베이스캠프는 취소돼요.</li>
            <li>결제를 기다리는 행사 신청은 취소돼요.</li>
          </ul>
        </Notice>
        {formError && (
          <div role="alert">
            <ErrorNotice title="탈퇴하지 못했어요" error={formError.cause} />
          </div>
        )}
        <PasswordField
          label="비밀번호 확인"
          name="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={passwordError ?? undefined}
        />
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" disabled={submitting} onClick={onClose}>
            돌아가기
          </Button>
          <Button
            type="submit"
            variant="danger"
            loading={submitting}
            disabled={lock.secondsLeft > 0}
            disabledReason={lock.secondsLeft > 0 ? `${lock.secondsLeft}초 뒤에 다시 시도할 수 있어요` : undefined}
          >
            탈퇴하기
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
