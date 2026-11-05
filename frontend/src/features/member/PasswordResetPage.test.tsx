import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { confirmPasswordReset, requestPasswordReset } from '../../api/auth';
import { ApiError } from '../../api/client';
import { PasswordResetPage } from './PasswordResetPage';

vi.mock('../../api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/auth')>()),
  requestPasswordReset: vi.fn(),
  confirmPasswordReset: vi.fn(),
}));

const session = vi.hoisted(() => ({ refresh: vi.fn() }));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => ({ status: 'anonymous', me: null, refresh: session.refresh, logout: vi.fn() }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

describe('비밀번호 재설정 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const field = (name: string) => container.querySelector<HTMLInputElement>(`[name="${name}"]`)!;

  async function type(element: HTMLInputElement, value: string) {
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.call(element, value);
      element.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }

  async function chooseDomain(domain: string) {
    const select = container.querySelector('select')!;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')?.set?.call(select, domain);
      select.dispatchEvent(new Event('change', { bubbles: true }));
    });
  }

  async function submitEmail() {
    await type(field('email'), 'hiker');
    await chooseDomain('gmail.com');
    await act(async () => button('재설정 링크 받기')?.click());
  }

  async function open(url: string) {
    window.history.replaceState(null, '', url);
    await act(async () => root.render(<PasswordResetPage />));
  }

  beforeEach(() => {
    vi.mocked(requestPasswordReset).mockReset().mockResolvedValue(undefined);
    vi.mocked(confirmPasswordReset).mockReset().mockResolvedValue(undefined);
    session.refresh.mockReset().mockResolvedValue(null);
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.useRealTimers();
  });

  describe('메일 주소를 받는 단계', () => {
    it('이메일을 비우고 누르면 입력하라는 문구만 보이고 요청은 가지 않는다', async () => {
      await open('/password-reset');

      await act(async () => button('재설정 링크 받기')?.click());

      expect(container.textContent).toContain('이메일을 입력해 주세요.');
      expect(requestPasswordReset).not.toHaveBeenCalled();
    });

    it('이메일을 넣어 보내면 안내 문구가 보이고 버튼이 다시 받기로 바뀐다', async () => {
      await open('/password-reset');

      await submitEmail();

      expect(requestPasswordReset).toHaveBeenCalledWith('hiker@gmail.com');
      expect(container.querySelector('[role="status"]')?.textContent).toContain('재설정 링크를 보냈어요');
      expect(button('재설정 링크 다시 받기')).toBeDefined();
    });

    it('요청이 너무 잦아 막히면 안내와 함께 남은 초만큼 버튼이 잠기고, 시간이 지나면 풀린다', async () => {
      vi.useFakeTimers();
      vi.mocked(requestPasswordReset).mockRejectedValue(
        new ApiError(429, 'PASSWORD_RESET_LIMITED', { code: 'PASSWORD_RESET_LIMITED' } as never, 30),
      );
      await open('/password-reset');

      await submitEmail();

      expect(container.querySelector('[role="alert"]')?.textContent).toContain('재설정을 너무 자주 요청했어요');
      expect(button('재설정 링크 받기')?.disabled).toBe(true);
      expect(container.textContent).toContain('30초 뒤에 다시 요청할 수 있어요');

      await act(async () => {
        await vi.advanceTimersByTimeAsync(10_000);
      });
      expect(container.textContent).toContain('20초 뒤에 다시 요청할 수 있어요');

      await act(async () => {
        await vi.advanceTimersByTimeAsync(20_000);
      });
      expect(button('재설정 링크 받기')?.disabled).toBe(false);
      expect(container.textContent).not.toContain('초 뒤에 다시 요청할 수 있어요');
    });

    it('로그인으로 돌아가기 링크가 로그인 화면을 가리킨다', async () => {
      await open('/password-reset');

      const link = [...container.querySelectorAll('a')].find(
        (element) => element.textContent === '로그인으로 돌아가기',
      );

      expect(link?.getAttribute('href')).toBe('/login');
    });
  });

  describe('새 비밀번호를 정하는 단계', () => {
    async function fillPasswords(newPassword: string, confirm: string) {
      await type(field('newPassword'), newPassword);
      await type(field('newPasswordConfirm'), confirm);
    }

    it('메일 링크의 토큰으로 열면 새 비밀번호 입력란이 보이고 주소에서 토큰이 사라진다', async () => {
      await open('/password-reset#token=abc123');

      expect(container.querySelector('h1')?.textContent).toBe('새 비밀번호 정하기');
      expect(field('newPassword')).not.toBeNull();
      expect(window.location.hash).toBe('');
    });

    it('두 비밀번호가 다르면 다르다는 문구가 보이고 서버로 보내지 않는다', async () => {
      await open('/password-reset#token=abc123');
      await fillPasswords('Valid-pass-1!', 'Valid-pass-2!');

      await act(async () => button('비밀번호 바꾸기')?.click());

      expect(container.textContent).toContain('비밀번호가 서로 달라요.');
      expect(confirmPasswordReset).not.toHaveBeenCalled();
    });

    it('비밀번호를 바꾸면 토큰과 함께 보내고, 로그인하기를 누르면 로그인 화면으로 간다', async () => {
      await open('/password-reset#token=abc123');
      await fillPasswords('Valid-pass-1!', 'Valid-pass-1!');

      await act(async () => button('비밀번호 바꾸기')?.click());

      expect(confirmPasswordReset).toHaveBeenCalledWith({
        token: 'abc123',
        newPassword: 'Valid-pass-1!',
        newPasswordConfirm: 'Valid-pass-1!',
      });
      expect(container.querySelector('h1')?.textContent).toBe('비밀번호를 바꿨어요');
      expect(container.querySelector('[role="status"]')?.textContent).toContain('모든 기기에서 로그아웃됐어요');

      await act(async () => button('로그인하기')?.click());

      expect(window.location.pathname).toBe('/login');
    });

    it('비밀번호 규칙에 어긋나면 새 비밀번호 칸 아래에 안내가 보이고 입력은 남는다', async () => {
      vi.mocked(confirmPasswordReset).mockRejectedValue(new ApiError(400, 'MEMBER_PASSWORD_POLICY', null));
      await open('/password-reset#token=abc123');
      await fillPasswords('short', 'short');

      await act(async () => button('비밀번호 바꾸기')?.click());

      expect(container.textContent).toContain('비밀번호 규칙에 맞지 않아요');
      expect(field('newPassword').value).toBe('short');
      expect(container.querySelector('h1')?.textContent).toBe('새 비밀번호 정하기');
    });

    it('만료된 링크면 안내가 보이고, 다시 받기를 누르면 메일 주소를 받는 단계로 돌아간다', async () => {
      vi.mocked(confirmPasswordReset).mockRejectedValue(new ApiError(400, 'PASSWORD_RESET_TOKEN_INVALID', null));
      await open('/password-reset#token=abc123');
      await fillPasswords('Valid-pass-1!', 'Valid-pass-1!');

      await act(async () => button('비밀번호 바꾸기')?.click());

      expect(container.querySelector('[role="alert"]')?.textContent).toContain('이 링크로는 비밀번호를 바꿀 수 없어요');

      await act(async () => button('재설정 링크 다시 받기')?.click());

      expect(field('email')).not.toBeNull();
      expect(container.querySelector('h1')?.textContent).toBe('비밀번호 재설정');
    });
  });
});
