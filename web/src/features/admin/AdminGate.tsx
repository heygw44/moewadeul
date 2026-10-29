import type { ReactNode } from 'react';
import { EmptyState } from '../../components/EmptyState';
import { PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { UnderlineNav, UnderlineNavLink } from '../../components/UnderlineNav';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { useDocumentTitle } from '../../components/useDocumentTitle';
import { useRequireAuth } from '../../app/session/useRequireAuth';

const NAV_ITEMS = [
  { to: '/admin/reports', label: '신고 처리' },
  { to: '/admin/spots', label: '박지 검토' },
  { to: '/admin/memories', label: '기록 검토' },
  { to: '/admin/sync-jobs', label: '데이터 적재' },
] as const;

export function AdminPage({ title, current, children }: { title: string; current: string; children: ReactNode }) {
  const session = useRequireAuth();
  const showSkeleton = useDelayedFlag(session.status === 'loading');

  useDocumentTitle(`${title} · 관리자 · 뫼와들`);

  if (session.status !== 'authenticated' || !session.me) {
    return (
      <PageLayout title={title} wide>
        {showSkeleton ? <Skeleton className="h-40 w-full" /> : null}
      </PageLayout>
    );
  }

  if (session.me.role !== 'ADMIN') {
    return (
      <PageLayout title={title} wide>
        <EmptyState framed title="관리자만 볼 수 있어요" description="관리자 계정으로 로그인해 주세요." />
      </PageLayout>
    );
  }

  return (
    <PageLayout
      title={title}
      description="모든 조치는 감사 로그에 남아요."
      wide
      tabs={
        <UnderlineNav label="관리자 메뉴">
          {NAV_ITEMS.map((item) => (
            <UnderlineNavLink key={item.to} to={item.to} active={item.to === current}>
              {item.label}
            </UnderlineNavLink>
          ))}
        </UnderlineNav>
      }
    >
      <div className="flex min-w-0 flex-col gap-4">{children}</div>
    </PageLayout>
  );
}
