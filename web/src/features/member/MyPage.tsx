import { useEffect, useState } from 'react';
import { toUserMessage } from '../../api/errors';
import { fetchMyTrust } from '../../api/trust';
import type { Me, MyTrust } from '../../api/types';
import { Link, navigate } from '../../app/router';
import { StatusText } from '../../components/StatusText';
import { Button } from '../../components/Button';
import { Evidence } from '../../components/Evidence';
import { AsideSection } from '../../components/HubLayout';
import { Notice } from '../../components/Notice';
import { PageCard, PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { MySoloTripsCard } from '../trip/MySoloTripsCard';
import { trustLevelBadge } from './memberLabels';
import { withNext } from './nextPath';
import { useSession } from './session';

const SHORTCUT_LINK_CLASS = 'inline-flex min-h-11 items-center text-base font-semibold text-forest hover:text-forest-strong';

export function MyPage() {
  const session = useSession();
  const showSkeleton = useDelayedFlag(session.status === 'loading');

  useEffect(() => {
    document.title = '내 정보 · 뫼와들';
  }, []);

  // 내 정보는 로그인한 세션으로만 볼 수 있다. 로그인한 뒤 이 화면으로 돌아오게 한다.
  useEffect(() => {
    if (session.status === 'anonymous') navigate(withNext('/login', '/me'), { replace: true });
  }, [session.status]);

  if (session.status !== 'authenticated' || !session.me) {
    return (
      <PageLayout title="내 정보">
        {showSkeleton ? <Skeleton className="h-40 w-full" /> : null}
      </PageLayout>
    );
  }

  const me = session.me;
  return (
    <PageLayout title="내 정보" aside={<ShortcutAside me={me} onLogout={() => void session.logout()} />}>
      <AccountCard me={me} />
      <TrustCard me={me} />
      <MySoloTripsCard />
    </PageLayout>
  );
}

function ShortcutAside({ me, onLogout }: { me: Me; onLogout: () => void }) {
  return (
    <AsideSection title="바로 가기">
      <ul>
        <li>
          <Link to="/me/settings" className={SHORTCUT_LINK_CLASS}>
            계정 설정
          </Link>
        </li>
        <li>
          <Link to="/me/basecamps" className={SHORTCUT_LINK_CLASS}>
            내 베이스캠프
          </Link>
        </li>
        <li>
          <Link to="/me/memories" className={SHORTCUT_LINK_CLASS}>
            내 추억지도
          </Link>
        </li>
        <li>
          <Link to="/me/program-applications" className={SHORTCUT_LINK_CLASS}>
            내 행사 신청
          </Link>
        </li>
        <li>
          <Link to="/me/companion-reviews" className={SHORTCUT_LINK_CLASS}>
            동행 후기
          </Link>
        </li>
        {me.role === 'ADMIN' && (
          <li>
            <Link to="/admin/reports" className={SHORTCUT_LINK_CLASS}>
              관리자
            </Link>
          </li>
        )}
        <li>
          <Link to={`/members/${me.memberId}`} className={SHORTCUT_LINK_CLASS}>
            다른 회원에게 보이는 내 프로필
          </Link>
        </li>
      </ul>
      {/* 데스크톱 머리글에는 로그아웃이 있어서 여기서는 모바일에만 둔다. 모바일 머리글에는 계정 자리가 없다. */}
      <div className="lg:hidden">
        <button
          type="button"
          onClick={onLogout}
          className="inline-flex min-h-11 items-center text-base font-semibold text-forest hover:underline"
        >
          로그아웃
        </button>
      </div>
    </AsideSection>
  );
}

function AccountCard({ me }: { me: Me }) {
  return (
    <PageCard title="계정">
      <Evidence
        items={[
          { label: '닉네임', value: me.nickname },
          { label: '이메일', value: me.email },
        ]}
      />
      <p className="text-sm text-ink-muted">이메일은 나만 볼 수 있어요. 다른 회원에게는 보이지 않아요.</p>
      {me.status === 'UNVERIFIED' && (
        <Notice tone="warning" title="이메일 인증이 필요해요">
          <p>인증을 마쳐야 후기 쓰기, 박지 제보 같은 쓰기 기능을 쓸 수 있어요.</p>
          <Link to="/verify-email" className="mt-1 inline-flex min-h-11 items-center font-semibold text-forest">
            인증 코드 입력하기
          </Link>
        </Notice>
      )}
    </PageCard>
  );
}

type TrustResult = { key: string; trust: MyTrust } | { key: string; error: string };

function TrustCard({ me }: { me: Me }) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<TrustResult | null>(null);
  // 본인확인을 마치면 단계가 바뀌므로, 세션의 신뢰 단계가 바뀔 때도 다시 받는다. 어느 요청의 결과인지 key로 구분한다.
  const key = `${attempt}:${me.trustLevel}:${me.identityVerified}`;

  useEffect(() => {
    const controller = new AbortController();
    fetchMyTrust(controller.signal).then(
      (trust) => {
        if (!controller.signal.aborted) setResult({ key, trust });
      },
      (cause: unknown) => {
        if (!controller.signal.aborted) setResult({ key, error: toUserMessage(cause) });
      },
    );
    return () => controller.abort();
  }, [key]);

  const current = result?.key === key ? result : null;
  const trust = current && 'trust' in current ? current.trust : null;
  const error = current && 'error' in current ? current.error : null;
  const badge = trustLevelBadge(trust?.trustLevel ?? me.trustLevel);
  const next = trust?.nextLevel;

  return (
    <PageCard title="신뢰 단계">
      <StatusText tone={badge.tone} icon={badge.icon}>
        {badge.label}
      </StatusText>
      {!me.identityVerified && (
        <div className="flex flex-col items-start gap-1">
          <p className="text-sm text-ink">
            본인확인을 마치면 베이스캠프를 열 수 있어요. 성인만 할 수 있고, 출생연도와 성별만 확인해요.
          </p>
          <Link to="/identity-verification" className="inline-flex min-h-11 items-center font-semibold text-forest underline underline-offset-2">
            본인확인하기
          </Link>
        </div>
      )}
      {error && (
        <div className="flex flex-col gap-2">
          <Notice tone="danger" title="신뢰 단계를 불러오지 못했어요">
            <p>{error}</p>
          </Notice>
          <div>
            <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}
      {next && (
        <div className="flex flex-col gap-2">
          <p className="text-sm text-ink-muted">신뢰 회원(단계 {next.level})이 되려면</p>
          <Evidence
            items={[
              {
                label: '완료한 동행',
                value: `${next.completedCompanions.current ?? 0} / ${next.completedCompanions.required}회`,
              },
              {
                label: '"다시 동행하고 싶어요"',
                value:
                  next.rejoinRate.current === null
                    ? `받은 후기 없음 / ${next.rejoinRate.required}% 이상`
                    : `${next.rejoinRate.current}% / ${next.rejoinRate.required}% 이상`,
              },
              { label: '최근 180일 제재', value: next.noRecentSanction ? '없음' : '있음' },
            ]}
          />
        </div>
      )}
    </PageCard>
  );
}
