import { useCallback, useEffect, useRef, useState } from 'react';
import { createMapView } from '../../map/KakaoMap';
import { MapLoadError } from '../../map/kakaoLoader';
import type { MapLoadErrorReason } from '../../map/kakaoLoader';
import type { MapView } from '../../map/types';
import { readMapScreen, rememberMapScreen } from './mapScreenMemory';

export type MapFailure = { reason: MapLoadErrorReason; message: string };

const INITIAL_VIEW = { center: { lat: 36.35, lng: 127.85 }, level: 12 };

export function useMapView() {
  const hostRef = useRef<HTMLDivElement>(null);
  const [map, setMap] = useState<MapView | null>(null);
  const [failure, setFailure] = useState<MapFailure | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const element = document.createElement('div');
    // 카카오 지도의 방향키 이동은 지도를 그린 이 칸이 포커스를 가져야 동작한다.
    element.className = 'absolute inset-0 focus-visible:-outline-offset-2';
    element.tabIndex = 0;
    element.setAttribute('role', 'application');
    element.setAttribute('aria-label', '지도');
    element.setAttribute('aria-describedby', 'map-keyboard-hint');
    host.appendChild(element);

    let cancelled = false;
    let created: MapView | null = null;
    createMapView(element, readMapScreen().view ?? INITIAL_VIEW).then(
      (view) => {
        if (cancelled) {
          view.destroy();
          return;
        }
        created = view;
        setMap(view);
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
      setMap(null);
    };
  }, [attempt]);

  useEffect(() => {
    const host = hostRef.current;
    if (!map || !host) return;
    const observer = new ResizeObserver(() => map.relayout());
    observer.observe(host);
    return () => observer.disconnect();
  }, [map]);

  useEffect(() => {
    if (!map) return;
    return map.onIdle(() => rememberMapScreen({ view: { center: map.getCenter(), level: map.getLevel() } }));
  }, [map]);

  const retry = useCallback(() => {
    setFailure(null);
    setAttempt((value) => value + 1);
  }, []);

  return { hostRef, map, failure, retry };
}
