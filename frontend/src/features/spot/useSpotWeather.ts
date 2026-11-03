import { useEffect, useState } from 'react';
import { fetchSpotWeather } from '../../api/spots';
import type { Weather } from '../../api/types';

export type SpotWeatherState = { status: 'loading' } | { status: 'loaded'; weather: Weather | null };

type WeatherResult = { spotId: number; weather: Weather | null };

// 기다릴 자리를 얻지 못한 요청은 공유 호출이 아직 도는 중이라 날씨가 null이다. 그 호출은 곧 값을 보관하므로 조금 뒤에 한 번 더 물으면 값을 받는다.
const RETRY_DELAY_MS = 3_000;

export function useSpotWeather(spotId: number): SpotWeatherState {
  const [result, setResult] = useState<WeatherResult | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    let retryTimer: ReturnType<typeof setTimeout> | undefined;

    const retry = () => {
      fetchSpotWeather(spotId, controller.signal).then(
        (weather) => {
          if (!controller.signal.aborted && weather !== null) setResult({ spotId, weather });
        },
        () => undefined,
      );
    };

    fetchSpotWeather(spotId, controller.signal).then(
      (weather) => {
        if (controller.signal.aborted) return;
        setResult({ spotId, weather });
        if (weather === null) retryTimer = setTimeout(retry, RETRY_DELAY_MS);
      },
      () => {
        if (!controller.signal.aborted) setResult({ spotId, weather: null });
      },
    );
    return () => {
      controller.abort();
      clearTimeout(retryTimer);
    };
  }, [spotId]);

  if (result === null || result.spotId !== spotId) return { status: 'loading' };
  return { status: 'loaded', weather: result.weather };
}
