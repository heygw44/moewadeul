import { useState } from 'react';
import { Icon } from '../../components/icons';
import { readStored, writeStored } from '../../lib/storage';

const SEEN_KEY = 'moewadeul.mapSafetyNoticeSeen';

export function MapSafetyNotice() {
  const [seen, setSeen] = useState(() => readStored(SEEN_KEY) === '1');

  if (seen) return null;

  function confirm() {
    writeStored(SEEN_KEY, '1');
    setSeen(true);
  }

  return (
    <div role="note" className="pointer-events-auto flex max-w-md gap-3 rounded-sheet bg-info-soft p-4 shadow-raise">
      <Icon name="info" size={20} className="mt-0.5 shrink-0 text-info" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="font-bold text-info">야영하기 전에 알아 두세요</p>
        <p className="text-sm text-ink">
          공원 경계는 참고 자료예요. 정확한 경계는 공식 고시 도면을 확인해 주세요. 자연공원 안에서는 지정된 야영장 밖의
          야영과 취사가 금지돼 있어요. 박지는 회원이 제보한 곳이에요. 야영해도 되는지는 관할 기관에 직접 확인해 주세요.
        </p>
        <button
          type="button"
          onClick={confirm}
          className="mt-1 inline-flex min-h-11 items-center self-start rounded-control bg-info px-4 text-sm font-semibold text-white hover:bg-info-strong"
        >
          확인했어요
        </button>
      </div>
    </div>
  );
}
