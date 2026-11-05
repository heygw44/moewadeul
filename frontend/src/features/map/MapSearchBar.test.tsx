import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { interpretSpotQuery } from '../../api/aiSearch';
import { SessionProvider } from '../../app/session/session';
import { notifySignedOut } from '../../app/session/signedOut';
import { searchRegions } from '../../map/regionSearch';
import type { RegionCandidate } from '../../map/regionSearch';
import { MapSearchBar } from './MapSearchBar';

vi.mock('../../api/aiSearch', () => ({ interpretSpotQuery: vi.fn() }));
vi.mock('../../map/regionSearch', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../map/regionSearch')>()),
  searchRegions: vi.fn(),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function candidate(name: string): RegionCandidate {
  return { name, position: { lat: 36.5, lng: 128.7 }, level: 10, pinned: false };
}

function apiError(code: string, status: number) {
  return new ApiError(status, code, null);
}

describe('지도 검색 칸', () => {
  const search = vi.mocked(searchRegions);
  const interpret = vi.mocked(interpretSpotQuery);
  let container: HTMLDivElement;
  let root: Root;
  let onSelect: ReturnType<typeof vi.fn<(candidate: RegionCandidate) => void>>;
  let onResult: ReturnType<typeof vi.fn>;
  let signedIn: boolean;

  async function mount() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <MapSearchBar
            onSearch={() => undefined}
            onSelect={onSelect}
            mapMoving={false}
            ai={{ onResult: onResult as never }}
          />
        </SessionProvider>,
      );
    });
  }

  async function submit(value: string) {
    const field = container.querySelector('input');
    if (!field) throw new Error('입력 칸이 없다');
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.call(field, value);
      field.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await act(async () => {
      container.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });
  }

  async function turnOnAi() {
    await act(async () => container.querySelector<HTMLButtonElement>('button[aria-pressed]')?.click());
  }

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    onSelect = vi.fn();
    onResult = vi.fn();
    signedIn = true;
    search.mockReset();
    interpret.mockReset();
    notifySignedOut();
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url === '/api/me' && signedIn) {
          return new Response(JSON.stringify({ memberId: 1, nickname: '산사람', status: 'ACTIVE', role: 'USER' }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        return new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED', message: 'x', traceId: 't' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  it('[F-30] 후보가 하나면 고르지 않아도 바로 그 지역으로 옮긴다', async () => {
    search.mockResolvedValue([candidate('안동시')]);
    await mount();

    await submit('안동');

    expect(onSelect).toHaveBeenCalledWith(candidate('안동시'));
    expect(container.querySelector('ul[aria-label="지역 후보"]')).toBeNull();
  });

  it('[F-30] 후보가 여럿이면 목록을 보여 주고, 고른 후보로 옮긴다', async () => {
    search.mockResolvedValue([candidate('안동시'), candidate('안동읍')]);
    await mount();

    await submit('안동');
    expect(onSelect).not.toHaveBeenCalled();
    const buttons = container.querySelectorAll<HTMLButtonElement>('ul[aria-label="지역 후보"] button');
    expect(buttons).toHaveLength(2);

    await act(async () => buttons[1]?.click());
    expect(onSelect).toHaveBeenCalledWith(candidate('안동읍'));
  });

  it('[F-30] 결과가 없으면 지도를 그대로 두고 다른 이름으로 찾으라고 안내한다', async () => {
    search.mockResolvedValue([]);
    await mount();

    await submit('없는곳 주변');

    expect(onSelect).not.toHaveBeenCalled();
    expect(container.querySelector('[role="status"]')?.textContent).toContain(
      "'없는곳' 검색 결과가 없어요. 다른 지역 이름이나 주소로 찾아 주세요.",
    );
  });

  it('[F-30] 공백뿐이거나 50자를 넘으면 검색하지 않고 입력 칸 아래에 이유를 보여 준다', async () => {
    await mount();

    await submit('   ');
    expect(container.textContent).toContain('찾을 지역 이름이나 주소를 입력해 주세요.');

    await submit('가'.repeat(51));
    expect(container.textContent).toContain('50자 이하로 입력해 주세요.');

    expect(search).not.toHaveBeenCalled();
  });

  it('[F-27][AI-01] 로그인하지 않으면 AI 검색 대신 로그인 안내를 보여 준다', async () => {
    signedIn = false;
    await mount();

    await turnOnAi();

    expect(container.textContent).toContain('AI 검색은 로그인한 회원이 쓸 수 있어요.');
    expect(container.querySelector('input')).toBeNull();
  });

  it('[F-27][AI-07] AI 모드에는 입력한 문장을 해석을 위해 OpenAI로 보낸다는 안내가 보인다', async () => {
    await mount();

    await turnOnAi();

    expect(container.textContent).toContain('입력한 문장은 해석을 위해 OpenAI로 보내져요.');
  });

  it('[F-27][AI-02] 공백을 뺀 길이가 2자 미만이거나 100자를 넘으면 서버를 부르지 않는다', async () => {
    await mount();
    await turnOnAi();

    await submit(' 가 ');
    expect(container.textContent).toContain('2자 이상 100자 이하로 입력해 주세요.');

    await submit('가'.repeat(101));
    expect(container.textContent).toContain('2자 이상 100자 이하로 입력해 주세요.');

    expect(interpret).not.toHaveBeenCalled();
  });

  it('[F-27][AI-09] 503이면 지역 이름으로 검색하라고 안내하고, 지역 검색으로 바꾸면 검색할 수 있다', async () => {
    interpret.mockRejectedValue(apiError('AI_SEARCH_UNAVAILABLE', 503));
    search.mockResolvedValue([candidate('안동시')]);
    await mount();
    await turnOnAi();

    await submit('강원도 계곡 옆 박지');

    const alert = container.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('지금은 AI 검색을 쓸 수 없어요. 지역 이름으로 검색해 주세요.');
    expect(onResult).not.toHaveBeenCalled();

    await act(async () => alert?.querySelector('button')?.click());
    await submit('안동');
    expect(onSelect).toHaveBeenCalledWith(candidate('안동시'));
  });

  it('[F-27][AI-03] 하루 한도를 넘고 남은 시간을 모르면 내일 다시 쓸 수 있다는 안내와 지역 검색은 쓸 수 있다는 안내를 보여 준다', async () => {
    interpret.mockRejectedValue(apiError('AI_SEARCH_LIMITED', 429));
    await mount();
    await turnOnAi();

    await submit('강원도 계곡 옆 박지');

    const alert = container.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('오늘 AI 검색 20번을 모두 썼어요.');
    expect(alert?.textContent).toContain('내일 다시 쓸 수 있어요.');
    expect(alert?.textContent).toContain('지역 이름 검색은 지금도 쓸 수 있어요.');
  });

  it('[F-27][AI-03] 하루 한도를 넘고 서버가 남은 시간을 주면 그 시간을 보여 준다', async () => {
    interpret.mockRejectedValue(new ApiError(429, 'AI_SEARCH_LIMITED', null, 5400));
    await mount();
    await turnOnAi();

    await submit('강원도 계곡 옆 박지');

    const alert = container.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('1시간 30분 뒤에 다시 쓸 수 있어요.');
  });

  it('[F-27][AI-05] 해석한 지역의 후보가 하나면 결과와 후보를 함께 넘긴다', async () => {
    const result = { region: '강원도', unparsed: [] };
    interpret.mockResolvedValue(result);
    search.mockResolvedValue([candidate('강원특별자치도')]);
    await mount();
    await turnOnAi();

    await submit('강원도 계곡 옆 박지');

    expect(search).toHaveBeenCalledWith('강원도');
    expect(onResult).toHaveBeenCalledWith(result, candidate('강원특별자치도'));
  });
});
