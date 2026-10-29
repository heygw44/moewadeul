import { useEffect, useRef, useState } from 'react';
import { buttonClassName } from '../../components/buttonClass';
import type { ReactNode } from 'react';
import { toUserMessage } from '../../api/errors';
import { fetchSpotsNearby } from '../../api/spots';
import type { NearbySpot } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/icons';
import { MetaLine, StatusText } from '../../components/StatusText';
import { roundCoordinate } from '../../lib/geo';
import type { LatLng } from '../../map/types';
import { withNext } from '../../app/session/nextPath';
import { useSession } from '../../app/session/session';
import { newMemoryPath } from '../memory/memoryLabels';
import { LocationLabel } from '../../components/LocationLabel';
import { SPOT_TYPE_META } from '../../lib/spotLabels';

const SAME_PLACE_RADIUS_KM = 0.05;

const GUIDE_LINK_CLASS = 'inline-flex min-h-11 items-center text-accent underline underline-offset-2';

const MEMORY_LINK_CLASS = `${buttonClassName('secondary')} w-full`;

type MapOpenMenuProps = {
  position: LatLng;
  onClose: () => void;
  onCandidates: (spots: NearbySpot[]) => void;
};

export function MapOpenMenu({ position, onClose, onCandidates }: MapOpenMenuProps) {
  const session = useSession();
  const location = useLocation();
  const bodyRef = useRef<HTMLDivElement>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    bodyRef.current?.querySelector<HTMLElement>('a, button:not([disabled])')?.focus({ preventScroll: true });
  }, []);

  useEffect(() => () => controllerRef.current?.abort(), []);

  async function search() {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setSearching(true);
    setError(null);
    const lat = roundCoordinate(position.lat);
    const lng = roundCoordinate(position.lng);
    try {
      const page = await fetchSpotsNearby({ lat, lng, radiusKm: SAME_PLACE_RADIUS_KM }, controller.signal);
      if (controller.signal.aborted) return;
      if (page.content.length > 0) {
        onCandidates(page.content);
        return;
      }
      navigate(`/bakjis/new?lat=${lat}&lng=${lng}&next=basecamp`);
    } catch (caught) {
      if (controller.signal.aborted) return;
      setSearching(false);
      setError(toUserMessage(caught));
    }
  }

  let body: ReactNode;
  if (session.status === 'loading') {
    body = (
      <Button fullWidth disabled>
        여기서 베이스캠프 열기
      </Button>
    );
  } else if (session.status === 'anonymous' || session.me === null) {
    body = (
      <>
        <p className="text-sm text-ink">로그인하면 베이스캠프를 열거나 기록을 남길 수 있어요.</p>
        <Link to={withNext('/login', `${location.pathname}${location.search}`)} className={GUIDE_LINK_CLASS}>
          로그인
        </Link>
      </>
    );
  } else if (session.me.status === 'UNVERIFIED') {
    body = (
      <>
        <p className="text-sm text-ink">
          이메일 인증을 마치면 기록을 남길 수 있어요. 베이스캠프는 본인확인까지 마쳐야 열 수 있어요.
        </p>
        <Link to="/verify-email" className={GUIDE_LINK_CLASS}>
          이메일 인증하기
        </Link>
      </>
    );
  } else if (session.me.trustLevel < 1) {
    body = (
      <>
        <p className="text-sm text-ink">베이스캠프는 본인확인을 마친 성인만 열 수 있어요.</p>
        <Link to="/identity-verification" className={GUIDE_LINK_CLASS}>
          본인확인하기
        </Link>
        <MemoryLink position={position} />
      </>
    );
  } else {
    body = (
      <>
        <Button fullWidth loading={searching} onClick={search}>
          여기서 베이스캠프 열기
        </Button>
        <p className="text-sm text-ink-muted">50m 안의 장소 중에서 골라요. 장소가 없으면 박지로 먼저 제보해요.</p>
        {error && (
          <p role="alert" className="flex items-start gap-1 text-sm text-danger">
            <Icon name="alert" size={16} className="mt-0.5 shrink-0" />
            <span>{error}</span>
          </p>
        )}
        <MemoryLink position={position} />
      </>
    );
  }

  // 오버레이를 감싼 SDK 요소가 줄바꿈을 막아 두므로 메뉴 안에서 다시 줄바꿈을 켠다(whitespace-normal).
  return (
    <div
      role="dialog"
      aria-label="이 위치에서 할 일"
      onKeyDown={(event) => {
        if (event.key === 'Escape') onClose();
      }}
      className="m-1 flex w-64 break-keep whitespace-normal max-w-[calc(100vw-2rem)] flex-col gap-2 rounded-sheet bg-surface py-2 pr-1 pl-4 text-ink shadow-raise"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex min-w-0 flex-col gap-1 pt-1">
          <p className="text-sm font-semibold text-ink">이 위치에서</p>
          <LocationLabel position={position} />
        </div>
        <button
          type="button"
          aria-label="닫기"
          onClick={onClose}
          className="flex size-11 shrink-0 items-center justify-center rounded-control text-ink hover:bg-fill"
        >
          <Icon name="close" size={20} />
        </button>
      </div>
      <div ref={bodyRef} className="flex flex-col gap-2 pr-2 pb-1">
        {body}
      </div>
    </div>
  );
}

function MemoryLink({ position }: { position: LatLng }) {
  return (
    <Link to={newMemoryPath(position)} className={MEMORY_LINK_CLASS}>
      여기에 기록 남기기
    </Link>
  );
}

type SpotPickDialogProps = {
  spots: NearbySpot[];
  onClose: () => void;
};

export function SpotPickDialog({ spots, onClose }: SpotPickDialogProps) {
  return (
    <Dialog title="어느 장소에서 열까요?" onClose={onClose}>
      <p className="text-sm text-ink-muted">누른 곳에서 50m 안에 있는 장소예요.</p>
      <ul className="mt-2 flex flex-col gap-0.5">
        {spots.map((spot) => (
          <li key={spot.spotId}>
            {spot.parkWarning ? (
              <div aria-disabled="true" className="flex min-h-11 flex-col gap-1 rounded-row bg-fill px-3 py-2.5">
                <SpotPickLabel spot={spot} />
                <p className="text-sm text-warning">공원 경계 경고가 있는 박지라 베이스캠프를 열 수 없어요.</p>
              </div>
            ) : (
              <Link
                to={`/basecamps/new?spotId=${spot.spotId}`}
                onClick={onClose}
                className="flex min-h-11 flex-col gap-1 rounded-row px-3 py-2.5 hover:bg-fill"
              >
                <SpotPickLabel spot={spot} />
              </Link>
            )}
          </li>
        ))}
      </ul>
    </Dialog>
  );
}

function SpotPickLabel({ spot }: { spot: NearbySpot }) {
  const meta = SPOT_TYPE_META[spot.type];
  return (
    <>
      <span className="flex items-center justify-between gap-3">
        <span className="min-w-0 break-words text-base text-ink">{spot.name}</span>
        <span className="shrink-0 font-mono text-sm tabular-nums text-ink-muted">
          {Math.round(spot.distanceKm * 1000)}m
        </span>
      </span>
      <MetaLine
        items={[
          meta.label,
          spot.parkWarning && (
            <StatusText tone="warning" icon="alert">
              공원 경계 경고
            </StatusText>
          ),
          spot.closedNow && (
            <StatusText tone="closed" icon="closed">
              휴장 중
            </StatusText>
          ),
        ]}
      />
    </>
  );
}
