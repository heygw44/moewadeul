import { PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { useDocumentTitle } from '../../components/useDocumentTitle';
import { PasswordChangeForm } from './PasswordChangeForm';
import { SelfReportForm } from './SelfReportForm';
import { useRequireAuth } from './useRequireAuth';
import { WithdrawalSection } from './WithdrawalSection';

const TITLE = '계정 설정';
const DESCRIPTION = '프로필과 비밀번호를 바꾸거나 회원 탈퇴를 할 수 있어요.';

export function AccountSettingsPage() {
  const session = useRequireAuth('/me/settings');
  const showSkeleton = useDelayedFlag(session.status === 'loading');

  useDocumentTitle(`${TITLE} · 뫼와들`);

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
