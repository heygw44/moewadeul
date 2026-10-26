import { act, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Me } from '../../api/types';
import type { Session } from './session';
import { useViewerEpoch } from './useViewerEpoch';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

let current: Pick<Session, 'status' | 'me'>;
vi.mock('./session', () => ({ useSession: () => current }));

const member = (memberId: number) => ({ memberId }) as Me;
const LOADING = { status: 'loading', me: null } as const;
const ANONYMOUS = { status: 'anonymous', me: null } as const;
const signedIn = (memberId: number) => ({ status: 'authenticated', me: member(memberId) }) as const;

type Request = { epoch: number; signal: AbortSignal };

describe('useViewerEpoch', () => {
  let container: HTMLDivElement;
  let root: Root;
  let epoch: number;
  let requests: Request[];
  // 화면이 내용 요청을 보내는 방식 그대로다. 값이 바뀔 때만 이전 요청을 끊고 다시 보낸다.
  function Probe() {
    const value = useViewerEpoch();
    epoch = value;
    useEffect(() => {
      const controller = new AbortController();
      requests.push({ epoch: value, signal: controller.signal });
      return () => controller.abort();
    }, [value]);
    return null;
  }

  async function show(session: typeof current) {
    current = session;
    await act(async () => root.render(<Probe />));
  }

  beforeEach(() => {
    container = document.createElement('div');
    root = createRoot(container);
    requests = [];
  });

  afterEach(() => act(async () => root.unmount()));

  it('세션이 처음 정해질 때는 이미 보낸 요청을 다시 보내지 않는다', async () => {
    await show(LOADING);
    await show(ANONYMOUS);

    expect(epoch).toBe(0);
    expect(requests).toHaveLength(1);
    expect(requests[0]!.signal.aborted).toBe(false);
  });

  it('처음 정해진 세션이 로그인한 회원이어도 다시 보내지 않는다', async () => {
    await show(LOADING);
    await show(signedIn(7));

    expect(requests).toHaveLength(1);
    expect(requests[0]!.signal.aborted).toBe(false);
  });

  it('세션이 이미 정해진 채로 열면 요청 하나만 보낸다', async () => {
    await show(signedIn(7));

    expect(requests).toHaveLength(1);
  });

  it('요청이 끝나기 전에 세션이 만료되면 이전 요청을 끊고 로그아웃 상태로 다시 보낸다', async () => {
    await show(LOADING);
    await show(signedIn(7));
    await show(ANONYMOUS);

    expect(requests).toHaveLength(2);
    expect(requests[0]!.signal.aborted).toBe(true);
    expect(requests[1]!.signal.aborted).toBe(false);
  });

  it('다른 회원으로 로그인하면 다시 보낸다', async () => {
    await show(signedIn(7));
    await show(signedIn(8));

    expect(requests).toHaveLength(2);
  });

  it('같은 회원의 정보만 새로 받았을 때는 다시 보내지 않는다', async () => {
    await show(signedIn(7));
    await show(signedIn(7));

    expect(requests).toHaveLength(1);
  });
});
