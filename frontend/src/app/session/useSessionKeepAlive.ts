import { useEffect, useRef } from 'react';

export const KEEP_ALIVE_INTERVAL_MS = 10 * 60 * 1000;

const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'scroll'] as const;

export function useSessionKeepAlive(enabled: boolean, check: () => Promise<unknown>): void {
  const checkRef = useRef(check);

  useEffect(() => {
    checkRef.current = check;
  });

  useEffect(() => {
    if (!enabled) return;

    let active = false;
    let lastCheckAt = Date.now();
    let timer: ReturnType<typeof setInterval> | null = null;

    const run = () => {
      lastCheckAt = Date.now();
      active = false;
      checkRef.current().catch(() => undefined);
    };
    const start = () => {
      timer ??= setInterval(() => {
        if (active) run();
      }, KEEP_ALIVE_INTERVAL_MS);
    };
    const stop = () => {
      if (timer !== null) clearInterval(timer);
      timer = null;
    };
    const markActive = () => {
      active = true;
    };
    const onVisibilityChange = () => {
      if (document.visibilityState !== 'visible') {
        stop();
        return;
      }
      if (Date.now() - lastCheckAt >= KEEP_ALIVE_INTERVAL_MS) run();
      start();
    };

    if (document.visibilityState === 'visible') start();
    for (const type of ACTIVITY_EVENTS) window.addEventListener(type, markActive, { capture: true, passive: true });
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      stop();
      for (const type of ACTIVITY_EVENTS) window.removeEventListener(type, markActive, { capture: true });
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, [enabled]);
}
