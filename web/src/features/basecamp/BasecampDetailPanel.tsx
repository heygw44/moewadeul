import { useEffect, useEffectEvent, useState } from 'react';
import type { ReactNode } from 'react';
import { applyToBasecamp, cancelMyApplication, fetchBasecampDetail, leaveBasecamp } from '../../api/basecamps';
import { ApiError } from '../../api/client';
import type { BasecampDetail, BasecampMember, JoinUnmetReason } from '../../api/types';
import { Link, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { buttonClassName } from '../../components/buttonClass';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { Icon } from '../../components/icons';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Notice } from '../../components/Notice';
import { StatusText } from '../../components/StatusText';
import { Skeleton } from '../../components/Skeleton';
import { TextArea } from '../../components/TextArea';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { departureInstant, formatLocalDate } from '../../lib/datetime';
import { trustLevelBadge } from '../member/memberLabels';
import { withNext } from '../member/nextPath';
import { MemberReportDialog } from '../report/MemberReportDialog';
import { useSession } from '../member/session';
import { useViewerEpoch } from '../member/useViewerEpoch';
import {
  BASECAMP_STATUS_GUIDE,
  BASECAMP_STATUS_META,
  headcountText,
  joinConditionSummary,
  nightsBetween,
  profileSummary,
  unmetReasonText,
} from './basecampLabels';
import { useDocumentTitle } from '../../components/useDocumentTitle';

type BasecampDetailPanelProps = {
  basecampId: number;
  onBack: () => void;
  onLoaded?: (detail: BasecampDetail) => void;
};

type DetailResult = { basecampId: number; detail: BasecampDetail } | { basecampId: number; error: unknown };

const SECTION_CLASS = 'mt-8';
const SECTION_TITLE_CLASS = 'mb-3 text-[1.1875rem] font-extrabold tracking-[-0.02em] text-ink';
const BOARD_CLASS = 'overflow-hidden rounded-sheet bg-fill';
const MESSAGE_MAX = 500;
const ACTION_LINK_CLASS = buttonClassName('secondary');
const SPOT_UNAVAILABLE_TEXT = '이 장소는 지금 지도에서 볼 수 없어요.';
const APPLY_CLOSED_BY_SPOT_TEXT = '장소를 지도에서 볼 수 없는 동안에는 합류 신청을 받지 않아요.';

const CONFIRMED_SAFETY_RULES = [
  '출발 전에 일정을 가족이나 지인에게 알려 주세요.',
  '되도록 일행과 떨어지지 마세요.',
  '야영한 자리에는 흔적을 남기지 마세요.',
];

export function BasecampDetailPanel({ basecampId, onBack, onLoaded }: BasecampDetailPanelProps) {
  const viewerEpoch = useViewerEpoch();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<DetailResult | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchBasecampDetail(basecampId, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setResult({ basecampId, detail });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ basecampId, error });
      },
    );
    return () => controller.abort();
  }, [basecampId, attempt, viewerEpoch]);

  const current = result && result.basecampId === basecampId ? result : null;
  const showSkeleton = useDelayedFlag(current === null);
  const loadedDetail = current && 'detail' in current ? current.detail : null;
  const loadedTitle = loadedDetail?.title ?? null;

  const reportLoaded = useEffectEvent((detail: BasecampDetail) => onLoaded?.(detail));
  useEffect(() => {
    if (loadedDetail !== null) reportLoaded(loadedDetail);
  }, [loadedDetail]);

  useDocumentTitle(loadedTitle === null ? null : `${loadedTitle} · 뫼와들`, { resetOnLeave: true });

  const reload = () => setAttempt((value) => value + 1);

  let body: ReactNode;
  if (current === null) {
    body = showSkeleton ? <DetailSkeleton /> : null;
  } else if ('detail' in current) {
    body = <DetailContent detail={current.detail} onChanged={reload} />;
  } else if (current.error instanceof ApiError && current.error.code === 'NOT_FOUND') {
    body = (
      <div className="px-4 py-4">
        <EmptyState title="베이스캠프를 찾을 수 없어요" description="삭제됐거나 주소가 잘못됐어요." />
      </div>
    );
  } else {
    body = (
      <div className="flex flex-col gap-3 px-4 py-4">
        <ErrorNotice title="베이스캠프를 불러오지 못했어요" error={current.error} />
        <div>
          <Button variant="secondary" onClick={reload}>
            다시 불러오기
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-full flex-col">
      <div className="sticky top-0 z-10 flex items-center bg-surface px-2 lg:px-4">
        <button
          type="button"
          onClick={onBack}
          className="inline-flex min-h-11 items-center gap-1 rounded-control pr-3 pl-1 text-sm font-semibold text-ink hover:bg-fill"
        >
          <Icon name="chevronLeft" size={22} />
          베이스캠프 목록
        </button>
      </div>
      <div aria-busy={current === null} className="flex flex-1 flex-col">
        {body}
      </div>
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="flex flex-col gap-3 px-4 py-4">
      <Skeleton className="h-7 w-2/3" />
      <Skeleton className="h-5 w-1/3" />
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-16 w-full" />
    </div>
  );
}

function DetailContent({ detail, onChanged }: { detail: BasecampDetail; onChanged: () => void }) {
  const session = useSession();
  const status = BASECAMP_STATUS_META[detail.status];
  const isMember = detail.myRelation === 'MEMBER' || detail.myRelation === 'LEADER';
  const { spot } = detail;

  return (
    <>
      <article className="px-4 pt-2 pb-6 lg:px-6">
        <header>
          <p className="inline-flex items-center gap-1.5 text-[0.8125rem] font-bold text-ink">
            <span
              aria-hidden="true"
              className={`size-2 rounded-full ${detail.status === 'RECRUITING' ? 'bg-brand' : 'bg-ink-faint'}`}
            />
            {status.label}
          </p>
          <h2 className="mt-1 text-[1.75rem] leading-tight font-extrabold tracking-[-0.03em] break-words text-ink lg:text-[1.875rem]">
            {detail.title}
          </h2>
          <dl className="mt-5 grid grid-cols-[1fr_1fr_auto] gap-2 text-ink">
            <div className="rounded-sheet bg-fill px-4 py-3">
              <dt className="text-[0.8125rem] text-ink-muted">출발</dt>
              <dd className="text-[0.9375rem] font-extrabold whitespace-nowrap tabular-nums">
                {formatLocalDate(detail.startDate)}
              </dd>
            </div>
            <div className="rounded-sheet bg-fill px-4 py-3">
              <dt className="text-[0.8125rem] text-ink-muted">돌아오는 날</dt>
              <dd className="text-[0.9375rem] font-extrabold whitespace-nowrap tabular-nums">
                {formatLocalDate(detail.endDate)}
              </dd>
            </div>
            <div className="rounded-sheet bg-fill px-4 py-3">
              <dt className="text-[0.8125rem] text-ink-muted">일정</dt>
              <dd className="text-[0.9375rem] font-extrabold tabular-nums">
                {nightsBetween(detail.startDate, detail.endDate)}박
              </dd>
            </div>
          </dl>
        </header>

        {detail.description && <p className="mt-4 whitespace-pre-line text-base text-ink">{detail.description}</p>}

        <section className={SECTION_CLASS} aria-labelledby="basecamp-spot-title">
          <h3 id="basecamp-spot-title" className={SECTION_TITLE_CLASS}>
            장소
          </h3>
          {spot ? (
            <Link
              to={`/spots/${spot.spotId}`}
              className={`${BOARD_CLASS} flex min-h-14 items-center gap-3 px-4 py-3 hover:bg-fill-strong`}
            >
              <span
                aria-hidden="true"
                className={`flex size-7 shrink-0 items-center justify-center rounded-full text-white ${spot.type === 'BAKJI' ? 'bg-bakji' : 'bg-brand'}`}
              >
                <Icon name={spot.type === 'BAKJI' ? 'backpack' : spot.type === 'FOREST' ? 'tree' : 'tent'} size={16} />
              </span>
              <span className="min-w-0 flex-1 truncate text-base font-bold text-ink">{spot.name}</span>
              <Icon name="chevronRight" size={20} className="shrink-0 text-ink-muted" />
            </Link>
          ) : (
            <p className={`${BOARD_CLASS} px-4 py-3 text-base text-ink-muted`}>{SPOT_UNAVAILABLE_TEXT}</p>
          )}
        </section>

        <section className={SECTION_CLASS} aria-labelledby="basecamp-members-title">
          <h3 id="basecamp-members-title" className={SECTION_TITLE_CLASS}>
            인원과 멤버
          </h3>
          <div className={BOARD_CLASS}>
            <SeatRow
              headcount={detail.headcount}
              capacity={detail.capacity}
              recruiting={detail.status === 'RECRUITING'}
            />
            <ul>
              {detail.members.map((member) => (
                <MemberRow
                  key={member.memberId}
                  member={member}
                  basecampId={detail.basecampId}
                  reportable={isMember && member.memberId !== session.me?.memberId}
                />
              ))}
            </ul>
          </div>
          {detail.leader.trustLevel === undefined && (
            <p className="mt-2 text-sm text-ink-muted">로그인하면 멤버의 신뢰 단계와 동행 기록을 볼 수 있어요.</p>
          )}
        </section>

        <section className={SECTION_CLASS} aria-labelledby="basecamp-condition-title">
          <h3 id="basecamp-condition-title" className={SECTION_TITLE_CLASS}>
            합류 조건
          </h3>
          <div className={`${BOARD_CLASS} flex items-center justify-between gap-3 px-4 py-3 text-sm`}>
            <span className="text-ink">{joinConditionSummary(detail.joinCondition)}</span>
            {detail.canApply === true && <StatusText tone="accent">내 조건에 맞아요</StatusText>}
            {detail.canApply === false && (detail.unmetReasons ?? []).length > 0 && (
              <StatusText tone="warning">내 조건과 달라요</StatusText>
            )}
          </div>
        </section>

        <section className={SECTION_CLASS} aria-labelledby="basecamp-contact-title">
          <h3 id="basecamp-contact-title" className={SECTION_TITLE_CLASS}>
            연락 수단
          </h3>
          <ContactInfo contactInfo={detail.contactInfo} isMember={isMember} />
        </section>

        <div className={SECTION_CLASS}>
          <Notice tone="info" title="안전 안내">
            <p>{detail.safetyNotice}</p>
            {detail.status === 'CONFIRMED' && (
              <>
                <p className="mt-2 font-semibold">출발 전 안전 수칙</p>
                <ul className="list-disc pl-4">
                  {CONFIRMED_SAFETY_RULES.map((rule) => (
                    <li key={rule}>{rule}</li>
                  ))}
                </ul>
              </>
            )}
          </Notice>
        </div>
      </article>

      <ActionArea detail={detail} onChanged={onChanged} />
    </>
  );
}

function MemberRow({
  member,
  basecampId,
  reportable,
}: {
  member: BasecampMember;
  basecampId: number;
  reportable: boolean;
}) {
  const [reporting, setReporting] = useState(false);
  const badge = member.trustLevel === undefined ? null : trustLevelBadge(member.trustLevel);
  const profile = profileSummary(member);

  return (
    <li className="flex flex-col gap-1 px-4 py-2.5">
      <div className="flex items-center gap-3">
        <span
          aria-hidden="true"
          className="flex size-9 shrink-0 items-center justify-center rounded-full bg-surface text-[0.9375rem] font-extrabold text-brand-strong"
        >
          {member.nickname.slice(0, 1)}
        </span>
        <div className="flex min-w-0 flex-1 flex-col">
          <span className="flex flex-wrap items-center gap-x-2">
            <Link
              to={`/members/${member.memberId}`}
              className="inline-flex min-h-8 items-center text-[0.9375rem] font-bold text-ink underline-offset-2 hover:underline"
            >
              {member.nickname}
            </Link>
            {member.role === 'LEADER' && (
              <span className="rounded-control bg-brand px-2 py-0.5 text-xs font-bold text-white">캠프 리더</span>
            )}
          </span>
          {profile.length > 0 && <span className="text-[0.8125rem] text-ink-muted">{profile.join(' · ')}</span>}
        </div>
        {badge && (
          <span className="shrink-0 text-sm">
            <StatusText tone={badge.tone} icon={badge.icon}>
              {badge.label}
            </StatusText>
          </span>
        )}
        {reportable && (
          <Button variant="ghost" className="shrink-0 px-2" onClick={() => setReporting(true)}>
            신고
          </Button>
        )}
      </div>
      {reporting && (
        <MemberReportDialog
          kind="MEMBER"
          targetMemberId={member.memberId}
          targetNickname={member.nickname}
          basecampId={basecampId}
          onClose={() => setReporting(false)}
        />
      )}
    </li>
  );
}

function SeatRow({ headcount, capacity, recruiting }: { headcount: number; capacity: number; recruiting: boolean }) {
  const left = Math.max(0, capacity - headcount);
  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <span aria-hidden="true" className="flex gap-1">
        {Array.from({ length: capacity }, (_, index) => (
          <span
            key={index}
            className={`size-5 rounded-full ${index < headcount ? 'bg-brand' : 'bg-surface ring-[1.5px] ring-inset ring-brand'}`}
          />
        ))}
      </span>
      <span className="font-mono text-base font-extrabold tabular-nums text-ink">
        {headcountText(headcount, capacity)}명
      </span>
      {recruiting && left > 0 && <span className="ml-auto text-[0.8125rem] text-ink-muted">{left}자리 남았어요</span>}
    </div>
  );
}

function parseHttpsUrl(value: string): URL | null {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' ? url : null;
  } catch {
    return null;
  }
}

function ContactInfo({ contactInfo, isMember }: { contactInfo: string | undefined; isMember: boolean }) {
  if (contactInfo === undefined) {
    return (
      <p className="mt-2 text-sm text-ink-muted">
        {isMember ? '확정되면 연락 수단이 공개돼요.' : '연락 수단은 확정된 멤버에게만 공개돼요.'}
      </p>
    );
  }
  const url = parseHttpsUrl(contactInfo);
  if (url === null) {
    return <p className="mt-2 break-words text-base text-ink">{contactInfo}</p>;
  }
  return (
    <a
      href={url.href}
      target="_blank"
      rel="noopener noreferrer"
      className="mt-1 inline-flex min-h-11 items-center break-all text-accent underline underline-offset-2"
    >
      {url.href}
    </a>
  );
}

function UnmetReasonList({ reasons, trustLevel }: { reasons: JoinUnmetReason[]; trustLevel: number | null }) {
  if (reasons.length === 0) return null;
  return (
    <ul className="flex flex-col gap-1 text-sm text-ink">
      {reasons.map((reason) => {
        const text = unmetReasonText(reason, trustLevel);
        return (
          <li key={reason}>
            <span>{text.reason}</span>
            {text.guide && <span className="text-ink-muted"> {text.guide}</span>}
            {reason === 'TRUST_LEVEL' && trustLevel === 0 && (
              <Link
                to="/identity-verification"
                className="ml-2 inline-flex min-h-11 items-center font-semibold text-accent underline underline-offset-2"
              >
                본인확인하기
              </Link>
            )}
          </li>
        );
      })}
    </ul>
  );
}

type Pending = 'apply' | 'cancel' | 'leave' | null;

const HOUR_MS = 60 * 60 * 1000;
const LATE_LEAVE_HOURS = 48;

function isLateLeave(detail: BasecampDetail): boolean {
  if (detail.status !== 'CONFIRMED') return false;
  return Date.now() >= departureInstant(detail.startDate).getTime() - LATE_LEAVE_HOURS * HOUR_MS;
}

const LEAVABLE_STATUSES: ReadonlyArray<BasecampDetail['status']> = ['RECRUITING', 'CLOSED', 'CONFIRMED'];

function ActionArea({ detail, onChanged }: { detail: BasecampDetail; onChanged: () => void }) {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [dialog, setDialog] = useState<Pending>(null);
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<{ title: string; cause: unknown } | null>(null);
  const [done, setDone] = useState<string | null>(null);

  async function run(action: () => Promise<unknown>, successText: string, failTitle: string) {
    setSubmitting(true);
    setError(null);
    setDone(null);
    try {
      await action();
      setDone(successText);
    } catch (caught) {
      setError({ title: failTitle, cause: caught });
    } finally {
      setSubmitting(false);
      setDialog(null);
      onChanged();
    }
  }

  let content: ReactNode;
  if (session.status === 'loading') {
    content = null;
  } else if (session.status === 'anonymous' || session.me === null) {
    content =
      detail.status !== 'RECRUITING' ? (
        <p className="text-base text-ink">{BASECAMP_STATUS_GUIDE[detail.status]}</p>
      ) : detail.spot === undefined ? (
        <p className="text-base text-ink">{APPLY_CLOSED_BY_SPOT_TEXT}</p>
      ) : (
        <Link to={withNext('/login', pathname + search)} className={`${buttonClassName('primary')} w-full`}>
          합류 신청
        </Link>
      );
  } else if (session.me.status === 'UNVERIFIED') {
    content = (
      <>
        <p className="text-sm text-ink-muted">이메일 인증을 마치면 합류를 신청할 수 있어요.</p>
        <Link to="/verify-email" className={buttonClassName('primary')}>
          이메일 인증하기
        </Link>
      </>
    );
  } else if (detail.myRelation === 'LEADER') {
    content = (
      <>
        <p className="text-base text-ink">내가 연 베이스캠프예요.</p>
        <div className="flex flex-wrap gap-2">
          <Link to={`/basecamps/${detail.basecampId}/manage`} className={ACTION_LINK_CLASS}>
            관리하기
          </Link>
          {detail.status === 'COMPLETED' && (
            <Link to="/me/companion-reviews" className={ACTION_LINK_CLASS}>
              동행 후기 쓰기
            </Link>
          )}
        </div>
      </>
    );
  } else if (detail.myRelation === 'MEMBER') {
    content = (
      <>
        <p className="text-base text-ink">멤버로 합류했어요.</p>
        <div className="flex flex-wrap gap-2">
          {LEAVABLE_STATUSES.includes(detail.status) && (
            <Button variant="secondary" onClick={() => setDialog('leave')}>
              탈퇴
            </Button>
          )}
          {detail.status === 'COMPLETED' && (
            <Link to="/me/companion-reviews" className={ACTION_LINK_CLASS}>
              동행 후기 쓰기
            </Link>
          )}
        </div>
      </>
    );
  } else if (detail.myRelation === 'APPLICANT') {
    content = (
      <>
        <p className="text-base text-ink">합류 신청을 보냈어요. 캠프 리더가 승인하거나 거절할 때까지 기다려 주세요.</p>
        <Button variant="secondary" onClick={() => setDialog('cancel')}>
          신청 취소
        </Button>
      </>
    );
  } else if (detail.status !== 'RECRUITING') {
    content = <p className="text-base text-ink">{BASECAMP_STATUS_GUIDE[detail.status]}</p>;
  } else if (detail.spot === undefined) {
    content = <p className="text-base text-ink">{APPLY_CLOSED_BY_SPOT_TEXT}</p>;
  } else if (detail.canApply === true) {
    content = (
      <Button fullWidth onClick={() => setDialog('apply')}>
        합류 신청
      </Button>
    );
  } else {
    content = (
      <>
        <Button fullWidth disabled>
          합류 신청
        </Button>
        <UnmetReasonList reasons={detail.unmetReasons ?? []} trustLevel={session.me.trustLevel} />
      </>
    );
  }

  if (content === null && !error && !done) return null;

  return (
    <div className="sticky bottom-0 z-10 mt-auto flex flex-col gap-2 border-t border-line bg-surface px-4 py-3 lg:px-6">
      {done && (
        <p role="status" className="text-sm font-semibold text-accent-deep">
          {done}
        </p>
      )}
      {error && (
        <div role="alert">
          <ErrorNotice title={error.title} error={error.cause} />
        </div>
      )}
      {content}

      {dialog === 'apply' && (
        <Dialog title="합류 신청" onClose={() => !submitting && setDialog(null)}>
          <div className="flex flex-col gap-4">
            <TextArea
              label="캠프 리더에게 한마디 (선택)"
              name="message"
              maxLength={MESSAGE_MAX}
              value={message}
              onChange={(event) => setMessage(event.target.value)}
            />
            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="secondary" disabled={submitting} onClick={() => setDialog(null)}>
                닫기
              </Button>
              <Button
                loading={submitting}
                onClick={() =>
                  run(
                    async () => {
                      await applyToBasecamp(detail.basecampId, message.trim() || undefined);
                      setMessage('');
                    },
                    '합류 신청을 보냈어요.',
                    '합류 신청을 보내지 못했어요',
                  )
                }
              >
                신청하기
              </Button>
            </div>
          </div>
        </Dialog>
      )}
      {dialog === 'leave' && (
        <Dialog title="베이스캠프 탈퇴" onClose={() => !submitting && setDialog(null)}>
          <div className="flex flex-col gap-4">
            <p className="text-base text-ink">탈퇴하면 이 베이스캠프에 다시 신청할 수 없어요.</p>
            {isLateLeave(detail) && (
              <Notice tone="warning" title="출발이 얼마 남지 않았어요">
                <p>
                  출발 48시간 안에 탈퇴하면 임박 탈퇴로 기록돼요. 임박 탈퇴가 180일 안에 3번 쌓이면 신뢰 단계 2에서
                  빠져요.
                </p>
              </Notice>
            )}
            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="secondary" disabled={submitting} onClick={() => setDialog(null)}>
                닫기
              </Button>
              <Button
                variant="danger"
                loading={submitting}
                onClick={() =>
                  run(() => leaveBasecamp(detail.basecampId), '베이스캠프에서 탈퇴했어요.', '탈퇴하지 못했어요')
                }
              >
                탈퇴
              </Button>
            </div>
          </div>
        </Dialog>
      )}
      {dialog === 'cancel' && (
        <Dialog title="신청 취소" onClose={() => !submitting && setDialog(null)}>
          <div className="flex flex-col gap-4">
            <p className="text-base text-ink">합류 신청을 취소할까요? 취소해도 나중에 다시 신청할 수 있어요.</p>
            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="secondary" disabled={submitting} onClick={() => setDialog(null)}>
                닫기
              </Button>
              <Button
                variant="danger"
                loading={submitting}
                onClick={() =>
                  run(
                    () => cancelMyApplication(detail.basecampId),
                    '합류 신청을 취소했어요.',
                    '합류 신청을 취소하지 못했어요',
                  )
                }
              >
                신청 취소
              </Button>
            </div>
          </div>
        </Dialog>
      )}
    </div>
  );
}
