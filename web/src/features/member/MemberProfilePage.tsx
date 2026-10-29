import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { ApiError } from '../../api/client';
import { fetchMemberProfile } from '../../api/members';
import type { MemberProfile } from '../../api/types';
import { Link, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { Evidence } from '../../components/Evidence';
import { ErrorNotice } from '../../components/ErrorNotice';
import { PageCard, PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { useDocumentTitle } from '../../components/useDocumentTitle';
import { MemberBoard, MEMBER_BOARD_LINK_CLASS } from './MemberBoard';
import { AGE_GROUP_LABELS, companionTagLabel, GENDER_LABELS } from './memberLabels';
import { withNext } from '../../app/session/nextPath';
import { useSession } from '../../app/session/session';
import { useViewerEpoch } from '../../app/session/useViewerEpoch';

type ProfileResult =
  { memberId: number; attempt: number; profile: MemberProfile } | { memberId: number; attempt: number; error: unknown };

export function MemberProfilePage({ memberId }: { memberId: number }) {
  const viewerEpoch = useViewerEpoch();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<ProfileResult | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchMemberProfile(memberId, controller.signal).then(
      (profile) => {
        if (!controller.signal.aborted) setResult({ memberId, attempt, profile });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ memberId, attempt, error });
      },
    );
    return () => controller.abort();
  }, [memberId, attempt, viewerEpoch]);

  const current = result && result.memberId === memberId && result.attempt === attempt ? result : null;
  const showSkeleton = useDelayedFlag(current === null);
  const nickname = current && 'profile' in current ? current.profile.nickname : null;

  useDocumentTitle(nickname ? `${nickname} · 뫼와들` : '회원 프로필 · 뫼와들');

  let body: ReactNode;
  if (current === null) {
    body = showSkeleton ? (
      <div className="flex flex-col gap-3">
        <Skeleton className="h-6 w-1/2" />
        <Skeleton className="h-16 w-full" />
      </div>
    ) : null;
  } else if ('profile' in current) {
    body = <ProfileContent profile={current.profile} />;
  } else if (current.error instanceof ApiError && current.error.code === 'NOT_FOUND') {
    body = <EmptyState framed title="회원을 찾을 수 없어요" description="탈퇴했거나 주소가 잘못됐어요." />;
  } else {
    body = (
      <div className="flex flex-col gap-3">
        <ErrorNotice title="프로필을 불러오지 못했어요" error={current.error} />
        <div>
          <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
            다시 불러오기
          </Button>
        </div>
      </div>
    );
  }

  return (
    <PageLayout title={nickname ?? '회원 프로필'} titleInContent>
      {body}
    </PageLayout>
  );
}

function ProfileContent({ profile }: { profile: MemberProfile }) {
  const { pathname } = useLocation();
  const session = useSession();
  const isMe = session.me?.memberId === profile.memberId;

  const board = (
    <MemberBoard
      nickname={profile.nickname}
      trustLevel={profile.trustLevel}
      aside={
        <Link to={`/members/${profile.memberId}/memories`} className={MEMBER_BOARD_LINK_CLASS}>
          기록 지도
        </Link>
      }
    />
  );

  if (profile.trustLevel === undefined) {
    return (
      <>
        {board}
        <PageCard title="프로필">
          <p className="text-base text-ink-muted">로그인하면 이 회원의 신뢰 단계와 동행 기록을 볼 수 있어요.</p>
          <Link
            to={withNext('/login', pathname)}
            className="inline-flex min-h-11 items-center self-start font-semibold text-accent underline underline-offset-2"
          >
            로그인
          </Link>
        </PageCard>
      </>
    );
  }

  const summary = profile.companionReviewSummary;

  return (
    <>
      {board}
      <PageCard title="기본 정보">
        <Evidence
          items={[
            {
              label: '연령대',
              value: verifiedValue(
                profile.ageGroup ? AGE_GROUP_LABELS[profile.ageGroup] : null,
                profile.ageGroupVerified,
              ),
            },
            {
              label: '성별',
              value: verifiedValue(profile.gender ? GENDER_LABELS[profile.gender] : null, profile.genderVerified),
            },
          ]}
        />
        <p className="text-sm text-ink-muted">
          베이스캠프의 연령·성별 조건은 본인확인한 정보로만 따져요. 직접 입력한 정보는 참고로만 봐 주세요.
        </p>
        {isMe && (
          <Link to="/me" className="inline-flex min-h-11 items-center self-start text-sm font-semibold text-accent">
            계정 설정
          </Link>
        )}
      </PageCard>

      <PageCard title="동행 기록">
        <Evidence
          items={[
            { label: '완료한 동행', value: <Figure value={profile.completedCompanions ?? 0} unit="회" /> },
            {
              label: '"다시 동행하고 싶어요"',
              value:
                summary?.rejoinRate === null || summary === undefined ? (
                  '받은 후기 없음'
                ) : (
                  <Figure value={summary.rejoinRate} unit="%" />
                ),
            },
          ]}
        />
        {summary && summary.topTags.length > 0 && (
          <div className="flex flex-col gap-2">
            <p className="text-sm text-ink-muted">자주 받은 태그</p>
            <p className="text-sm text-ink">{summary.topTags.map(companionTagLabel).join(' · ')}</p>
          </div>
        )}
      </PageCard>
    </>
  );
}

function verifiedValue(label: string | null, verified: boolean | undefined): string {
  if (label === null) return '밝히지 않음';
  return `${label} · ${verified ? '본인확인됨' : '직접 입력'}`;
}

function Figure({ value, unit }: { value: number; unit: string }) {
  return (
    <span className="whitespace-nowrap">
      <b className="text-base font-extrabold tabular-nums text-ink">{value}</b>
      <span className="text-xs font-semibold text-ink-muted">{unit}</span>
    </span>
  );
}
