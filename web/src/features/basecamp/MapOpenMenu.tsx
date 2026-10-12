import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { toUserMessage } from '../../api/errors';
import { fetchSpotsNearby } from '../../api/spots';
import type { NearbySpot } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/icons';
import type { LatLng } from '../../map/types';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { LocationLabel } from '../spot/LocationLabel';
import { SPOT_TYPE_META } from '../spot/spotLabels';

// 누른 곳에서 이 반경 안의 장소를 같은 곳으로 본다. 박지를 제보할 때 서버가 중복 후보를 찾는 반경과 같은 50m다.
const SAME_PLACE_RADIUS_KM = 0.05;

const GUIDE_LINK_CLASS = 'inline-flex min-h-11 items-center text-forest underline underline-offset-2';

// 제보 폼이 서버에 보내는 좌표와 같은 정밀도로 줄인다.
function roundCoordinate(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}

type MapOpenMenuProps = {
  position: LatLng;
  onClose: () => void;
  // 근처에 장소가 있으면 메뉴를 닫고 후보 목록을 띄우도록 호출하는 쪽에 넘긴다.
  onCandidates: (spots: NearbySpot[]) => void;
};

// 지도를 우클릭하거나 길게 누른 곳에 뜨는 메뉴다. 열 자격이 없으면 무엇을 하면 되는지 안내만 한다.
// 화면의 자격 안내는 편의일 뿐이고, 열기 폼과 서버가 다시 판정한다.
export function MapOpenMenu({ position, onClose, onCandidates }: MapOpenMenuProps) {
  const session = useSession();
  const location = useLocation();
  const bodyRef = useRef<HTMLDivElement>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 메뉴가 뜨면 키보드로 바로 고를 수 있게 닫기 버튼이 아니라 본문의 첫 버튼·링크로 포커스를 옮긴다.
  // 지도가 움직이지 않도록 스크롤은 막는다.
  useEffect(() => {
    bodyRef.current?.querySelector<HTMLElement>('a, button:not([disabled])')?.focus({ preventScroll: true });
  }, []);

  // 찾는 중에 메뉴를 닫으면 응답을 기다리지 않는다.
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
      // 근처에 장소가 없으면 그 자리를 박지로 먼저 제보하고, 등록되면 새 장소에서 베이스캠프를 연다.
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
        <p className="text-sm text-ink">로그인하면 베이스캠프를 열 수 있어요.</p>
        <Link to={withNext('/login', `${location.pathname}${location.search}`)} className={GUIDE_LINK_CLASS}>
          로그인하러 가기
        </Link>
      </>
    );
  } else if (session.me.status === 'UNVERIFIED') {
    body = (
      <>
        <p className="text-sm text-ink">이메일 인증을 마치고 본인확인을 하면 베이스캠프를 열 수 있어요.</p>
        <Link to="/verify-email" className={GUIDE_LINK_CLASS}>
          이메일 인증하러 가기
        </Link>
      </>
    );
  } else if (session.me.trustLevel < 1) {
    body = (
      <>
        <p className="text-sm text-ink">함께 가는 사람의 안전을 위해 본인확인을 마친 성인만 베이스캠프를 열 수 있어요.</p>
        <Link to="/identity-verification" className={GUIDE_LINK_CLASS}>
          본인확인하러 가기
        </Link>
      </>
    );
  } else {
    body = (
      <>
        <Button fullWidth loading={searching} onClick={search}>
          여기서 베이스캠프 열기
        </Button>
        <p className="text-sm text-ink-muted">50m 안의 장소에서 골라요. 장소가 없으면 박지로 먼저 제보해요.</p>
        {error && (
          <p role="alert" className="flex items-start gap-1 text-sm text-danger">
            <Icon name="alert" size={16} className="mt-0.5 shrink-0" />
            <span>{error}</span>
          </p>
        )}
      </>
    );
  }

  // 오버레이를 감싼 SDK 요소가 줄바꿈을 막아 두므로 메뉴 안에서 다시 줄바꿈을 켠다(whitespace-normal).
  return (
    <div
      role="dialog"
      aria-label="이 위치에서 베이스캠프 열기"
      onKeyDown={(event) => {
        if (event.key === 'Escape') onClose();
      }}
      className="m-1 flex w-64 break-keep whitespace-normal max-w-[calc(100vw-2rem)] flex-col gap-2 rounded-control border border-contour bg-card py-2 pr-1 pl-3 text-ink shadow-raise"
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
          className="flex size-11 shrink-0 items-center justify-center rounded-control text-ink hover:bg-paper-deep"
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

type SpotPickDialogProps = {
  spots: NearbySpot[];
  onClose: () => void;
};

// 누른 곳 근처의 장소 중에서 베이스캠프를 열 곳을 고른다. 공원 경계 경고가 있는 박지는 고를 수 없게 막고 이유를 적는다.
export function SpotPickDialog({ spots, onClose }: SpotPickDialogProps) {
  return (
    <Dialog title="어느 장소에서 열까요?" onClose={onClose}>
      <p className="text-sm text-ink-muted">누른 곳에서 50m 안에 있는 장소예요.</p>
      <ul className="mt-2 flex flex-col">
        {spots.map((spot) => (
          <li key={spot.spotId} className="border-t border-contour first:border-t-0">
            {spot.parkWarning ? (
              <div aria-disabled="true" className="flex min-h-11 flex-col gap-1 py-2">
                <SpotPickLabel spot={spot} />
                <p className="text-sm text-warning">공원 경계 안일 가능성이 있는 박지라서 베이스캠프를 열 수 없어요.</p>
              </div>
            ) : (
              <Link
                to={`/basecamps/new?spotId=${spot.spotId}`}
                onClick={onClose}
                className="flex min-h-11 flex-col gap-1 py-2 hover:bg-paper-deep"
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
      <span className="flex flex-wrap gap-1">
        <Badge tone="neutral">{meta.label}</Badge>
        {spot.parkWarning && (
          <Badge tone="warning" icon="alert">
            공원 경계 경고
          </Badge>
        )}
        {spot.closedNow && (
          <Badge tone="closed" icon="closed">
            휴장
          </Badge>
        )}
      </span>
    </>
  );
}
