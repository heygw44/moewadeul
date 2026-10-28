const SDK_URL = 'https://dapi.kakao.com/v2/maps/sdk.js';
const LOAD_TIMEOUT_MS = 15_000;

export type MapLoadErrorReason = 'NO_KEY' | 'LOAD_FAILED';

const MESSAGES: Record<MapLoadErrorReason, string> = {
  NO_KEY: '지금은 지도를 볼 수 없어요.',
  LOAD_FAILED: '지도를 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.',
};

export class MapLoadError extends Error {
  readonly reason: MapLoadErrorReason;

  constructor(reason: MapLoadErrorReason) {
    super(MESSAGES[reason]);
    this.name = 'MapLoadError';
    this.reason = reason;
  }
}

let pending: Promise<void> | null = null;

export function loadKakaoMaps(): Promise<void> {
  pending ??= startLoading().catch((error: unknown) => {
    pending = null;
    throw error;
  });
  return pending;
}

function startLoading(): Promise<void> {
  const key = import.meta.env.VITE_KAKAO_JS_KEY?.trim();
  if (!key) {
    return Promise.reject(new MapLoadError('NO_KEY'));
  }

  return new Promise<void>((resolve, reject) => {
    let script: HTMLScriptElement | null = null;
    let settled = false;

    const timer = window.setTimeout(fail, LOAD_TIMEOUT_MS);

    function succeed() {
      if (settled) return;
      settled = true;
      window.clearTimeout(timer);
      resolve();
    }

    function fail() {
      if (settled) return;
      settled = true;
      window.clearTimeout(timer);
      script?.remove();
      reject(new MapLoadError('LOAD_FAILED'));
    }

    // autoload=false로 불러오면 SDK 나머지 파일은 kakao.maps.load를 불러야 받는다.
    function finishWithSdk() {
      const maps = window.kakao?.maps;
      if (!maps) {
        fail();
        return;
      }
      maps.load(succeed);
    }

    if (window.kakao?.maps) {
      finishWithSdk();
      return;
    }

    script = document.createElement('script');
    script.src = `${SDK_URL}?appkey=${encodeURIComponent(key)}&libraries=services&autoload=false`;
    script.async = true;
    script.addEventListener('load', finishWithSdk);
    script.addEventListener('error', fail);
    document.head.append(script);
  });
}
