import { useEffect } from 'react';
import { PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { navigate } from '../../app/router';
import { withNext } from './nextPath';
import { PasswordChangeForm } from './PasswordChangeForm';
import { SelfReportForm } from './SelfReportForm';
import { useSession } from './session';
import { WithdrawalSection } from './WithdrawalSection';

const TITLE = '계정 설정';
const DESCRIPTION = '프로필과 비밀번호를 바꾸거나 회원 탈퇴를 할 수 있어요.';

// 계정을 고치는 기능을 한 화면에 모은다. 내 정보 화면에는 이 화면으로 가는 바로 가기만 둔다.
export function AccountSettingsPage() {
  const session = useSession();
  const showSkeleton = useDelayedFlag(session.status === 'loading');

  useEffect(() => {
    document.title = `${TITLE} · 뫼와들`;
  }, []);

  useEffect(() => {
    if (session.status === 'anonymous') navigate(withNext('/login', '/me/settings'), { replace: true });
  }, [session.status]);

  if (session.status !== 'authenticated' || !session.me) {
    return (
      <PageLayout title={TITLE} description={DESCRIPTION}>
        {showSkeleton ? <Skeleton className="h-40 w-full" /> : null}
      </PageLayout>
    );
  }

  return (
    <PageLayout title={TITLE} description={DESCRIPTION}>
      <SelfReportForm me={session.me} onSaved={() => void session.refresh().catch(() => null)} />
      <PasswordChangeForm />
      <WithdrawalSection />
    </PageLayout>
  );
}
