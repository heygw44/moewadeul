import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadPage } from '../../app/router';
import { SessionProvider } from './session';
import { SignupPage } from './SignupPage';

vi.mock('../../app/router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/router')>()),
  loadPage: vi.fn(),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('회원가입 화면의 만 14세 확인과 약관 안내', () => {
  let container: HTMLDivElement;
  let root: Root;
  let fetchMock: ReturnType<typeof vi.fn>;

  const signupRequests = () =>
    fetchMock.mock.calls.filter(
      ([url, init]) => url === '/api/members' && (init as RequestInit | undefined)?.method === 'POST',
    );
  const field = (name: string) => container.querySelector<HTMLInputElement | HTMLSelectElement>(`[name="${name}"]`)!;
  const link = (label: string) =>
    [...container.querySelectorAll('a')].find((element) => element.textContent === label) ?? null;
  const ageCheckbox = () =>
    [...container.querySelectorAll('label')].find((element) => element.textContent === '만 14세 이상이에요')
      ?.control as HTMLInputElement;

  async function fill(element: HTMLInputElement | HTMLSelectElement, value: string, eventType: 'input' | 'change') {
    const prototype = element instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
    await act(async () => {
      Object.getOwnPropertyDescriptor(prototype, 'value')?.set?.call(element, value);
      element.dispatchEvent(new Event(eventType, { bubbles: true }));
    });
  }

  async function fillValidForm() {
    await fill(field('email'), 'hiker', 'input');
    await fill(container.querySelector('select')!, 'gmail.com', 'change');
    await fill(field('password'), 'Valid-pass-1!', 'input');
    await fill(field('passwordConfirm'), 'Valid-pass-1!', 'input');
    await fill(field('nickname'), '새벽능선', 'input');
  }

  async function submit() {
    const button = [...container.querySelectorAll('button')].find((element) => element.textContent === '가입하기');
    await act(async () => button!.click());
  }

  beforeEach(async () => {
    container = document.createElement('div');
    document.body.appendChild(container);
    fetchMock = vi.fn(async (url: string) =>
      url === '/api/members'
        ? json(201, { memberId: 1, status: 'UNVERIFIED' })
        : json(401, { code: 'AUTHENTICATION_REQUIRED' }),
    );
    vi.stubGlobal('fetch', fetchMock);
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <SignupPage />
        </SessionProvider>,
      );
    });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  it('만 14세 이상 체크 없이 가입하면 오류를 보여 주고 가입 요청을 보내지 않는다', async () => {
    await fillValidForm();

    await submit();

    expect(container.textContent).toContain('만 14세 이상만 가입할 수 있어요.');
    expect(ageCheckbox().getAttribute('aria-invalid')).toBe('true');
    expect(signupRequests()).toHaveLength(0);
  });

  it('체크하고 가입하면 ageConfirmed를 true로 보낸다', async () => {
    await fillValidForm();
    await act(async () => ageCheckbox().click());

    await submit();

    expect(signupRequests()).toHaveLength(1);
    const [, init] = signupRequests()[0]!;
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toMatchObject({ ageConfirmed: true });
  });

  it('가입과 로그인은 됐는데 회원 정보를 읽지 못하면 이메일 인증 페이지를 새로 연다', async () => {
    let loggedIn = false;
    fetchMock.mockImplementation(async (url: string) => {
      if (url === '/api/members') return json(201, { memberId: 1, status: 'UNVERIFIED' });
      if (url === '/api/auth/login') {
        loggedIn = true;
        return json(200, { memberId: 1, nickname: '새벽능선', status: 'UNVERIFIED', role: 'USER' });
      }
      return loggedIn ? json(500, { code: 'INTERNAL_ERROR', message: '서버 오류' }) : json(401, {});
    });
    await fillValidForm();
    await act(async () => ageCheckbox().click());

    await submit();

    expect(loadPage).toHaveBeenCalledWith('/verify-email');
  });

  it('이용약관과 개인정보 처리방침 링크가 안내 문구 안에 있다', () => {
    expect(container.textContent).toContain('가입하면 이용약관과 개인정보 처리방침에 동의하게 돼요.');
    expect(link('이용약관')?.getAttribute('href')).toBe('/terms');
    expect(link('개인정보 처리방침')?.getAttribute('href')).toBe('/privacy');
  });
});
