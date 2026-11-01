import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SessionProvider } from '../../app/session/session';
import { createMapView } from '../../map/KakaoMap';
import { MapPage } from './MapPage';
import { forgetMapScreen } from './mapScreenMemory';
import { createFakeMapView } from './fakeMapView';
import type { FakeMapView } from './fakeMapView';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

vi.mock('../../map/KakaoMap', () => ({ createMapView: vi.fn() }));
vi.mock('../../map/addressName', () => ({ addressAt: async () => null }));

const MAP_CENTER = { lat: 37.5, lng: 127.25 };

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('박지 제보 위치를 키보드로 찍기', () => {
  let container: HTMLDivElement;
  let root: Root;
  let view: FakeMapView;
  let postBodies: unknown[];

  beforeEach(async () => {
    forgetMapScreen();
    view = createFakeMapView();
    view.setCenter(MAP_CENTER);
    vi.mocked(createMapView).mockReset().mockResolvedValue(view);
    postBodies = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/me') {
          return json(200, {
            memberId: 1,
            email: 'a@b.c',
            nickname: '산사람',
            status: 'ACTIVE',
            role: 'USER',
            selfAgeGroup: null,
            selfGender: null,
            identityVerified: false,
            trustLevel: 1,
          });
        }
        if (url === '/api/bakjis' && init?.method === 'POST') {
          postBodies.push(JSON.parse(String(init.body)));
          return json(200, {
            spotId: 9,
            parkWarning: { warned: false },
            guide: '제보를 받았어요.',
            duplicateCandidates: [],
          });
        }
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
    vi.stubGlobal('scrollTo', vi.fn());
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
    window.history.replaceState(null, '', '/map/report');
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () =>
      root.render(
        <SessionProvider>
          <MapPage mode="report" />
        </SessionProvider>,
      ),
    );
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  const button = (name: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === name);

  it('지도를 누르지 않아도 버튼으로 지도 가운데를 위치로 찍는다', async () => {
    expect(container.textContent).toContain('지도를 누르거나, 지도를 옮긴 뒤 아래 버튼을 눌러 박지 위치를 찍어 주세요');

    await act(async () => button('지도 가운데를 위치로 쓰기')?.click());

    expect(view.showDraft).toHaveBeenLastCalledWith(MAP_CENTER);
    expect(container.textContent).toContain('37.50000, 127.25000');
    expect(container.textContent).toContain('지도를 다시 누르거나 버튼을 다시 누르면 위치가 바뀌어요.');
  });

  it('휴대폰에서 시트가 지도를 다 덮으면 가운데 버튼을 숨긴다', async () => {
    const sheetToggle = () =>
      [...container.querySelectorAll('button')].find((element) =>
        element.getAttribute('aria-label')?.startsWith('패널 크기 바꾸기'),
      );
    expect(sheetToggle()?.getAttribute('aria-label')).toBe('패널 크기 바꾸기, 지금 절반');

    await act(async () => sheetToggle()?.click());

    expect(sheetToggle()?.getAttribute('aria-label')).toBe('패널 크기 바꾸기, 지금 전체');
    expect(button('지도 가운데를 위치로 쓰기')).toBeUndefined();
    expect(container.textContent).toContain('지도를 눌러 박지 위치를 찍어 주세요');

    await act(async () => sheetToggle()?.click());

    expect(button('지도 가운데를 위치로 쓰기')).toBeDefined();
  });

  it('지도 칸 하나만 지도라는 이름으로 읽힌다', () => {
    const labelled = container.querySelectorAll('[aria-label="지도"]');
    expect(labelled).toHaveLength(1);
    expect(labelled[0]?.getAttribute('role')).toBe('application');
  });

  it('버튼으로 찍은 위치를 제보에 실어 보낸다', async () => {
    const field = (label: string) =>
      [...container.querySelectorAll('label')].find((element) => element.textContent === label)
        ?.control as HTMLInputElement;
    await act(async () => button('지도 가운데를 위치로 쓰기')?.click());
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.call(field('이름'), '솔숲 박지');
      field('이름').dispatchEvent(new Event('input', { bubbles: true }));
    });
    for (const name of ['hasWater', 'hasToilet']) {
      const yes = [...container.querySelectorAll<HTMLInputElement>(`input[name="${name}"]`)].find(
        (element) => element.parentElement?.textContent === '있음',
      );
      await act(async () => yes?.click());
    }

    await act(async () => button('제보하기')?.click());

    expect(postBodies).toEqual([{ name: '솔숲 박지', ...MAP_CENTER, hasWater: true, hasToilet: true }]);
  });
});
