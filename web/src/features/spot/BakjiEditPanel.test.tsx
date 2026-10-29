import { act, useState } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LatLng } from '../../map/types';
import { SessionProvider } from '../../app/session/session';
import { BakjiEditPanel } from './BakjiEditPanel';

vi.mock('../../map/addressName', () => ({ addressAt: async () => null }));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const SPOT_ID = 7;
const EDIT_PATH = `/bakjis/${SPOT_ID}/edit`;

function me(memberId: number) {
  return {
    memberId,
    email: 'a@b.c',
    nickname: '산사람',
    status: 'ACTIVE',
    role: 'USER',
    selfAgeGroup: null,
    selfGender: null,
    identityVerified: false,
    trustLevel: 1,
  };
}

function spotDetail(reporterId: number) {
  return {
    spotId: SPOT_ID,
    type: 'BAKJI',
    name: '솔숲 박지',
    lat: 37.123456,
    lng: 127.654321,
    address: null,
    bakji: {
      description: '바람이 잔잔해요.',
      hasWater: true,
      hasToilet: false,
      signalLevel: 'WEAK',
      confirmationCount: 0,
      reporter: { memberId: reporterId, nickname: '산사람' },
    },
    publicDetail: null,
    parkWarning: { warned: false },
    rating: { average: null, count: 0 },
    recentReviews: [],
    expectedPeople: [],
    recruitingBasecamps: [],
  };
}

const SUBMISSION = {
  spotId: SPOT_ID,
  parkWarning: { warned: false },
  guide: '박지 정보를 고쳤어요.',
  duplicateCandidates: [],
};

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const MOVED_POSITION: LatLng = { lat: 37.2, lng: 127.654321 };

function EditScreen() {
  const [draft, setDraft] = useState<LatLng | null>(null);
  return (
    <>
      <button type="button" onClick={() => setDraft(MOVED_POSITION)}>
        지도 누르기
      </button>
      <BakjiEditPanel
        spotId={SPOT_ID}
        draft={draft}
        onLoaded={(detail) => setDraft({ lat: detail.lat, lng: detail.lng })}
        onCancel={() => undefined}
        onSubmitted={() => undefined}
      />
    </>
  );
}

describe('박지 수정 화면', () => {
  let container: HTMLDivElement;
  let root: Root;
  let reporterId: number;
  let patchBodies: unknown[];

  const input = (label: string) =>
    [...container.querySelectorAll('label')].find((element) => element.textContent === label)?.control as
      HTMLInputElement | HTMLTextAreaElement;
  const radio = (name: string, label: string) =>
    [...container.querySelectorAll<HTMLInputElement>(`input[name="${name}"]`)].find(
      (element) => element.parentElement?.textContent === label,
    );

  async function type(field: HTMLInputElement | HTMLTextAreaElement, value: string) {
    const prototype = field instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    await act(async () => {
      Object.getOwnPropertyDescriptor(prototype, 'value')?.set?.call(field, value);
      field.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }

  async function save() {
    const button = [...container.querySelectorAll('button')].find((element) => element.textContent === '저장하기');
    await act(async () => button?.click());
  }

  beforeEach(async () => {
    container = document.createElement('div');
    vi.stubGlobal('scrollTo', vi.fn());
    document.body.appendChild(container);
    reporterId = 1;
    patchBodies = [];
    window.history.replaceState(null, '', EDIT_PATH);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/me') return json(200, me(1));
        if (url === `/api/spots/${SPOT_ID}`) return json(200, spotDetail(reporterId));
        if (url === `/api/bakjis/${SPOT_ID}` && init?.method === 'PATCH') {
          patchBodies.push(JSON.parse(String(init.body)));
          return json(200, SUBMISSION);
        }
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  async function mount() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <EditScreen />
        </SessionProvider>,
      );
    });
  }

  it('F-07: 지금 박지 정보를 채워서 연다', async () => {
    await mount();

    expect(input('이름').value).toBe('솔숲 박지');
    expect(input('설명 (선택)').value).toBe('바람이 잔잔해요.');
    expect(radio('hasWater', '있음')?.checked).toBe(true);
    expect(radio('hasToilet', '없음')?.checked).toBe(true);
    expect(radio('signalLevel', '약함')?.checked).toBe(true);
    expect(radio('groundType', '바꾸지 않음')?.checked).toBe(true);
  });

  it('F-07: 바꾼 필드만 보내고, 비운 설명과 모름으로 바꾼 통신 상태는 null로 보낸다', async () => {
    await mount();

    await type(input('이름'), '  소나무 박지 ');
    await type(input('설명 (선택)'), '');
    await act(async () => radio('signalLevel', '모름')?.click());
    await save();

    expect(patchBodies).toEqual([{ name: '소나무 박지', description: null, signalLevel: null }]);
    expect(container.textContent).toContain('박지 정보를 고쳤어요');
  });

  it('F-07: 위치를 옮기면 위도와 경도를 함께 보낸다', async () => {
    await mount();

    await act(async () =>
      [...container.querySelectorAll('button')].find((element) => element.textContent === '지도 누르기')?.click(),
    );
    await save();

    expect(patchBodies).toEqual([{ lat: 37.2, lng: 127.654321 }]);
  });

  it('F-07: 바닥은 고를 때만 보낸다', async () => {
    await mount();

    await act(async () => radio('groundType', '모름')?.click());
    await save();

    expect(patchBodies).toEqual([{ groundType: null }]);
  });

  it('F-07: 바꾼 것이 없으면 서버에 보내지 않고 장소 상세로 돌아간다', async () => {
    await mount();

    await save();

    expect(patchBodies).toEqual([]);
    expect(window.location.pathname).toBe(`/spots/${SPOT_ID}`);
  });

  it('F-07: 다른 회원이 제보한 박지면 폼을 열지 않고 장소 상세로 보낸다', async () => {
    reporterId = 2;
    await mount();

    expect(input('이름')).toBeUndefined();
    expect(window.location.pathname).toBe(`/spots/${SPOT_ID}`);
  });
});
