import { useEffect, useState } from 'react';
import { fetchSpotWeather } from '../../api/spots';
import type { Weather } from '../../api/types';

export type SpotWeatherState = { status: 'loading' } | { status: 'loaded'; weather: Weather | null };

type WeatherResult = { spotId: number; weather: Weather | null };

// 같은 값을 받는 호출이 몰려 기다릴 자리를 얻지 못한 요청은 날씨나 출몰시각이 null로 온다. 그 호출은 곧 값을 보관하므로, 비어 온 동안만 늘려 가며 다시 묻는다.
const RETRY_DELAYS_MS = [3_000, 6_000];

const isIncomplete = (weather: Weather | null) => weather === null || weather.sun === null;

export function useSpotWeather(spotId: number): SpotWeatherState {
  const [result, setResult] = useState<WeatherResult | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    let retryTimer: ReturnType<typeof setTimeout> | undefined;

    const ask = (retriesDone: number) => {
      fetchSpotWeather(spotId, controller.signal).then(
        (weather) => {
          if (controller.signal.aborted) return;
          if (retriesDone === 0 || weather !== null) setResult({ spotId, weather });
          const delay = RETRY_DELAYS_MS[retriesDone];
          if (delay !== undefined && isIncomplete(weather)) retryTimer = setTimeout(() => ask(retriesDone + 1), delay);
        },
        () => {
          if (retriesDone === 0 && !controller.signal.aborted) setResult({ spotId, weather: null });
        },
      );
    };
    ask(0);

    return () => {
      controller.abort();
      clearTimeout(retryTimer);
    };
  }, [spotId]);

  if (result === null || result.spotId !== spotId) return { status: 'loading' };
  return { status: 'loaded', weather: result.weather };
}
