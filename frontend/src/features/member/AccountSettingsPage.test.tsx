import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { changePassword, updateMe } from '../../api/members';
import { AccountSettingsPage } from './AccountSettingsPage';

vi.mock('../../api/members', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/members')>()),
  updateMe: vi.fn(),
  changePassword: vi.fn(),
}));

vi.mock('../../api/notifications', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/notifications')>()),
  fetchUnreadCount: vi.fn(async () => ({ count: 0 })),
}));

const session = vi.hoisted(() => ({
  status: 'authenticated',
  me: null as unknown,
  refresh: vi.fn(),
  logout: vi.fn(),
}));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => ({ status: session.status, me: session.me, refresh: session.refresh, logout: session.logout }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function me(overrides: Record<string, unknown> = {}) {
  return {
    memberId: 1,
    email: 'hiker@gmail.com',
    nickname: '산사람',
    status: 'ACTIVE',
    role: 'USER',
    selfAgeGroup: 'THIRTIES',
    selfGender: null,
    ...overrides,
  };
}

describe('계정 설정 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const input = (name: string) => container.querySelector<HTMLInputElement>(`[name="${name}"]`)!;
  const sectionTitles = () => [...container.querySelectorAll('main h2')].map((element) => element.textContent);

  async function type(name: string, value: string) {
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.call(input(name), value);
      input(name).dispatchEvent(new Event('input', { bubbles: true }));
    });
  }

  async function open() {
    window.history.replaceState(null, '', '/me/settings');
    await act(async () => root.render(<AccountSettingsPage />));
  }

  beforeEach(() => {
    session.status = 'authenticated';
    session.me = me();
    session.refresh.mockReset().mockResolvedValue(null);
    session.logout.mockReset().mockResolvedValue(undefined);
    vi.mocked(updateMe)
      .mockReset()
      .mockResolvedValue(me() as never);
    vi.mocked(changePassword).mockReset().mockResolvedValue(undefined);
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('로그인하지 않았으면 설정 화면을 돌아올 주소로 달아 로그인 화면으로 보낸다', async () => {
    session.status = 'anonymous';
    session.me = null;

    await open();

    expect(window.location.pathname).toBe('/login');
    expect(new URLSearchParams(window.location.search).get('next')).toBe('/me/settings');
  });

  it('회원 정보를 아직 읽는 중이면 제목만 보이고 설정 입력란은 보이지 않는다', async () => {
    session.status = 'loading';
    session.me = null;

    await open();

    expect(container.querySelector('h1')?.textContent).toBe('계정 설정');
    expect(container.querySelector('form')).toBeNull();
  });

  it('로그인한 회원에게는 프로필, 비밀번호 변경, 회원 탈퇴 영역이 보이고 닉네임이 채워져 있다', async () => {
    await open();

    expect(container.querySelector('h1')?.textContent).toBe('계정 설정');
    expect(sectionTitles()).toEqual(['프로필 정보', '비밀번호 변경', '회원 탈퇴']);
    expect(input('nickname').value).toBe('산사람');
    expect(container.querySelector<HTMLInputElement>('input[name="selfAgeGroup"]:checked')?.value).toBe('THIRTIES');
  });

  it('이메일 인증 전인 회원은 프로필을 고칠 수 없고 이유가 보인다', async () => {
    session.me = me({ status: 'UNVERIFIED' });

    await open();

    expect(button('저장')?.disabled).toBe(true);
    expect(input('nickname').disabled).toBe(true);
    expect(container.textContent).toContain('이메일 인증을 마치면 수정할 수 있어요');
  });

  it('닉네임을 바꿔 저장하면 바뀐 값만 보내고 저장했다는 안내가 보인다', async () => {
    await open();

    await type('nickname', '새벽능선');
    await act(async () => button('저장')?.click());

    expect(updateMe).toHaveBeenCalledWith({ nickname: '새벽능선' });
    expect(container.querySelector('[role="status"]')?.textContent).toBe('저장했어요.');
    expect(session.refresh).toHaveBeenCalledTimes(1);
  });

  it('닉네임을 비우고 저장하면 입력하라는 문구가 보이고 요청은 가지 않는다', async () => {
    await open();

    await type('nickname', '  ');
    await act(async () => button('저장')?.click());

    expect(container.textContent).toContain('닉네임을 입력해 주세요.');
    expect(updateMe).not.toHaveBeenCalled();
  });

  it('다른 회원이 쓰는 닉네임이면 닉네임 칸 아래에 안내가 보인다', async () => {
    vi.mocked(updateMe).mockRejectedValue(new ApiError(409, 'MEMBER_NICKNAME_DUPLICATED', null));
    await open();

    await type('nickname', '새벽능선');
    await act(async () => button('저장')?.click());

    expect(container.textContent).toContain('다른 회원이 쓰는 닉네임이에요');
    expect(container.querySelector('[role="status"]')).toBeNull();
  });

  it('비밀번호를 바꾸면 입력란이 비워지고 바꿨다는 안내가 보인다', async () => {
    await open();

    await type('currentPassword', 'Old-pass-1234!');
    await type('newPassword', 'New-pass-1234!');
    await type('newPasswordConfirm', 'New-pass-1234!');
    await act(async () => button('비밀번호 바꾸기')?.click());

    expect(changePassword).toHaveBeenCalledWith({
      currentPassword: 'Old-pass-1234!',
      newPassword: 'New-pass-1234!',
      newPasswordConfirm: 'New-pass-1234!',
    });
    expect(container.textContent).toContain('비밀번호를 바꿨어요');
    expect(input('currentPassword').value).toBe('');
  });
});
