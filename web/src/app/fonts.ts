let loading: Promise<unknown> | null = null;

export function loadFonts(): Promise<unknown> {
  loading ??= import('../styles/fonts/pretendard/pretendardvariable-dynamic-subset.css').catch(() => {
    loading = null;
  });
  return loading;
}

export function loadFontsForFirstScreen(
  opensMap: boolean,
  loadSdk: () => Promise<unknown>,
  load: () => Promise<unknown> = loadFonts,
): Promise<unknown> {
  return opensMap ? loadSdk().then(load, load) : load();
}
