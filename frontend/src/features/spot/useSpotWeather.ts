import { useEffect, useState } from 'react';
import { fetchSpotWeather } from '../../api/spots';
import type { Weather } from '../../api/types';

export type SpotWeatherState = { status: 'loading' } | { status: 'loaded'; weather: Weather | null };

type WeatherResult = { spotId: number; weather: Weather | null };

// 서버는 제한 시간을 넘긴 호출을 버리지 않고 끝까지 이어 가서 값을 보관한다. 첫 응답이 비었으면 그 값이 준비될 즈음 한 번 더 묻는다.
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
