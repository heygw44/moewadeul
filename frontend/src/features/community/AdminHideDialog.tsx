import { useState } from 'react';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { ErrorNotice } from '../../components/ErrorNotice';
import { hideConfirmText } from './communityLabels';
import type { CommunityNoun } from './communityLabels';

type AdminHideDialogProps = {
  noun: CommunityNoun;
  nickname: string;
  hide: () => Promise<unknown>;
  onClose: () => void;
  onHidden: () => void;
};

export function AdminHideDialog({ noun, nickname, hide, onClose, onHidden }: AdminHideDialogProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function confirm() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await hide();
    } catch (caught) {
      setError(caught);
      setBusy(false);
      return;
    }
    onHidden();
  }

  return (
    <Dialog title={`${noun}을 숨길까요?`} onClose={() => !busy && onClose()}>
      <div className="flex flex-col gap-4">
        <p className="text-base text-ink">{hideConfirmText(noun, nickname)}</p>
        {error !== null && (
          <div role="alert">
            <ErrorNotice title={`${noun}을 숨기지 못했어요`} error={error} />
          </div>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" disabled={busy} onClick={onClose}>
            취소
          </Button>
          <Button variant="danger" loading={busy} onClick={() => void confirm()}>
            숨기기
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
