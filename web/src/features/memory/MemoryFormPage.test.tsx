import { act } from 'react';
import type { ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { navigate, useLocation } from '../../app/router';
import { SaveNoticeTray } from '../../app/saveNotice/SaveNoticeTray';
import { notifySignedOut } from '../../app/session/signedOut';
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
  let saveResponse: () => Response;

  const submitButton = () =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === '남기기');
  const tray = () => container.querySelector('[role="status"]');

  async function saveAndLeave() {
    await act(async () => submitButton()?.click());
    await act(async () => navigate('/community'));
  }

  beforeEach(async () => {
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    notifySignedOut();
    saveResponse = () => json(201, { memoryId: 9 });
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
          return saveResponse();
        }
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
    await act(async () =>
      root.render(
        <SessionProvider>
          <FormRoute />
          <SaveNoticeTray />
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
    expect(tray()?.textContent).toBe('');
  });

  it('저장하는 중에 다른 화면으로 가면 저장이 끝나도 저장한 기록으로 끌려가지 않는다', async () => {
    await saveAndLeave();
    await act(async () => finishSave());

    expect(window.location.pathname).toBe('/community');
  });

  it('저장하는 중에 다른 화면으로 가도 먼저 누른 저장이 어떻게 됐는지 알림으로 알 수 있다', async () => {
    await saveAndLeave();
    expect(tray()?.textContent).toContain('기록을 남기는 중이에요.');

    await act(async () => finishSave());

    expect(tray()?.textContent).toContain('기록을 남겼어요.');
    expect(tray()?.querySelector('a')?.getAttribute('href')).toBe('/memories/9');
  });

  it('저장하는 중에 다른 화면으로 간 뒤 연결이 끊기면 저장됐는지 확인하라는 안내와 내 기록 링크가 뜬다', async () => {
    saveResponse = () => {
      throw new TypeError('Failed to fetch');
    };
    await saveAndLeave();
    await act(async () => finishSave());

    expect(tray()?.textContent).toContain('저장됐는지 확인하지 못했어요.');
    expect(tray()?.textContent).toContain('다시 저장하기 전에 먼저 확인해 주세요.');
    expect(tray()?.textContent).not.toContain('기록을 남기지 못했어요.');
    expect(tray()?.querySelector('a')?.getAttribute('href')).toBe('/me/memories');
  });

  it('저장하는 중에 다른 화면으로 간 뒤 서버 오류가 나면 실패했다는 알림이 뜬다', async () => {
    saveResponse = () => json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 't' });
    await saveAndLeave();
    await act(async () => finishSave());

    expect(tray()?.textContent).toContain('기록을 남기지 못했어요.');
    expect(tray()?.textContent).not.toContain('기록을 남기는 중이에요.');
  });

  it('저장하는 중에 다른 화면으로 간 뒤 로그인이 풀려도 알림은 남기지 않는다', async () => {
    saveResponse = () => json(401, { code: 'AUTHENTICATION_REQUIRED', message: 'x', traceId: 't' });
    await saveAndLeave();
    await act(async () => finishSave());

    expect(tray()?.textContent).toBe('');
  });
});
