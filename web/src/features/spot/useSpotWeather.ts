import { useEffect, useState } from 'react';
import { fetchSpotWeather } from '../../api/spots';
import type { Weather } from '../../api/types';

export type SpotWeatherState = { status: 'loading' } | { status: 'loaded'; weather: Weather | null };

type WeatherResult = { spotId: number; weather: Weather | null };

export function useSpotWeather(spotId: number): SpotWeatherState {
  const [result, setResult] = useState<WeatherResult | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchSpotWeather(spotId, controller.signal).then(
      (weather) => {
        if (!controller.signal.aborted) setResult({ spotId, weather });
      },
      () => {
        if (!controller.signal.aborted) setResult({ spotId, weather: null });
      },
    );
    return () => controller.abort();
  }, [spotId]);

  if (result === null || result.spotId !== spotId) return { status: 'loading' };
  return { status: 'loaded', weather: result.weather };
}
