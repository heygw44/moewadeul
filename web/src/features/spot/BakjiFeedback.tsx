import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { confirmBakji, reportBakji } from '../../api/bakjis';
import { ApiError } from '../../api/client';
import type { BakjiProblemReportRequest, BakjiReportReason } from '../../api/types';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { Dialog } from '../../components/Dialog';
import { ErrorNotice } from '../../components/ErrorNotice';
import { TextArea } from '../../components/TextArea';
import { useSession } from '../member/session';
import { GatedButton } from '../member/GatedButton';
import { REPORT_REASON_LABELS } from './spotLabels';

type BakjiFeedbackProps = {
  spotId: number;
  onConfirmed: (confirmationCount: number) => void;
};

const REPORT_CONTENT_MAX = 1000;

const REASON_OPTIONS: ChoiceOption<BakjiReportReason>[] = (
  Object.keys(REPORT_REASON_LABELS) as BakjiReportReason[]
).map((value) => ({ value, label: REPORT_REASON_LABELS[value] }));

export function BakjiFeedback({ spotId, onConfirmed }: BakjiFeedbackProps) {
  const session = useSession();
  const [confirming, setConfirming] = useState(false);
  const [confirmed, setConfirmed] = useState<'new' | 'already' | null>(null);
  const [confirmError, setConfirmError] = useState<{ cause: unknown } | null>(null);
  const [reportOpen, setReportOpen] = useState(false);
  const [reported, setReported] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => () => controllerRef.current?.abort(), []);

  if (session.status === 'loading') return null;

  async function handleConfirm() {
    if (confirming || confirmed !== null) return;
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setConfirming(true);
    setConfirmError(null);
    try {
      const response = await confirmBakji(spotId, controller.signal);
      if (controller.signal.aborted) return;
      setConfirmed('new');
      onConfirmed(response.confirmationCount);
    } catch (error) {
      if (controller.signal.aborted) return;
      if (error instanceof ApiError && error.code === 'BAKJI_ALREADY_CONFIRMED') {
        setConfirmed('already');
      } else {
        setConfirmError({ cause: error });
      }
    } finally {
      if (!controller.signal.aborted) setConfirming(false);
    }
  }

  return (
    <div className="mt-3 flex flex-col gap-3">
      <div className="flex flex-wrap items-start gap-2">
        <GatedButton variant="secondary" loading={confirming} disabled={confirmed !== null} onClick={handleConfirm}>
          {confirmed !== null ? '확인했어요' : '다녀왔어요, 정보가 맞아요'}
        </GatedButton>
        <GatedButton variant="ghost" disabled={reported} onClick={() => setReportOpen(true)}>
          문제 신고
        </GatedButton>
      </div>

      {confirmed === 'already' && (
        <p role="status" className="text-sm text-ink-muted">
          이미 이 박지를 확인했어요.
        </p>
      )}
      {confirmError && (
        <div role="alert">
          <ErrorNotice title="확인을 남기지 못했어요" error={confirmError.cause} />
        </div>
      )}
      {reported && (
        <p role="status" className="text-sm text-ink">
          신고를 접수했어요. 신고가 쌓이면 관리자가 검토하는 동안 지도에 보이지 않아요.
        </p>
      )}

      {reportOpen && (
        <ReportDialog
          spotId={spotId}
          onClose={() => setReportOpen(false)}
          onReported={() => {
            setReportOpen(false);
            setReported(true);
          }}
        />
      )}
    </div>
  );
}

function ReportDialog({
  spotId,
  onClose,
  onReported,
}: {
  spotId: number;
  onClose: () => void;
  onReported: () => void;
}) {
  const [reason, setReason] = useState<BakjiReportReason | null>(null);
  const [content, setContent] = useState('');
  const [reasonError, setReasonError] = useState<string | undefined>();
  const [contentError, setContentError] = useState<string | undefined>();
  const [formError, setFormError] = useState<{ cause: unknown } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => () => controllerRef.current?.abort(), []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    setReasonError(undefined);
    setContentError(undefined);
    setFormError(null);
    if (reason === null) {
      setReasonError('신고 사유를 골라 주세요.');
      return;
    }

    const request: BakjiProblemReportRequest = { reason };
    if (content.trim() !== '') request.content = content.trim();

    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setSubmitting(true);
    try {
      await reportBakji(spotId, request, controller.signal);
      if (controller.signal.aborted) return;
      onReported();
    } catch (error) {
      if (controller.signal.aborted) return;
      setSubmitting(false);
      handleError(error);
    }
  }

  function handleError(error: unknown) {
    if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const others: string[] = [];
      for (const { field, reason: message } of error.fieldErrors) {
        if (field === 'reason') setReasonError(message);
        else if (field === 'content') setContentError(message);
        else others.push(message);
      }
      if (others.length > 0) setFormError({ cause: others.join(' ') });
      return;
    }
    setFormError({ cause: error });
  }

  return (
    <Dialog title="박지 문제 신고" onClose={onClose}>
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <p className="text-sm text-ink-muted">신고는 되돌릴 수 없어요. 사유를 골라 주세요.</p>
        {formError && (
          <div role="alert">
            <ErrorNotice title="신고하지 못했어요" error={formError.cause} />
          </div>
        )}
        <ChoiceGroup
          legend="신고 사유"
          name="reason"
          options={REASON_OPTIONS}
          value={reason}
          onChange={setReason}
          error={reasonError}
        />
        <TextArea
          label="자세한 내용 (선택)"
          name="content"
          maxLength={REPORT_CONTENT_MAX}
          value={content}
          onChange={(event) => setContent(event.target.value)}
          error={contentError}
        />
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" variant="danger" loading={submitting}>
            신고하기
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
