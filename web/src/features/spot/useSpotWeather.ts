import { useEffect, useState } from 'react';
import { fetchSpotWeather } from '../../api/spots';
import type { Weather } from '../../api/types';

// 날씨를 받는 중이면 loading, 받았으면 loaded다. 기상청이 실패했거나 요청이 실패하면 weather가 null이다.
// 화면은 두 실패를 구분하지 않고 "날씨 정보를 불러오지 못했어요"를 보여 준다.
export type SpotWeatherState = { status: 'loading' } | { status: 'loaded'; weather: Weather | null };

type WeatherResult = { spotId: number; weather: Weather | null };

// 장소 상세와 같은 때에 날씨 요청을 따로 보낸다. 날씨는 외부 API를 기다려서 느릴 수 있으므로,
// 상세의 근거(공원 경계, 출처, 기준일)는 날씨를 기다리지 않고 먼저 그린다.
// 어느 장소의 결과인지 함께 기억해서, 장소를 바꾸면 이전 장소의 날씨를 곧바로 버린다.
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
