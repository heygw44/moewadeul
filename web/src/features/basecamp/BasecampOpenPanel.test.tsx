import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { navigate, useLocation } from '../../app/router';
import { SaveNoticeTray } from '../../app/saveNotice/SaveNoticeTray';
import { resetSaveNoticesForTest } from '../../app/saveNotice/saveNotices';
import { SessionProvider } from '../../app/session/session';
import { addDaysToLocalDate, todayKst } from '../../lib/datetime';
import { BasecampOpenPanel } from './BasecampOpenPanel';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const PANEL_PATH = '/basecamps/new';

const ME = {
  memberId: 1,
  email: 'a@b.c',
  nickname: '산사람',
  status: 'ACTIVE',
  role: 'USER',
  selfAgeGroup: null,
  selfGender: null,
  identityVerified: true,
  trustLevel: 1,
};

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function PanelRoute() {
  const { pathname } = useLocation();
  return pathname === PANEL_PATH ? <BasecampOpenPanel spotId={1} onBack={() => undefined} /> : <p>다른 화면</p>;
}

describe('베이스캠프 열기 패널: 저장할 때', () => {
  let container: HTMLDivElement;
  let root: Root;
  let finishSave: () => void;
  let saveResponse: () => Response;

  const tray = () => container.querySelector('[role="status"]');
  const input = (label: string) =>
    [...container.querySelectorAll('label')].find((element) => element.textContent === label)?.control as
      HTMLInputElement | HTMLTextAreaElement;
  const submitButton = () =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === '베이스캠프 열기');

  async function type(field: HTMLInputElement | HTMLTextAreaElement, value: string) {
    const prototype = field instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    await act(async () => {
      Object.getOwnPropertyDescriptor(prototype, 'value')?.set?.call(field, value);
      field.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }

  async function fillAndSave() {
    const start = addDaysToLocalDate(todayKst(), 2);
    await type(input('제목'), '설악산 1박 2일');
    await type(input('설명'), '대피소에서 만나요.');
    await type(input('출발일'), start);
    await type(input('돌아오는 날'), addDaysToLocalDate(start, 1));
    await act(async () => container.querySelector<HTMLInputElement>('input[name="capacity"][value="4"]')?.click());
    await act(async () => submitButton()?.click());
  }

  async function saveAndLeave() {
    await fillAndSave();
    await act(async () => navigate('/community'));
  }

  beforeEach(async () => {
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    resetSaveNoticesForTest();
    saveResponse = () => json(201, { basecampId: 12 });
    const gate = new Promise<void>((resolve) => {
      finishSave = resolve;
    });
    window.history.replaceState(null, '', PANEL_PATH);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/me') return json(200, ME);
        if (url === '/api/spots/1') return json(200, { spotId: 1, name: '설악 박지', parkWarning: { warned: false } });
        if (url === '/api/basecamps' && init?.method === 'POST') {
          await gate;
          return saveResponse();
        }
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
    await act(async () =>
      root.render(
        <SessionProvider>
          <PanelRoute />
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

  it('저장이 끝나면 연 베이스캠프 화면으로 간다', async () => {
    await fillAndSave();
    await act(async () => finishSave());

    expect(window.location.pathname).toBe('/basecamps/12');
    expect(tray()?.textContent).toBe('');
  });

  it('저장하는 중에 다른 화면으로 가면 저장이 끝나도 연 베이스캠프로 끌려가지 않는다', async () => {
    await saveAndLeave();
    await act(async () => finishSave());

    expect(window.location.pathname).toBe('/community');
  });

  it('저장하는 중에 다른 화면으로 가도 먼저 누른 저장이 어떻게 됐는지 알림으로 알 수 있다', async () => {
    await saveAndLeave();
    expect(tray()?.textContent).toContain('베이스캠프를 여는 중이에요.');

    await act(async () => finishSave());

    expect(tray()?.textContent).toContain('베이스캠프를 열었어요.');
    expect(tray()?.querySelector('a')?.getAttribute('href')).toBe('/basecamps/12');
  });

  it('저장하는 중에 다른 화면으로 간 뒤 연결이 끊기면 저장됐는지 확인하라는 안내와 내 베이스캠프 링크가 뜬다', async () => {
    saveResponse = () => {
      throw new TypeError('Failed to fetch');
    };
    await saveAndLeave();
    await act(async () => finishSave());

    expect(tray()?.textContent).toContain('베이스캠프를 열지 못했어요.');
    expect(tray()?.textContent).toContain('저장됐는지 확인하지 못했어요. 다시 저장하기 전에 먼저 확인해 주세요.');
    expect(tray()?.querySelector('a')?.getAttribute('href')).toBe('/me/basecamps');
  });

  it('저장하는 중에 다른 화면으로 간 뒤 서버 오류가 나면 실패했다는 알림이 뜬다', async () => {
    saveResponse = () => json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 't' });
    await saveAndLeave();
    await act(async () => finishSave());

    expect(tray()?.textContent).toContain('베이스캠프를 열지 못했어요.');
    expect(tray()?.textContent).not.toContain('베이스캠프를 여는 중이에요.');
  });

  it('저장하는 중에 다른 화면으로 간 뒤 로그인이 풀려도 알림은 남기지 않는다', async () => {
    saveResponse = () => json(401, { code: 'AUTHENTICATION_REQUIRED', message: 'x', traceId: 't' });
    await saveAndLeave();
    await act(async () => finishSave());

    expect(tray()?.textContent).toBe('');
  });
});
