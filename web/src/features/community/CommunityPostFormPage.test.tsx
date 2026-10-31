import { act } from 'react';
import type { ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { navigate, useLocation } from '../../app/router';
import { SaveNoticeTray } from '../../app/saveNotice/SaveNoticeTray';
import { resetSaveNoticesForTest } from '../../app/saveNotice/saveNotices';
import { SessionProvider } from '../../app/session/session';
import { CommunityPostFormPage } from './CommunityPostFormPage';

vi.mock('../../components/HubLayout', () => ({
  HubLayout: ({ children }: { children: ReactNode }) => <main>{children}</main>,
  AsideSection: ({ children }: { children: ReactNode }) => <aside>{children}</aside>,
}));

vi.mock('../../components/reencodeImage', () => ({
  reencodeImage: async (file: File) => new Blob(['reencoded'], { type: file.type }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const FORM_PATH = '/community/new';

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

function FormRoute() {
  const { pathname } = useLocation();
  return pathname === FORM_PATH ? <CommunityPostFormPage mode="create" /> : <p>로그인 화면</p>;
}

describe('글쓰기 화면: 저장할 때', () => {
  let container: HTMLDivElement;
  let root: Root;
  let currentMemberId: number;
  let postStatus: number;
  let postGate: Promise<void> | null;
  let postFailure: 'network' | 'server' | null;

  const input = (label: string) =>
    [...container.querySelectorAll('label')].find((element) => element.textContent === label)?.control as
      HTMLInputElement | HTMLTextAreaElement;
  const tray = () => container.querySelector('[role="status"]');
  const photoCount = () => container.querySelectorAll('img[alt^="올린 사진"]').length;

  async function mountForm() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <FormRoute />
          <SaveNoticeTray />
        </SessionProvider>,
      );
    });
  }

  async function unmountForm() {
    await act(async () => root.unmount());
  }

  async function type(field: HTMLInputElement | HTMLTextAreaElement, value: string) {
    const prototype = field instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    await act(async () => {
      Object.getOwnPropertyDescriptor(prototype, 'value')?.set?.call(field, value);
      field.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }

  async function attachPhoto() {
    const file = new File(['x'], 'trail.png', { type: 'image/png' });
    const field = container.querySelector<HTMLInputElement>('input[type="file"]');
    if (!field) throw new Error('사진 입력이 없다');
    await act(async () => {
      Object.defineProperty(field, 'files', { value: [file], configurable: true });
      field.dispatchEvent(new Event('change', { bubbles: true }));
    });
  }

  async function submit() {
    const button = [...container.querySelectorAll('button')].find((element) => element.textContent === '올리기');
    await act(async () => button?.click());
  }

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    currentMemberId = 1;
    postStatus = 401;
    postGate = null;
    postFailure = null;
    resetSaveNoticesForTest();
    window.history.replaceState(null, '', FORM_PATH);
    URL.createObjectURL = vi.fn(() => 'blob:preview');
    URL.revokeObjectURL = vi.fn();
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/me') return json(200, me(currentMemberId));
        if (url === '/api/community/images') {
          return json(201, { imageId: 77, uploadUrl: 'https://storage.test/put', method: 'PUT', headers: {} });
        }
        if (url === 'https://storage.test/put') return new Response(null, { status: 200 });
        if (url === '/api/community/posts' && init?.method === 'POST') {
          if (postGate) await postGate;
          if (postFailure === 'network') throw new TypeError('Failed to fetch');
          if (postFailure === 'server') return json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 't' });
          return postStatus === 401
            ? json(401, { code: 'AUTHENTICATION_REQUIRED', message: 'x', traceId: 't' })
            : json(201, { postId: 5 });
        }
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
  });

  afterEach(async () => {
    await unmountForm().catch(() => undefined);
    container.remove();
    vi.unstubAllGlobals();
  });

  async function writeAndGetKickedOut() {
    await mountForm();
    await type(input('제목'), '지리산 종주 후기');
    await type(input('내용'), '비가 와서 대피소에서 잤어요.');
    await attachPhoto();
    expect(photoCount()).toBe(1);
    await submit();
    expect(window.location.pathname).toBe('/login');
    await unmountForm();
  }

  it('같은 회원이 로그인하고 돌아오면 제목·내용·올린 사진이 그대로 채워진다', async () => {
    await writeAndGetKickedOut();

    window.history.replaceState(null, '', FORM_PATH);
    await mountForm();

    expect(input('제목').value).toBe('지리산 종주 후기');
    expect(input('내용').value).toBe('비가 와서 대피소에서 잤어요.');
    expect(photoCount()).toBe(1);
  });

  it('다른 회원이 로그인하면 앞사람의 글은 채워지지 않는다', async () => {
    await writeAndGetKickedOut();

    currentMemberId = 2;
    window.history.replaceState(null, '', FORM_PATH);
    await mountForm();

    expect(input('제목').value).toBe('');
    expect(input('내용').value).toBe('');
    expect(photoCount()).toBe(0);
  });

  it('채워진 초안은 한 번만 쓴다. 같은 주소를 나중에 다시 열면 빈 폼이다', async () => {
    await writeAndGetKickedOut();

    window.history.replaceState(null, '', FORM_PATH);
    await mountForm();
    expect(input('제목').value).toBe('지리산 종주 후기');
    await unmountForm();

    window.history.replaceState(null, '', FORM_PATH);
    await mountForm();
    expect(input('제목').value).toBe('');
  });

  async function saveAndLeave() {
    let finishSave: () => void = () => undefined;
    postGate = new Promise<void>((resolve) => {
      finishSave = resolve;
    });
    await mountForm();
    await type(input('제목'), '지리산 종주 후기');
    await type(input('내용'), '비가 와서 대피소에서 잤어요.');
    await submit();
    await act(async () => navigate('/community'));
    return finishSave;
  }

  it('저장하는 중에 다른 화면으로 가면 저장이 끝나도 저장한 글로 끌려가지 않는다', async () => {
    postStatus = 201;
    const finishSave = await saveAndLeave();
    await act(async () => finishSave());

    expect(window.location.pathname).toBe('/community');
  });

  it('저장하는 중에 다른 화면으로 가도 먼저 누른 저장이 어떻게 됐는지 알림으로 알 수 있다', async () => {
    postStatus = 201;
    const finishSave = await saveAndLeave();
    expect(tray()?.textContent).toContain('글을 올리는 중이에요.');

    await act(async () => finishSave());

    expect(tray()?.textContent).toContain('글을 올렸어요.');
    expect(tray()?.querySelector('a')?.getAttribute('href')).toBe('/community/5');
  });

  it('저장하는 중에 다른 화면으로 간 뒤 연결이 끊기면 저장됐는지 확인하라는 안내와 커뮤니티 링크가 뜬다', async () => {
    postFailure = 'network';
    const finishSave = await saveAndLeave();
    await act(async () => finishSave());

    expect(tray()?.textContent).toContain('글을 올리지 못했어요.');
    expect(tray()?.textContent).toContain('저장됐는지 확인하지 못했어요. 다시 저장하기 전에 먼저 확인해 주세요.');
    expect(tray()?.querySelector('a')?.getAttribute('href')).toBe('/community');
  });

  it('저장하는 중에 다른 화면으로 간 뒤 서버 오류가 나면 실패했다는 알림이 뜬다', async () => {
    postFailure = 'server';
    const finishSave = await saveAndLeave();
    await act(async () => finishSave());

    expect(tray()?.textContent).toContain('글을 올리지 못했어요.');
    expect(tray()?.textContent).not.toContain('글을 올리는 중이에요.');
  });

  it('저장하는 중에 다른 화면으로 간 뒤 로그인이 풀려도 알림은 남기지 않는다', async () => {
    postStatus = 401;
    const finishSave = await saveAndLeave();
    await act(async () => finishSave());

    expect(tray()?.textContent).toBe('');
  });
});
