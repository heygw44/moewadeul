import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchSpotWeather } from '../../api/spots';
import type { Weather } from '../../api/types';
import { useSpotWeather } from './useSpotWeather';
import type { SpotWeatherState } from './useSpotWeather';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

vi.mock('../../api/spots', () => ({ fetchSpotWeather: vi.fn() }));
const fetchMock = vi.mocked(fetchSpotWeather);

const SUN = { sunrise: '06:00', sunset: '18:00' } as Weather['sun'];
const WEATHER = { source: '기상청', shortTerm: [], midTerm: [], sun: SUN } as Weather;
const WEATHER_WITHOUT_SUN = { ...WEATHER, sun: null } as Weather;

describe('useSpotWeather', () => {
  let root: Root;
  let state: SpotWeatherState;

  function Probe({ spotId }: { spotId: number }) {
    state = useSpotWeather(spotId);
    return null;
  }

  const show = (spotId: number) => act(async () => root.render(<Probe spotId={spotId} />));
  const advance = (ms: number) => act(async () => vi.advanceTimersByTime(ms));

  beforeEach(() => {
    vi.useFakeTimers();
    fetchMock.mockReset();
    root = createRoot(document.createElement('div'));
  });

  afterEach(() => {
    act(() => root.unmount());
    vi.useRealTimers();
  });

  it('첫 응답에 날씨와 출몰시각이 모두 있으면 다시 묻지 않는다', async () => {
    fetchMock.mockResolvedValue(WEATHER);

    await show(7);
    await advance(30_000);

    expect(state).toEqual({ status: 'loaded', weather: WEATHER });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('첫 응답이 비면 바로 비어 있는 상태를 보여 주고, 3초 뒤 다시 물어 값이 오면 바꾼다', async () => {
    fetchMock.mockResolvedValueOnce(null).mockResolvedValueOnce(WEATHER);

    await show(7);
    expect(state).toEqual({ status: 'loaded', weather: null });
    await advance(2_999);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await advance(1);

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(state).toEqual({ status: 'loaded', weather: WEATHER });
  });

  it('출몰시각만 비어 오면 날씨를 바로 보여 주고, 다시 물어 출몰시각이 오면 바꾼 뒤 더 묻지 않는다', async () => {
    fetchMock.mockResolvedValueOnce(WEATHER_WITHOUT_SUN).mockResolvedValueOnce(WEATHER);

    await show(7);
    expect(state).toEqual({ status: 'loaded', weather: WEATHER_WITHOUT_SUN });
    await advance(3_000);
    await advance(30_000);

    expect(state).toEqual({ status: 'loaded', weather: WEATHER });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('다시 물어도 비어 있으면 첫 다시 묻기로부터 6초 뒤 한 번 더 묻고, 그 뒤로는 묻지 않는다', async () => {
    fetchMock.mockResolvedValue(null);

    await show(7);
    await advance(3_000);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    await advance(5_999);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    await advance(1);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    await advance(60_000);

    expect(state).toEqual({ status: 'loaded', weather: null });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('두 번째 다시 묻기에서 값이 오면 바꾼다', async () => {
    fetchMock.mockResolvedValueOnce(null).mockResolvedValueOnce(WEATHER_WITHOUT_SUN).mockResolvedValueOnce(WEATHER);

    await show(7);
    await advance(3_000);
    expect(state).toEqual({ status: 'loaded', weather: WEATHER_WITHOUT_SUN });
    await advance(6_000);

    expect(state).toEqual({ status: 'loaded', weather: WEATHER });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('다시 물었을 때 날씨가 null로 오면 이미 보여 준 날씨를 지우지 않는다', async () => {
    fetchMock.mockResolvedValueOnce(WEATHER_WITHOUT_SUN).mockResolvedValue(null);

    await show(7);
    await advance(3_000);
    await advance(6_000);

    expect(state).toEqual({ status: 'loaded', weather: WEATHER_WITHOUT_SUN });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('다시 묻기가 실패하면 아무것도 바꾸지 않고 더 묻지 않는다', async () => {
    fetchMock.mockResolvedValueOnce(WEATHER_WITHOUT_SUN).mockRejectedValueOnce(new Error('network'));

    await show(7);
    await advance(3_000);
    await advance(60_000);

    expect(state).toEqual({ status: 'loaded', weather: WEATHER_WITHOUT_SUN });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('첫 요청이 실패하면 다시 묻지 않는다', async () => {
    fetchMock.mockRejectedValue(new Error('network'));

    await show(7);
    await advance(30_000);

    expect(state).toEqual({ status: 'loaded', weather: null });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('기다리는 동안 장소가 바뀌면 이전 장소는 다시 묻지 않고, 새 장소의 결과만 보여 준다', async () => {
    fetchMock.mockResolvedValueOnce(null).mockResolvedValueOnce(WEATHER);

    await show(7);
    await show(8);
    await advance(30_000);

    expect(fetchMock.mock.calls.map(([spotId]) => spotId)).toEqual([7, 8]);
    expect(state).toEqual({ status: 'loaded', weather: WEATHER });
  });

  it('다시 묻는 요청이 오는 도중 장소가 바뀌면 그 요청을 끊고 늦게 온 값을 쓰지 않는다', async () => {
    let resolveRetry: (weather: Weather | null) => void = () => undefined;
    fetchMock
      .mockResolvedValueOnce(null)
      .mockImplementationOnce(() => new Promise((resolve) => (resolveRetry = resolve)))
      .mockResolvedValueOnce(null);

    await show(7);
    await advance(3_000);
    const retrySignal = fetchMock.mock.calls[1]?.[1];
    await show(8);
    await act(async () => resolveRetry(WEATHER));

    expect(retrySignal?.aborted).toBe(true);
    expect(state).toEqual({ status: 'loaded', weather: null });
  });

  it('화면에서 사라지면 기다리던 다시 묻기를 보내지 않는다', async () => {
    fetchMock.mockResolvedValue(null);

    await show(7);
    await act(async () => root.unmount());
    await advance(30_000);

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
