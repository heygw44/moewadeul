import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { withdraw } from '../../api/members';
import { navigate } from '../../app/router';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { Notice } from '../../components/Notice';
import { PageCard } from '../../components/PageLayout';
import { PasswordField } from '../../components/PasswordField';
import { PASSWORD_REQUIRED } from './requiredMessages';
import { useSession } from './session';

const WRONG_PASSWORD = '비밀번호가 맞지 않아요. 다시 확인해 주세요.';

// 탈퇴는 되돌릴 수 없어서 첫 버튼은 테두리만 있는 위험 버튼으로 두고, 비밀번호를 다시 받는 다이얼로그에서 확정한다.
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
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
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
    // 서버가 세션을 이미 지웠다. 홈으로 먼저 옮긴 뒤 세션을 다시 확인해야, 이 화면이 비로그인 상태를 보고 로그인 화면으로 보내지 않는다.
    navigate('/', { replace: true });
    void session.refresh().catch(() => null);
  }

  function handleError(error: unknown) {
    if (!(error instanceof ApiError)) {
      setFormError(toUserMessage(error));
      return;
    }
    if (error.code === 'LOGIN_FAILED') {
      setPasswordError(WRONG_PASSWORD);
      return;
    }
    if (error.code === 'INVALID_INPUT') {
      setPasswordError(error.fieldErrors.find(({ field }) => field === 'password')?.reason ?? PASSWORD_REQUIRED);
      return;
    }
    // 세션이 끊긴 401은 로그아웃 처리를 건너뛰었으므로, 세션을 다시 확인해서 비로그인 상태로 돌린다. 그러면 화면이 로그인으로 보낸다.
    if (error.code === 'AUTHENTICATION_REQUIRED') {
      void session.refresh().catch(() => null);
      return;
    }
    setFormError(toUserMessage(error));
  }

  return (
    <Dialog title="회원 탈퇴" onClose={() => !submitting && onClose()}>
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Notice tone="warning" title="탈퇴하기 전에 확인해 주세요">
          <ul className="list-disc pl-5">
            <li>닉네임과 이메일 같은 계정 정보가 지워지고 되돌릴 수 없어요.</li>
            <li>참여 중인 베이스캠프에서 빠지고, 캠프 리더라면 그 베이스캠프는 취소돼요.</li>
            <li>결제를 기다리는 행사 신청은 취소돼요.</li>
          </ul>
        </Notice>
        {formError && (
          <div role="alert">
            <Notice tone="danger" title="탈퇴하지 못했어요">
              <p>{formError}</p>
            </Notice>
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
          <Button type="submit" variant="danger" loading={submitting}>
            탈퇴하기
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
