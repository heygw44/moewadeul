import { useState } from 'react';
import { deleteBakji } from '../../api/bakjis';
import { Link, navigate } from '../../app/router';
import { Button } from '../../components/Button';
import { buttonClassName } from '../../components/buttonClass';
import { Dialog } from '../../components/Dialog';
import { ErrorNotice } from '../../components/ErrorNotice';
import { useSession } from '../member/session';

type BakjiOwnerActionsProps = {
  spotId: number;
  reporterId: number;
  onDeleted?: () => void;
};

export function BakjiOwnerActions({ spotId, reporterId, onDeleted }: BakjiOwnerActionsProps) {
  const session = useSession();
  const [deleting, setDeleting] = useState(false);

  if (session.status !== 'authenticated' || session.me?.memberId !== reporterId) return null;
  return (
    <div className="mt-3 flex flex-wrap gap-2">
      <Link to={`/bakjis/${spotId}/edit`} className={buttonClassName('secondary')}>
        수정
      </Link>
      <Button variant="danger-outline" onClick={() => setDeleting(true)}>
        삭제
      </Button>
      {deleting && <DeleteBakjiDialog spotId={spotId} onClose={() => setDeleting(false)} onDeleted={onDeleted} />}
    </div>
  );
}

function DeleteBakjiDialog({
  spotId,
  onClose,
  onDeleted,
}: {
  spotId: number;
  onClose: () => void;
  onDeleted?: () => void;
}) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<{ cause: unknown } | null>(null);

  async function handleDelete() {
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await deleteBakji(spotId);
      onDeleted?.();
      navigate('/map', { replace: true });
    } catch (cause) {
      setSubmitting(false);
      setError({ cause });
    }
  }

  return (
    <Dialog title="박지를 삭제할까요?" onClose={() => !submitting && onClose()}>
      <div className="flex flex-col gap-4">
        <p className="text-base text-ink">삭제한 박지는 지도와 장소 상세에서 사라지고 되돌릴 수 없어요.</p>
        {error && (
          <div role="alert">
            <ErrorNotice title="삭제하지 못했어요" error={error.cause} />
          </div>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            취소
          </Button>
          <Button variant="danger" loading={submitting} onClick={() => void handleDelete()}>
            삭제
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
