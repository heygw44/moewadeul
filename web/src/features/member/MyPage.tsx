import { useEffect, useState } from 'react';
import { fetchMyTrust } from '../../api/trust';
import type { Me, MyTrust } from '../../api/types';
import { Link } from '../../app/router';
import { Button } from '../../components/Button';
import { Icon } from '../../components/icons';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Notice } from '../../components/Notice';
import { BoardSection, PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { useDocumentTitle } from '../../components/useDocumentTitle';
import { MySoloTripsCard } from '../trip/MySoloTripsCard';
import { MemberBoard, MemberBoardTag, MEMBER_BOARD_LINK_CLASS } from './MemberBoard';
import { useRequireAuth } from '../../app/session/useRequireAuth';

export function MyPage() {
  const session = useRequireAuth('/me');
  const showSkeleton = useDelayedFlag(session.status === 'loading');

  useDocumentTitle('내 정보 · 뫼와들');

  if (session.status !== 'authenticated' || !session.me) {
    return <PageLayout title="내 정보">{showSkeleton ? <Skeleton className="h-40 w-full" /> : null}</PageLayout>;
  }

  const me = session.me;
  return (
    <PageLayout title="내 정보" titleInContent aside={<ShortcutAside me={me} onLogout={() => void session.logout()} />}>
      <TrustCard me={me} />
      <AccountCard me={me} />
      <MySoloTripsCard />
    </PageLayout>
  );
}

const SHORTCUT_ROW_CLASS =
  'flex min-h-14 w-full items-center justify-between gap-3 rounded-row px-5 text-left text-base font-bold text-ink hover:bg-fill-strong';

function ShortcutAside({ me, onLogout }: { me: Me; onLogout: () => void }) {
  const links = [
    { to: '/me/settings', label: '계정 설정' },
    { to: '/me/basecamps', label: '내 베이스캠프' },
    { to: '/me/memories', label: '내 기록 지도' },
    { to: '/me/program-applications', label: '내 행사 신청' },
    { to: '/me/companion-reviews', label: '동행 후기' },
    ...(me.role === 'ADMIN' ? [{ to: '/admin/reports', label: '관리자' }] : []),
    { to: `/members/${me.memberId}`, label: '다른 회원에게 보이는 내 프로필' },
  ];
  return (
    <BoardSection title="바로 가기">
      <nav aria-label="바로 가기" className="flex flex-col">
        {links.map((link) => (
          <Link key={link.to} to={link.to} className={SHORTCUT_ROW_CLASS}>
            <span className="min-w-0 break-words">{link.label}</span>
            <Icon name="chevronRight" size={18} className="shrink-0 text-ink-muted" />
          </Link>
        ))}
        <button type="button" onClick={onLogout} className={`${SHORTCUT_ROW_CLASS} lg:hidden`}>
          로그아웃
        </button>
      </nav>
    </BoardSection>
  );
}

function AccountCard({ me }: { me: Me }) {
  return (
    <BoardSection title="계정">
      <dl className="flex flex-col">
        <BoardRow label="닉네임" value={me.nickname} />
        <BoardRow label="이메일 · 나만 볼 수 있어요" value={me.email} />
      </dl>
      {me.status === 'UNVERIFIED' && (
        <div className="p-4">
          <Notice tone="warning" title="이메일 인증이 필요해요">
            <p>인증을 마치면 후기를 쓰고 박지를 제보할 수 있어요.</p>
            <Link to="/verify-email" className="mt-1 inline-flex min-h-11 items-center font-semibold text-accent">
              이메일 인증하기
            </Link>
          </Notice>
        </div>
      )}
    </BoardSection>
  );
}

function BoardRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 px-5 py-3.5">
      <dt className="text-base text-ink-muted">{label}</dt>
      <dd className="min-w-0 break-all text-base font-bold text-ink">{value}</dd>
    </div>
  );
}

type TrustResult = { key: string; trust: MyTrust } | { key: string; error: { cause: unknown } };

function TrustCard({ me }: { me: Me }) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<TrustResult | null>(null);
  const key = `${attempt}:${me.trustLevel}:${me.identityVerified}`;

  useEffect(() => {
    const controller = new AbortController();
    fetchMyTrust(controller.signal).then(
      (trust) => {
        if (!controller.signal.aborted) setResult({ key, trust });
      },
      (cause: unknown) => {
        if (!controller.signal.aborted) setResult({ key, error: { cause } });
      },
    );
    return () => controller.abort();
  }, [key]);

  const current = result?.key === key ? result : null;
  const trust = current && 'trust' in current ? current.trust : null;
  const error = current && 'error' in current ? current.error : null;
  const level = trust?.trustLevel ?? me.trustLevel;
  const next = trust?.nextLevel;

  return (
    <>
      <MemberBoard
        nickname={me.nickname}
        trustLevel={level}
        caption={me.status === 'UNVERIFIED' ? '이메일 인증 전' : undefined}
        aside={
          me.identityVerified ? (
            <MemberBoardTag>
              <Icon name="check" size={16} />
              본인확인됨
            </MemberBoardTag>
          ) : (
            <Link to="/identity-verification" className={MEMBER_BOARD_LINK_CLASS}>
              본인확인하기
            </Link>
          )
        }
      />
      {!me.identityVerified && (
        <p className="text-sm text-ink-muted">
          본인확인을 마치면 베이스캠프를 열거나 합류할 수 있어요. 성인만 할 수 있고 출생연도와 성별만 확인해요.
        </p>
      )}
      {error && (
        <div className="flex flex-col gap-2">
          <ErrorNotice title="신뢰 단계를 불러오지 못했어요" error={error.cause} />
          <div>
            <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}
      {next && (
        <BoardSection title={`신뢰 단계 ${next.level}까지`}>
          <dl className="flex flex-col">
            <ProgressRow
              label="완료한 동행"
              ratio={(next.completedCompanions.current ?? 0) / next.completedCompanions.required}
              value={`${next.completedCompanions.current ?? 0} / ${next.completedCompanions.required}회`}
            />
            <ProgressRow
              label={'"다시 동행하고 싶어요"'}
              ratio={next.rejoinRate.current === null ? 0 : next.rejoinRate.current / next.rejoinRate.required}
              value={
                next.rejoinRate.current === null
                  ? `받은 후기 없음 · ${next.rejoinRate.required}% 이상`
                  : `${next.rejoinRate.current}% / ${next.rejoinRate.required}% 이상`
              }
            />
            <ProgressRow
              label="최근 180일 출발 직전 나가기"
              value={`${next.recentEarlyLeaves.current} / ${next.recentEarlyLeaves.limit}회 미만`}
            />
            <ProgressRow label="최근 180일 확정 제재" value={next.noRecentSanction ? '없음' : '있음'} />
          </dl>
        </BoardSection>
      )}
    </>
  );
}

function ProgressRow({ label, value, ratio }: { label: string; value: string; ratio?: number }) {
  const percent = ratio === undefined ? null : Math.round(Math.min(Math.max(ratio, 0), 1) * 100);
  return (
    <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-5 py-3.5 sm:grid sm:grid-cols-[minmax(0,1fr)_8.75rem_auto]">
      <dt className="text-base text-ink">{label}</dt>
      <dd className="ml-auto text-right text-base font-bold tabular-nums text-ink sm:order-last">{value}</dd>
      {percent !== null ? (
        <dd aria-hidden="true" className="h-1.5 basis-full overflow-hidden rounded-control bg-surface">
          <span className="block h-full bg-brand" style={{ width: `${percent}%` }} />
        </dd>
      ) : (
        <dd aria-hidden="true" className="hidden sm:block" />
      )}
    </div>
  );
}
