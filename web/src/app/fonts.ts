// 글꼴(Pretendard) CSS는 첫 화면을 그리는 데 필요하지 않아서 따로 받는다. 글꼴 파일이 늦게 도착하면 그동안 시스템 글꼴로 보이다가 바뀐다(font-display: swap).
// 동적 import라서 Vite가 별도 CSS 파일로 나누고, 그 안의 글꼴 조각은 해시가 붙은 /assets 아래에 놓여 오래 캐시된다.
let loading: Promise<unknown> | null = null;

export function loadFonts(): Promise<unknown> {
  loading ??= import('../styles/fonts/pretendard/pretendardvariable-dynamic-subset.css').catch(() => {
    // 받지 못하면 다음에 다시 시도할 수 있게 비운다. 글꼴이 없어도 시스템 글꼴로 화면은 쓸 수 있다.
    loading = null;
  });
  return loading;
}

// 지도 화면을 처음 여는 방문은 글꼴 조각 9개(233KB)가 지도 SDK 본체(34KB)와 대역폭을 나눠서 첫 장소 표시를 늦춘다.
// 그래서 SDK가 준비되거나 실패한 뒤에 글꼴을 받는다. 지도가 아닌 화면은 바로 받는다.
export function loadFontsForFirstScreen(
  opensMap: boolean,
  loadSdk: () => Promise<unknown>,
  load: () => Promise<unknown> = loadFonts,
): Promise<unknown> {
  return opensMap ? loadSdk().then(load, load) : load();
}
