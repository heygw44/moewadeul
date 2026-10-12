import { useEffect, useState } from 'react';
import { toUserMessage } from '../../api/errors';
import { deleteSoloTrip, fetchMySoloTrips } from '../../api/soloTrips';
import type { SoloTrip } from '../../api/types';
import { Link } from '../../app/router';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { Notice } from '../../components/Notice';
import { PageCard } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatLocalDate } from '../../lib/datetime';

// 어느 시도의 결과인지 함께 기억해서, 다시 불러오는 동안 이전 결과를 보여 주지 않는다.
type TripsResult = { attempt: number; trips: SoloTrip[] } | { attempt: number; error: string };

export function MySoloTripsCard() {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<TripsResult | null>(null);
  const [deleting, setDeleting] = useState<SoloTrip | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchMySoloTrips(controller.signal).then(
      (trips) => {
        if (!controller.signal.aborted) setResult({ attempt, trips });
      },
      (cause: unknown) => {
        if (!controller.signal.aborted) setResult({ attempt, error: toUserMessage(cause) });
      },
    );
    return () => controller.abort();
  }, [attempt]);

  const current = result?.attempt === attempt ? result : null;
  const showSkeleton = useDelayedFlag(current === null);
  const reload = () => setAttempt((value) => value + 1);

  return (
    <PageCard title="혼자 가는 일정">
      <p className="text-sm text-ink-muted">
        나만 보는 일정이에요. 다른 사람에게는 장소의 예상 인원에 1명으로만 더해져요.
      </p>
      {current === null ? (
        showSkeleton && <Skeleton className="h-16 w-full" />
      ) : 'error' in current ? (
        <div className="flex flex-col gap-2">
          <Notice tone="danger" title="일정을 불러오지 못했어요">
            <p>{current.error}</p>
          </Notice>
          <div>
            <Button variant="secondary" onClick={reload}>
              다시 불러오기
            </Button>
          </div>
        </div>
      ) : current.trips.length === 0 ? (
        <p className="text-sm text-ink-muted">
          다가오는 일정이 없어요.{' '}
          <Link to="/map" className="font-semibold text-forest underline underline-offset-2">
            지도
          </Link>
          에서 장소를 골라 일정을 등록해 보세요.
        </p>
      ) : (
        <ul className="flex flex-col">
          {current.trips.map((trip) => (
            <li
              key={trip.soloTripId}
              className="flex items-center justify-between gap-3 border-t border-contour py-3 first:border-t-0"
            >
              <div className="flex min-w-0 flex-col">
                <Link to={`/spots/${trip.spot.spotId}`} className="truncate font-semibold text-ink hover:underline">
                  {trip.spot.name}
                </Link>
                <span className="text-sm text-ink-muted">
                  {formatLocalDate(trip.startDate)} ~ {formatLocalDate(trip.endDate)}
                </span>
              </div>
              <Button variant="danger-outline" onClick={() => setDeleting(trip)}>
                지우기
              </Button>
            </li>
          ))}
        </ul>
      )}
      {deleting && (
        <DeleteTripDialog
          trip={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={() => {
            setDeleting(null);
            reload();
          }}
        />
      )}
    </PageCard>
  );
}

function DeleteTripDialog({ trip, onClose, onDeleted }: { trip: SoloTrip; onClose: () => void; onDeleted: () => void }) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDelete() {
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await deleteSoloTrip(trip.soloTripId);
      onDeleted();
    } catch (cause) {
      setSubmitting(false);
      setError(toUserMessage(cause));
    }
  }

  return (
    <Dialog title="일정을 지울까요?" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p className="text-base text-ink">
          {trip.spot.name} {formatLocalDate(trip.startDate)} 출발 일정을 지워요. 지운 일정은 되돌릴 수 없어요.
        </p>
        {error && (
          <div role="alert">
            <Notice tone="danger" title="지우지 못했어요">
              <p>{error}</p>
            </Notice>
          </div>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            취소
          </Button>
          <Button variant="danger" loading={submitting} onClick={() => void handleDelete()}>
            지우기
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
