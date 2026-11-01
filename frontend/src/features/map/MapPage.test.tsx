import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SessionProvider } from '../../app/session/session';
import { createMapView } from '../../map/KakaoMap';
import { MapLoadError } from '../../map/kakaoLoader';
import { searchRegions } from '../../map/regionSearch';
import type { RegionCandidate } from '../../map/regionSearch';
import { MapPage } from './MapPage';
import { forgetMapScreen } from './mapScreenMemory';
import { createFakeMapView } from './fakeMapView';
import type { FakeMapView } from './fakeMapView';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

vi.mock('../../map/KakaoMap', () => ({ createMapView: vi.fn() }));
vi.mock('../../map/regionSearch', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../map/regionSearch')>()),
  searchRegions: vi.fn(),
}));
vi.mock('../spot/BakjiReportPanel', () => ({
  BakjiReportPanel: ({ draft, onSubmitted }: { draft: unknown; onSubmitted: () => void }) => (
    <button type="button" onClick={onSubmitted}>
      {draft ? '위치 있음 제보 보내기' : '위치 없음 제보 보내기'}
    </button>
  ),
}));
vi.mock('../spot/SpotDetailPanel', () => ({
  SpotDetailPanel: ({ onLoaded }: { onLoaded: (detail: unknown) => void }) => (
    <button
      type="button"
      onClick={() =>
        onLoaded({ spotId: 7, type: 'BAKJI', name: '아침 박지', lat: 37.5, lng: 127.5, parkWarning: { warned: false } })
      }
    >
      상세 불러옴
    </button>
  ),
}));

const SEORAK: RegionCandidate = {
  name: '설악산',
  position: { lat: 38.12, lng: 128.46 },
  level: 8,
  pinned: true,
};

describe('MapPage', () => {
  let container: HTMLDivElement;
  let root: Root;
  let view: FakeMapView;
  const create = vi.mocked(createMapView);
  const search = vi.mocked(searchRegions);

  beforeEach(() => {
    forgetMapScreen();
    view = createFakeMapView();
    create.mockReset().mockResolvedValue(view);
    search.mockReset().mockResolvedValue([SEORAK]);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url === '/api/me'
          ? new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED', message: 'x' }), { status: 401 })
          : new Response(JSON.stringify({ code: 'INTERNAL_ERROR', message: 'x' }), { status: 500 }),
      ),
    );
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      },
    );
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: false,
      media: query,
      addEventListener() {},
      removeEventListener() {},
    }));
    window.history.replaceState(null, '', '/map');
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  async function mount(props: Parameters<typeof MapPage>[0] = {}) {
    await act(async () =>
      root.render(
        <SessionProvider>
          <MapPage {...props} />
        </SessionProvider>,
      ),
    );
  }

  describe('지도 만들기', () => {
    it('지도를 만들면 한 번만 만들고, 화면을 닫을 때 지도를 없앤다', async () => {
      await mount();

      expect(create).toHaveBeenCalledTimes(1);
      expect(view.destroy).not.toHaveBeenCalled();

      act(() => root.unmount());

      expect(view.destroy).toHaveBeenCalledTimes(1);
      root = createRoot(container);
    });

    it('키가 없어서 지도를 못 만들면 안내를 보이고 다시 불러오기 버튼은 없다', async () => {
      create.mockRejectedValue(new MapLoadError('NO_KEY'));
      await mount();

      expect(container.textContent).toContain('지도를 불러오지 못했어요');
      expect(container.textContent).not.toContain('다시 불러오기');
    });

    it('불러오기에 실패한 뒤 "다시 불러오기"를 누르면 지도를 다시 만들고 안내를 걷는다', async () => {
      create.mockRejectedValueOnce(new MapLoadError('LOAD_FAILED'));
      await mount();
      expect(container.textContent).toContain('지도를 불러오지 못했어요');

      const retry = [...container.querySelectorAll('button')].find((b) => b.textContent === '다시 불러오기');
      await act(async () => retry?.click());

      expect(create).toHaveBeenCalledTimes(2);
      expect(container.textContent).not.toContain('지도를 불러오지 못했어요');
    });
  });

  describe('지도 오른쪽 클릭 메뉴', () => {
    const POSITION = { lat: 37.1, lng: 127.2 };

    it('오른쪽 클릭하면 그 자리에 메뉴를 열고, 지도를 움직이면 닫는다', async () => {
      await mount();

      act(() => view.emit.contextMenu(POSITION));

      expect(view.showMenu).toHaveBeenCalledWith(POSITION, expect.any(Number));
      expect(view.menuHost.textContent).toContain('로그인');

      act(() => view.emit.moveStart());

      expect(view.showMenu).toHaveBeenLastCalledWith(null);
      expect(view.menuHost.textContent).toBe('');
    });

    it('지도를 누르면 메뉴를 닫는다', async () => {
      await mount();
      act(() => view.emit.contextMenu(POSITION));

      act(() => view.emit.mapClick({ lat: 37, lng: 127 }));

      expect(view.menuHost.textContent).toBe('');
    });

    it('박지 제보 화면에서는 메뉴를 열지 않는다', async () => {
      await mount({ mode: 'report' });

      act(() => view.emit.contextMenu(POSITION));

      expect(view.showMenu).not.toHaveBeenCalled();
    });

    it('베이스캠프 목록 화면에서는 메뉴를 연다', async () => {
      await mount({ mode: 'basecamps' });

      act(() => view.emit.contextMenu(POSITION));

      expect(view.showMenu).toHaveBeenCalledWith(POSITION, expect.any(Number));
    });
  });

  describe('주소의 ?q= 딥링크', () => {
    it('지도가 준비되면 주소에서 q를 지우고 첫 후보 지역으로 옮기며 핀을 꽂는다', async () => {
      window.history.replaceState(null, '', `/map?q=${encodeURIComponent('설악산')}`);
      await mount();

      expect(window.location.search).toBe('');
      expect(search).toHaveBeenCalledWith('설악산');
      expect(view.setCenter).toHaveBeenCalledWith(SEORAK.position, SEORAK.level);
      expect(view.showSearchPin).toHaveBeenLastCalledWith(SEORAK.position);
    });

    it('q가 비어 있으면 검색하지 않는다', async () => {
      window.history.replaceState(null, '', '/map?q=%20');
      await mount();

      expect(search).not.toHaveBeenCalled();
      expect(view.setCenter).not.toHaveBeenCalled();
    });

    it('후보가 없거나 검색이 실패해도 화면이 멈추지 않는다', async () => {
      window.history.replaceState(null, '', '/map?q=none');
      search.mockResolvedValueOnce([]);
      await mount();
      expect(view.setCenter).not.toHaveBeenCalled();
      expect(container.querySelector('[aria-label="지도"]')).not.toBeNull();
    });

    it('검색이 실패해도 화면이 멈추지 않는다', async () => {
      window.history.replaceState(null, '', '/map?q=boom');
      search.mockRejectedValueOnce(new Error('boom'));
      await mount();
      expect(view.setCenter).not.toHaveBeenCalled();
      expect(container.querySelector('[aria-label="지도"]')).not.toBeNull();
    });

    it('장소 상세 화면에서는 q를 읽지 않는다', async () => {
      window.history.replaceState(null, '', '/map?q=x');
      await mount({ mode: 'bakjiEdit', editSpotId: 3 });

      expect(search).not.toHaveBeenCalled();
      expect(window.location.search).toBe('?q=x');
    });
  });

  describe('검색 핀', () => {
    it('검색으로 지도가 움직이는 동안에는 핀을 지우지 않고, 사용자가 지도를 움직이면 지운다', async () => {
      window.history.replaceState(null, '', '/map?q=x');
      await mount();
      await act(async () => {});
      expect(view.showSearchPin).toHaveBeenLastCalledWith(SEORAK.position);

      act(() => view.emit.moveStart());

      expect(view.showSearchPin).toHaveBeenLastCalledWith(null);
    });

    it('핀을 꽂지 않는 후보는 핀을 지운다', async () => {
      window.history.replaceState(null, '', '/map?q=x');
      search.mockResolvedValueOnce([{ ...SEORAK, pinned: false }]);
      await mount();

      expect(view.setCenter).toHaveBeenCalled();
      expect(view.showSearchPin).toHaveBeenLastCalledWith(null);
    });
  });

  describe('박지 제보', () => {
    it('지도를 누른 자리를 제보 위치로 보여 준다', async () => {
      await mount({ mode: 'report' });

      act(() => view.emit.mapClick({ lat: 37.3, lng: 127.3 }));

      expect(view.showDraft).toHaveBeenLastCalledWith({ lat: 37.3, lng: 127.3 });
    });

    it('넘겨받은 위치가 지도 밖이면 그 위치로 옮기고, 안이면 옮기지 않는다', async () => {
      await mount({ mode: 'report', initialDraft: { lat: 35, lng: 126 } });

      expect(view.showDraft).toHaveBeenLastCalledWith({ lat: 35, lng: 126 });
      expect(view.setCenter).toHaveBeenCalledWith({ lat: 35, lng: 126 }, 5);

      act(() => root.unmount());
      root = createRoot(container);
      view.setCenter.mockClear();
      await mount({ mode: 'report', initialDraft: { lat: 36.5, lng: 127.5 } });

      expect(view.setCenter).not.toHaveBeenCalled();
    });

    it('제보를 보내면 위치를 비우고, 그 뒤로는 지도를 눌러도 위치를 받지 않는다', async () => {
      await mount({ mode: 'report' });
      act(() => view.emit.mapClick({ lat: 37.3, lng: 127.3 }));
      const submit = () =>
        [...container.querySelectorAll('button')].find((b) => b.textContent?.endsWith('제보 보내기'));
      expect(submit()?.textContent).toBe('위치 있음 제보 보내기');

      await act(async () => submit()?.click());
      act(() => view.emit.mapClick({ lat: 37.4, lng: 127.4 }));

      expect(submit()?.textContent).toBe('위치 없음 제보 보내기');
      expect(view.showDraft).toHaveBeenLastCalledWith(null);
    });

    it('지도를 보기만 하는 화면에서는 제보 위치를 받지 않는다', async () => {
      await mount();

      act(() => view.emit.mapClick({ lat: 37.3, lng: 127.3 }));

      expect(view.showDraft).not.toHaveBeenCalledWith({ lat: 37.3, lng: 127.3 });
    });
  });

  describe('장소 상세', () => {
    it('상세가 불러와지면 그 장소를 지도에 표시하고 그 자리로 옮긴다', async () => {
      await mount({ spotId: 7 });
      expect(view.showSelectedSpot).toHaveBeenLastCalledWith(null);

      const loaded = [...container.querySelectorAll('button')].find((b) => b.textContent === '상세 불러옴');
      await act(async () => loaded?.click());

      expect(view.showSelectedSpot).toHaveBeenLastCalledWith(
        expect.objectContaining({ id: 7, kind: 'BAKJI', position: { lat: 37.5, lng: 127.5 } }),
      );
      expect(view.panTo).toHaveBeenCalled();
    });

    it('다른 장소로 바뀌면 이전 장소 표시를 걷는다', async () => {
      await mount({ spotId: 7 });
      const loaded = [...container.querySelectorAll('button')].find((b) => b.textContent === '상세 불러옴');
      await act(async () => loaded?.click());

      await mount({ spotId: 8 });

      expect(view.showSelectedSpot).toHaveBeenLastCalledWith(null);
    });
  });
});
