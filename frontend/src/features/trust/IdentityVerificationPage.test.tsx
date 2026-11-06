import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { verifyIdentity } from '../../api/trust';
import { useLocation } from '../../app/router';
import { IdentityVerificationPage } from './IdentityVerificationPage';

vi.mock('../../api/trust', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/trust')>()),
  verifyIdentity: vi.fn(),
}));

type FakeMe = { status: string; identityVerified: boolean; trustLevel: number; nickname: string };

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

function me(overrides: Partial<FakeMe> = {}): FakeMe {
  return { status: 'ACTIVE', identityVerified: false, trustLevel: 0, nickname: '산사람', ...overrides };
}

function Route() {
  const { pathname } = useLocation();
  return pathname === '/identity-verification' ? <IdentityVerificationPage /> : null;
}

describe('본인확인 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const input = (name: string) => container.querySelector<HTMLInputElement>(`[name="${name}"]`)!;

  async function type(name: string, value: string) {
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.call(input(name), value);
      input(name).dispatchEvent(new Event('input', { bubbles: true }));
    });
  }

  async function fillForm(birthYear: string) {
    await type('birthYear', birthYear);
    await act(async () => container.querySelector<HTMLInputElement>('input[name="gender"][value="FEMALE"]')?.click());
    await type('demoIdentityKey', 'demo-key-1');
  }

  async function open(url = '/identity-verification') {
    window.history.replaceState(null, '', url);
    await act(async () => root.render(<Route />));
  }

  beforeEach(() => {
    session.status = 'authenticated';
    session.me = me();
    session.refresh.mockReset().mockResolvedValue(null);
    session.logout.mockReset().mockResolvedValue(undefined);
    vi.mocked(verifyIdentity)
      .mockReset()
      .mockResolvedValue({ adult: true } as never);
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('로그인하지 않았으면 이 화면을 돌아올 주소로 달아 로그인 화면으로 보낸다', async () => {
    session.status = 'anonymous';
    session.me = null;

    await open();

    expect(window.location.pathname).toBe('/login');
    expect(new URLSearchParams(window.location.search).get('next')).toBe('/identity-verification');
  });

  it('이메일 인증 전인 회원에게는 입력란 대신 이메일 인증으로 가는 링크가 보인다', async () => {
    session.me = me({ status: 'UNVERIFIED' });

    await open();

    expect(container.querySelector('h1')?.textContent).toBe('이메일 인증이 먼저 필요해요');
    expect(input('birthYear')).toBeNull();
    const link = [...container.querySelectorAll('a')].find((element) => element.textContent === '이메일 인증하기');
    expect(link?.getAttribute('href')).toBe('/verify-email');
  });

  it('아무것도 입력하지 않고 누르면 세 칸 모두 안내가 보이고 요청은 가지 않는다', async () => {
    await open();

    await act(async () => button('본인확인하기')?.click());

    expect(container.textContent).toContain('출생연도는 1900~');
    expect(container.textContent).toContain('성별을 골라 주세요.');
    expect(container.textContent).toContain('시연용 식별 문자열을 입력해 주세요.');
    expect(verifyIdentity).not.toHaveBeenCalled();
  });

  it.each(['9999', '1899'])('출생연도 %s는 범위를 벗어났다는 안내가 보이고 요청은 가지 않는다', async (birthYear) => {
    await open();
    await fillForm(birthYear);

    await act(async () => button('본인확인하기')?.click());

    expect(container.textContent).toContain('출생연도는 1900~');
    expect(container.textContent).not.toContain('성별을 골라 주세요.');
    expect(verifyIdentity).not.toHaveBeenCalled();
  });

  it('입력을 마치고 보내면 성인으로 확인됐다는 화면이 보이고 회원 정보를 다시 읽는다', async () => {
    await open();
    await fillForm('1990');

    await act(async () => button('본인확인하기')?.click());

    expect(verifyIdentity).toHaveBeenCalledWith({ birthYear: 1990, gender: 'FEMALE', demoIdentityKey: 'demo-key-1' });
    expect(session.refresh).toHaveBeenCalledTimes(1);
    expect(container.querySelector('h1')?.textContent).toBe('본인확인을 마쳤어요');
    expect(container.textContent).toContain('성인으로 확인돼 신뢰 단계 1이 됐어요');
    const link = [...container.querySelectorAll('a')].find((element) => element.textContent === '지도로 가기');
    expect(link?.getAttribute('href')).toBe('/map');
  });

  it('돌아올 주소가 있으면 완료 화면의 링크가 그 화면을 가리킨다', async () => {
    await open('/identity-verification?next=%2Fbasecamps%2F7');
    await fillForm('1990');

    await act(async () => button('본인확인하기')?.click());

    const link = [...container.querySelectorAll('a')].find(
      (element) => element.textContent === '보던 화면으로 돌아가기',
    );
    expect(link?.getAttribute('href')).toBe('/basecamps/7');
  });

  it('성인 기준에 못 미치면 완료 화면에 신뢰 단계 0이라는 경고가 보인다', async () => {
    vi.mocked(verifyIdentity).mockResolvedValue({ adult: false } as never);
    await open();
    await fillForm('2015');

    await act(async () => button('본인확인하기')?.click());

    expect(container.textContent).toContain('아직 성인 기준에 맞지 않아요');
    expect(container.textContent).toContain('신뢰 단계가 0이에요');
  });

  it('이미 본인확인을 마친 회원은 입력란 없이 완료 화면을 본다', async () => {
    session.me = me({ identityVerified: true, trustLevel: 1 });

    await open();

    expect(container.querySelector('h1')?.textContent).toBe('본인확인을 마쳤어요');
    expect(input('birthYear')).toBeNull();
  });

  it('다른 계정에서 쓴 식별 문자열이면 오류 안내가 보이고 입력은 남아 있다', async () => {
    vi.mocked(verifyIdentity).mockRejectedValue(new ApiError(409, 'IDENTITY_CI_DUPLICATED', null));
    await open();
    await fillForm('1990');

    await act(async () => button('본인확인하기')?.click());

    expect(container.querySelector('[role="alert"]')?.textContent).toContain('다른 계정에서 이미 본인확인을 했어요');
    expect(input('demoIdentityKey').value).toBe('demo-key-1');
    expect(session.refresh).not.toHaveBeenCalled();
  });

  it('서버가 입력칸별로 거절하면 해당 칸 아래에 사유가 보인다', async () => {
    vi.mocked(verifyIdentity).mockRejectedValue(
      new ApiError(400, 'INVALID_INPUT', {
        code: 'INVALID_INPUT',
        fieldErrors: [{ field: 'demoIdentityKey', reason: '식별 문자열 형식이 맞지 않아요.' }],
      } as never),
    );
    await open();
    await fillForm('1990');

    await act(async () => button('본인확인하기')?.click());

    expect(container.textContent).toContain('식별 문자열 형식이 맞지 않아요.');
  });
});
