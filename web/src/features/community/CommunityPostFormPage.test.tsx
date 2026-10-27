import { act } from 'react';
import type { ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useLocation } from '../../app/router';
import { SessionProvider } from '../member/session';
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

describe('글쓰기 화면: 세션이 끊긴 채 저장했을 때', () => {
  let container: HTMLDivElement;
  let root: Root;
  let currentMemberId: number;
  let postStatus: number;

  const input = (label: string) =>
    [...container.querySelectorAll('label')].find((element) => element.textContent === label)?.control as
      HTMLInputElement | HTMLTextAreaElement;
  const photoCount = () => container.querySelectorAll('img[alt^="올린 사진"]').length;

  async function mountForm() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <FormRoute />
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
});
