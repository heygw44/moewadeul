import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Mock } from 'vitest';
import { SessionProvider } from '../../app/session/session';
import { BakjiOwnerActions } from './BakjiOwnerActions';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const SPOT_ID = 7;
const REPORTER_ID = 1;

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

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('박지 상세의 수정·삭제', () => {
  let container: HTMLDivElement;
  let root: Root;
  let currentMemberId: number | null;
  let deleteCalls: number;
  let onDeleted: Mock<() => void>;

  const button = (label: string, scope: ParentNode = container) =>
    [...scope.querySelectorAll('button')].find((element) => element.textContent === label);
  const editLink = () => [...container.querySelectorAll('a')].find((element) => element.textContent === '수정') ?? null;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    currentMemberId = REPORTER_ID;
    deleteCalls = 0;
    onDeleted = vi.fn();
    window.history.replaceState(null, '', `/spots/${SPOT_ID}`);
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.open = true;
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.open = false;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/me') {
          return currentMemberId === null
            ? json(401, { code: 'AUTHENTICATION_REQUIRED', message: 'x', traceId: 't' })
            : json(200, me(currentMemberId));
        }
        if (url === `/api/bakjis/${SPOT_ID}` && init?.method === 'DELETE') {
          deleteCalls += 1;
          return new Response(null, { status: 204 });
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
          <BakjiOwnerActions spotId={SPOT_ID} reporterId={REPORTER_ID} onDeleted={onDeleted} />
        </SessionProvider>,
      );
    });
  }

  it('F-07: 제보한 회원에게만 수정과 삭제가 보인다', async () => {
    await mount();

    expect(editLink()?.getAttribute('href')).toBe(`/bakjis/${SPOT_ID}/edit`);
    expect(button('삭제')).toBeDefined();
  });

  it('F-07: 다른 회원에게는 수정과 삭제가 보이지 않는다', async () => {
    currentMemberId = 2;
    await mount();

    expect(editLink()).toBeNull();
    expect(button('삭제')).toBeUndefined();
  });

  it('F-07: 로그인하지 않으면 수정과 삭제가 보이지 않는다', async () => {
    currentMemberId = null;
    await mount();

    expect(editLink()).toBeNull();
    expect(button('삭제')).toBeUndefined();
  });

  it('F-07: 삭제를 확인하면 DELETE를 보내고 지도로 돌아간다', async () => {
    await mount();

    await act(async () => button('삭제')?.click());
    const dialog = container.querySelector('dialog');
    if (!dialog) throw new Error('삭제 확인 다이얼로그가 없다');
    expect(deleteCalls).toBe(0);

    await act(async () => button('삭제', dialog)?.click());

    expect(deleteCalls).toBe(1);
    expect(onDeleted).toHaveBeenCalledTimes(1);
    expect(window.location.pathname).toBe('/map');
  });

  it('F-07: 삭제 확인에서 취소하면 DELETE를 보내지 않는다', async () => {
    await mount();

    await act(async () => button('삭제')?.click());
    const dialog = container.querySelector('dialog');
    if (!dialog) throw new Error('삭제 확인 다이얼로그가 없다');
    await act(async () => button('취소', dialog)?.click());

    expect(deleteCalls).toBe(0);
    expect(container.querySelector('dialog')).toBeNull();
    expect(window.location.pathname).toBe(`/spots/${SPOT_ID}`);
  });
});
