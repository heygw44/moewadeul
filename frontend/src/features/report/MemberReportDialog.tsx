import { useState } from 'react';
import { createMemberReport } from '../../api/memberReports';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import type { ReportKind, ReportType } from '../../api/types';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { Dialog } from '../../components/Dialog';
import { Notice } from '../../components/Notice';
import { TextArea } from '../../components/TextArea';

const CONTENT_MAX = 1000;

type MemberReportType = Exclude<ReportType, 'INAPPROPRIATE_REVIEW'>;

const MEMBER_REPORT_OPTIONS: ReadonlyArray<ChoiceOption<MemberReportType>> = [
  { value: 'NO_SHOW', label: '약속한 날 나오지 않았어요' },
  { value: 'MONEY_REQUEST', label: '금전을 요구했어요' },
  { value: 'HARASSMENT_OR_THREAT', label: '성희롱하거나 위협했어요' },
  { value: 'OFFENSIVE_BEHAVIOR', label: '불쾌한 행동을 했어요' },
  { value: 'FAKE_PROFILE', label: '프로필이 사실과 달라요' },
  { value: 'ILLEGAL_CAMPING_INDUCEMENT', label: '불법 야영을 부추겼어요' },
];

type MemberReportDialogProps = {
  kind: ReportKind;
  targetMemberId: number;
  targetNickname: string;
  basecampId: number;
  companionReviewId?: number;
  onClose: () => void;
};

export function MemberReportDialog({
  kind,
  targetMemberId,
  targetNickname,
  basecampId,
  companionReviewId,
  onClose,
}: MemberReportDialogProps) {
  const [type, setType] = useState<MemberReportType | null>(null);
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<ApiError | string | null>(null);
  const [typeError, setTypeError] = useState<string | undefined>();
  const [contentError, setContentError] = useState<string | undefined>();

  const isReview = kind === 'REVIEW';
  const trimmed = content.trim();
  const ready = trimmed.length > 0 && (isReview || type !== null);

  async function submit() {
    if (!ready || submitting) return;
    const reportType: ReportType = isReview ? 'INAPPROPRIATE_REVIEW' : (type as MemberReportType);
    setSubmitting(true);
    setError(null);
    setTypeError(undefined);
    setContentError(undefined);
    try {
      await createMemberReport({
        targetMemberId,
        basecampId,
        kind,
        companionReviewId: isReview ? companionReviewId : undefined,
        type: reportType,
        content: trimmed,
      });
      setDone(true);
    } catch (caught) {
      if (caught instanceof ApiError && caught.fieldErrors.length > 0) {
        const typeItem = caught.fieldErrors.find((item) => item.field === 'type');
        const contentItem = caught.fieldErrors.find((item) => item.field === 'content');
        setTypeError(typeItem?.reason);
        setContentError(contentItem?.reason);
        if (!typeItem && !contentItem) setError(caught);
      } else {
        setError(caught instanceof ApiError ? caught : toUserMessage(caught));
      }
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <Dialog title={isReview ? '후기 신고' : `${targetNickname}님 신고`} onClose={onClose}>
        <div className="flex flex-col gap-4">
          <p role="status" className="text-base font-semibold text-accent-deep">
            신고를 접수했어요
          </p>
          <p className="text-sm text-ink-muted">관리자가 검토한 뒤 결과를 알림으로 보내요.</p>
          <div className="flex justify-end">
            <Button onClick={onClose}>닫기</Button>
          </div>
        </div>
      </Dialog>
    );
  }

  return (
    <Dialog title={isReview ? '후기 신고' : `${targetNickname}님 신고`} onClose={() => !submitting && onClose()}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-ink-muted">
          {isReview
            ? `${targetNickname}님이 내게 쓴 동행 후기를 신고해요.`
            : '이 베이스캠프에서 함께한 회원만 신고할 수 있어요.'}
        </p>
        {error && (
          <div role="alert">
            <Notice tone="danger" title="신고를 접수하지 못했어요">
              <p>{typeof error === 'string' ? error : error.message}</p>
              {typeof error !== 'string' && error.status >= 500 && error.traceId && (
                <p className="mt-1 font-mono text-xs text-ink-muted">문의 번호 {error.traceId}</p>
              )}
            </Notice>
          </div>
        )}
        {isReview ? (
          <p className="text-base text-ink">신고 사유: 부적절한 후기</p>
        ) : (
          <ChoiceGroup
            legend="신고 사유"
            name="reportType"
            options={MEMBER_REPORT_OPTIONS}
            value={type}
            onChange={setType}
            error={typeError}
            disabled={submitting}
          />
        )}
        {type === 'HARASSMENT_OR_THREAT' && (
          <Notice tone="warning" title="상대가 바로 임시 정지돼요">
            <p>접수하면 상대 회원은 72시간 동안 이용이 정지돼요. 관리자가 검토한 뒤 정지를 확정하거나 풀어요.</p>
          </Notice>
        )}
        <TextArea
          label="신고 내용"
          name="content"
          maxLength={CONTENT_MAX}
          value={content}
          disabled={submitting}
          error={contentError}
          hint="언제, 어떤 일이 있었는지 적어 주세요."
          onChange={(event) => setContent(event.target.value)}
        />
        <div className="flex flex-wrap items-start justify-end gap-2">
          <Button variant="secondary" disabled={submitting} onClick={onClose}>
            닫기
          </Button>
          <Button
            variant={type === 'HARASSMENT_OR_THREAT' ? 'danger' : 'primary'}
            loading={submitting}
            disabled={!ready}
            disabledReason={
              ready ? undefined : isReview ? '신고 내용을 적어 주세요.' : '신고 사유를 고르고 내용을 적어 주세요.'
            }
            onClick={() => void submit()}
          >
            신고하기
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
