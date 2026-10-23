import { useCallback, useEffect, useState } from 'react';

// 서버가 잠시 막았을 때(429의 Retry-After, 로그인 잠금, 코드 재발송 간격) 남은 초를 보여 준다. 0이 되면 멈춘다.
// 숨은 탭에서는 브라우저가 타이머를 멈추거나 늦추므로, 틱마다 1을 빼지 않고 끝나는 시각에서 지금 시각을 빼서 센다.
// 그래서 탭이 다시 보이면 그동안 흐른 시간이 바로 반영된다.
// 기기가 잠든 동안에도 흘러가야 하므로 performance.now() 대신 Date.now()를 쓴다.
export function useRetryCountdown(): { secondsLeft: number; start(seconds: number): void } {
  const [endsAt, setEndsAt] = useState<number | null>(null);
  const [secondsLeft, setSecondsLeft] = useState(0);

  useEffect(() => {
    if (endsAt === null) return;
    let timer: number | undefined;

    const tick = () => {
      window.clearTimeout(timer);
      const remainingMs = endsAt - Date.now();
      if (remainingMs <= 0) {
        setSecondsLeft(0);
        setEndsAt(null);
        return;
      }
      setSecondsLeft(Math.ceil(remainingMs / 1000));
      // 다음 초가 바뀌는 순간에 깨어나도록 맞춘다. 타이머가 조금 일찍 깨어나도 다음 틱에서 바로잡힌다.
      timer = window.setTimeout(tick, remainingMs % 1000 || 1000);
    };

    timer = window.setTimeout(tick, (endsAt - Date.now()) % 1000 || 1000);
    document.addEventListener('visibilitychange', tick);
    return () => {
      window.clearTimeout(timer);
      document.removeEventListener('visibilitychange', tick);
    };
  }, [endsAt]);

  const start = useCallback((seconds: number) => {
    const whole = Math.max(0, Math.ceil(seconds));
    setSecondsLeft(whole);
    setEndsAt(whole > 0 ? Date.now() + whole * 1000 : null);
  }, []);

  return { secondsLeft, start };
}
