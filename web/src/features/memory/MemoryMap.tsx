import { useEffect, useRef, useState } from 'react';
import { Button } from '../../components/Button';
import { Notice } from '../../components/Notice';
import { MapLoadError } from '../../map/kakaoLoader';
import { createMemoryMapView } from '../../map/MemoryMapView';
import type { MemoryMapView, MemoryPinData } from '../../map/types';

type MapFailure = { reason: MapLoadError['reason']; message: string };

// 추억 기록을 사진 핀으로 보여 주는 지도다. 지도를 못 불러오면 지도 자리에 안내만 두고, 아래 격자는 그대로 쓸 수 있다.
// pins는 값이 바뀔 때만 새 배열을 넘겨야 한다(useMemo). 새 배열이 올 때마다 핀을 다시 그리고 지도를 맞춘다.
export function MemoryMap({
  pins,
  selectedId,
  onSelect,
  heightClass = 'h-72 sm:h-96',
}: {
  pins: MemoryPinData[];
  selectedId: number | null;
  onSelect: (id: number) => void;
  // 지도 높이 클래스다. 기록 상세 옆 칸처럼 작은 지도로 쓸 때 줄인다.
  heightClass?: string;
}) {
  const hostRef = useRef<HTMLDivElement>(null);
  const [view, setView] = useState<MemoryMapView | null>(null);
  const [failure, setFailure] = useState<MapFailure | null>(null);
  const [attempt, setAttempt] = useState(0);
  const onSelectRef = useRef(onSelect);
  useEffect(() => {
    onSelectRef.current = onSelect;
  });

  // 지도는 시도할 때마다 새 요소에 만든다. 개발 모드에서 효과가 두 번 실행돼도 한 요소에 지도가 겹쳐 생기지 않게 하기 위해서다.
  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const element = document.createElement('div');
    element.className = 'absolute inset-0';
    host.appendChild(element);

    let cancelled = false;
    let created: MemoryMapView | null = null;
    createMemoryMapView(element).then(
      (next) => {
        if (cancelled) {
          next.destroy();
          return;
        }
        created = next;
        next.onPinClick((id) => onSelectRef.current(id));
        setFailure(null);
        setView(next);
      },
      (error: unknown) => {
        if (cancelled) return;
        const loadError = error instanceof MapLoadError ? error : new MapLoadError('LOAD_FAILED');
        setFailure({ reason: loadError.reason, message: loadError.message });
      },
    );

    return () => {
      cancelled = true;
      created?.destroy();
      element.remove();
      setView(null);
    };
  }, [attempt]);

  // 지도 칸의 크기가 바뀌면(화면 회전, 폭 변경) 지도에 알린다.
  useEffect(() => {
    const host = hostRef.current;
    if (!view || !host) return;
    const observer = new ResizeObserver(() => view.relayout());
    observer.observe(host);
    return () => observer.disconnect();
  }, [view]);

  useEffect(() => {
    if (!view) return;
    view.showPins(pins);
    view.fitTo();
  }, [view, pins]);

  useEffect(() => {
    view?.setSelected(selectedId);
  }, [view, selectedId, pins]);

  if (failure) {
    return (
      <div className="flex flex-col gap-3">
        <Notice tone="info" title="지도를 볼 수 없어요">
          {failure.reason === 'NO_KEY' ? (
            <p>아래 목록에서 기록을 열어 볼 수 있어요.</p>
          ) : (
            <p>아래 목록에서 기록을 열어 볼 수 있어요.</p>
          )}
        </Notice>
        {failure.reason !== 'NO_KEY' && (
          <div>
            <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
              지도 다시 불러오기
            </Button>
          </div>
        )}
      </div>
    );
  }

  return (
    <div
      ref={hostRef}
      role="region"
      aria-label="기록 지도"
      className={`relative w-full overflow-hidden rounded-sheet bg-fill ${heightClass}`}
    />
  );
}
