import { act } from 'react';
import type { ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { navigate, useLocation } from '../../app/router';
import { SessionProvider } from '../../app/session/session';
import { MemoryFormPage } from './MemoryFormPage';

vi.mock('../../components/HubLayout', () => ({
  HubLayout: ({ children }: { children: ReactNode }) => <main>{children}</main>,
  AsideSection: ({ children }: { children: ReactNode }) => <aside>{children}</aside>,
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const FORM_PATH = '/memories/new';

const ME = {
  memberId: 1,
  email: 'a@b.c',
  nickname: '산사람',
  status: 'ACTIVE',
  role: 'USER',
  selfAgeGroup: null,
  selfGender: null,
  identityVerified: false,
  trustLevel: 1,
};

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function FormRoute() {
  const { pathname } = useLocation();
  return pathname === FORM_PATH ? <MemoryFormPage mode="create" /> : <p>다른 화면</p>;
}

describe('기록 남기기 화면: 저장할 때', () => {
  let container: HTMLDivElement;
  let root: Root;
  let finishSave: () => void;

  const submitButton = () =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === '남기기');

  beforeEach(async () => {
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    const gate = new Promise<void>((resolve) => {
      finishSave = resolve;
    });
    window.history.replaceState(null, '', `${FORM_PATH}?lat=37.5&lng=127.0`);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/me') return json(200, ME);
        if (url === '/api/memories' && init?.method === 'POST') {
          await gate;
          return json(201, { memoryId: 9 });
        }
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
    await act(async () =>
      root.render(
        <SessionProvider>
          <FormRoute />
        </SessionProvider>,
      ),
    );
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  it('저장이 끝나면 저장한 기록 화면으로 간다', async () => {
    await act(async () => submitButton()?.click());
    await act(async () => finishSave());

    expect(window.location.pathname).toBe('/memories/9');
  });

  it('저장하는 중에 다른 화면으로 가면 저장이 끝나도 저장한 기록으로 끌려가지 않는다', async () => {
    await act(async () => submitButton()?.click());

    await act(async () => navigate('/community'));
    await act(async () => finishSave());

    expect(window.location.pathname).toBe('/community');
  });
});
