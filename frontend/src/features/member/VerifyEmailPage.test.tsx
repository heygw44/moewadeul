import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resendVerification, verifyEmail } from '../../api/auth';
import { ApiError } from '../../api/client';
import { useLocation } from '../../app/router';
import { VerifyEmailPage } from './VerifyEmailPage';

vi.mock('../../api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/auth')>()),
  verifyEmail: vi.fn(),
  resendVerification: vi.fn(),
}));

const session = vi.hoisted(() => ({
  status: 'authenticated',
  me: null as null | { email: string; status: string },
  refresh: vi.fn(),
  logout: vi.fn(),
}));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => ({ status: session.status, me: session.me, refresh: session.refresh, logout: session.logout }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function Route() {
  const { pathname } = useLocation();
  return pathname === '/verify-email' ? <VerifyEmailPage /> : null;
}

const UNVERIFIED_ME = { email: 'hiker@gmail.com', status: 'UNVERIFIED' };
const ACTIVE_ME = { email: 'hiker@gmail.com', status: 'ACTIVE' };

describe('이메일 인증 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const codeInput = () => container.querySelector<HTMLInputElement>('[name="code"]')!;

  async function open(url = '/verify-email') {
    window.history.replaceState(null, '', url);
    await act(async () => root.render(<Route />));
  }

  async function submitCode(code: string) {
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.call(codeInput(), code);
      codeInput().dispatchEvent(new Event('input', { bubbles: true }));
    });
    await act(async () => button('인증하기')?.click());
  }

  beforeEach(() => {
    session.status = 'authenticated';
    session.me = UNVERIFIED_ME;
    session.refresh.mockReset().mockResolvedValue(ACTIVE_ME);
    session.logout.mockReset().mockResolvedValue(undefined);
    vi.mocked(verifyEmail).mockReset().mockResolvedValue({ status: 'ACTIVE' });
    vi.mocked(resendVerification).mockReset().mockResolvedValue(undefined);
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.useRealTimers();
  });

  it('로그인하지 않았으면 이 화면을 돌아올 주소로 달아 로그인 화면으로 보낸다', async () => {
    session.status = 'anonymous';
    session.me = null;

    await open('/verify-email?next=%2Fbasecamps');

    expect(window.location.pathname).toBe('/login');
    expect(new URLSearchParams(window.location.search).get('next')).toBe('/verify-email?next=%2Fbasecamps');
  });

  it('인증이 필요한 회원에게는 코드를 보낸 이메일 주소와 입력란이 보인다', async () => {
    await open();

    expect(container.querySelector('h1')?.textContent).toBe('이메일 인증');
    expect(container.textContent).toContain('hiker@gmail.com');
    expect(codeInput()).not.toBeNull();
  });

  it('코드를 비우고 누르면 입력하라는 문구가 보이고 요청은 가지 않는다', async () => {
    await open();

    await act(async () => button('인증하기')?.click());

    expect(container.textContent).toContain('메일로 받은 6자리 코드를 입력해 주세요.');
    expect(verifyEmail).not.toHaveBeenCalled();
  });

  it('맞는 코드를 넣으면 앞뒤 공백을 떼어 보내고 지도로 이동한다', async () => {
    await open();

    await submitCode(' 123456 ');

    expect(verifyEmail).toHaveBeenCalledWith('123456');
    expect(window.location.pathname).toBe('/map');
  });

  it('돌아올 주소가 있으면 인증 뒤에 그 화면으로 이동한다', async () => {
    await open('/verify-email?next=%2Fbasecamps');

    await submitCode('123456');

    expect(window.location.pathname).toBe('/basecamps');
  });

  it('코드가 틀리면 입력란 아래에 안내가 보이고 화면은 그대로다', async () => {
    vi.mocked(verifyEmail).mockRejectedValue(new ApiError(400, 'EMAIL_CODE_INVALID', null));
    await open();

    await submitCode('000000');

    expect(container.textContent).toContain('인증 코드가 맞지 않아요');
    expect(window.location.pathname).toBe('/verify-email');
  });

  it('서버가 실패하면 인증하지 못했다는 오류가 보인다', async () => {
    vi.mocked(verifyEmail).mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR', null));
    await open();

    await submitCode('123456');

    expect(container.querySelector('[role="alert"]')?.textContent).toContain('인증하지 못했어요');
  });

  it('코드를 다시 받으면 안내가 보이고 버튼이 60초 동안 잠긴다', async () => {
    await open();

    await act(async () => button('코드 다시 받기')?.click());

    expect(resendVerification).toHaveBeenCalledTimes(1);
    expect(container.querySelector('[role="status"]')?.textContent).toContain('새 인증 코드를 보냈어요');
    expect(button('코드 다시 받기')?.disabled).toBe(true);
    expect(container.textContent).toContain('60초 뒤에 다시 받을 수 있어요');
  });

  it('다시 받기가 너무 잦다고 막히면 서버가 알려 준 초만큼 버튼이 잠기고, 시간이 지나면 풀린다', async () => {
    vi.useFakeTimers();
    vi.mocked(resendVerification).mockRejectedValue(
      new ApiError(429, 'EMAIL_RESEND_LIMITED', { code: 'EMAIL_RESEND_LIMITED' } as never, 25),
    );
    await open();

    await act(async () => button('코드 다시 받기')?.click());

    expect(container.querySelector('[role="status"]')?.textContent).toContain('코드를 너무 자주 요청했어요');
    expect(button('코드 다시 받기')?.disabled).toBe(true);
    expect(container.textContent).toContain('25초 뒤에 다시 받을 수 있어요');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(25_000);
    });
    expect(button('코드 다시 받기')?.disabled).toBe(false);
  });

  it('이미 인증을 마친 회원에게는 인증 입력란 대신 지도로 가는 링크가 보인다', async () => {
    session.me = ACTIVE_ME;

    await open();

    expect(container.querySelector('h1')?.textContent).toBe('이미 인증을 마쳤어요');
    expect(codeInput()).toBeNull();
    const link = [...container.querySelectorAll('a')].find((element) => element.textContent === '지도로 가기');
    expect(link?.getAttribute('href')).toBe('/map');
  });
});
