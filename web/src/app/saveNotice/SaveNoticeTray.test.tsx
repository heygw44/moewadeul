import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '../../api/client';
import { SaveNoticeTray } from './SaveNoticeTray';
import { resetSaveNoticesForTest, useSaveOutlivingScreen } from './saveNotices';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const COPY = {
  pending: '글을 올리는 중이에요.',
  done: (id: number) => ({ title: '글을 올렸어요.', link: { to: `/community/${id}`, label: '글 보기' } }),
  failed: '글을 올리지 못했어요.',
  checkAt: { to: '/community', label: '커뮤니티 보기' },
};

describe('저장 알림 트레이', () => {
  let container: HTMLDivElement;
  let root: Root;
  let settleSave: { resolve: (id: number) => void; reject: (error: unknown) => void };
  let outcome: string[];

  function SaveScreen() {
    const track = useSaveOutlivingScreen();
    return (
      <button
        type="button"
        onClick={() => {
          const save = new Promise<number>((resolve, reject) => {
            settleSave = { resolve, reject };
          });
          track(save, COPY).then(
            (id) => outcome.push(`저장됨 ${id}`),
            () => outcome.push('실패'),
          );
        }}
      >
        저장
      </button>
    );
  }

  async function renderScreen(visible: boolean) {
    await act(async () =>
      root.render(
        <>
          {visible && <SaveScreen />}
          <SaveNoticeTray />
        </>,
      ),
    );
  }

  const tray = () => container.querySelector('[role="status"]');
  const closeButtons = () => container.querySelectorAll<HTMLButtonElement>('button[aria-label="알림 닫기"]');

  async function startSaveAndLeave() {
    await renderScreen(true);
    await act(async () => container.querySelector('button')?.click());
    await renderScreen(false);
  }

  beforeEach(() => {
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    outcome = [];
    resetSaveNoticesForTest();
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('화면에 머무는 동안 저장이 끝나면 알림 없이 호출한 쪽에 결과가 돌아간다', async () => {
    await renderScreen(true);
    await act(async () => container.querySelector('button')?.click());
    await act(async () => settleSave.resolve(5));

    expect(outcome).toEqual(['저장됨 5']);
    expect(tray()?.textContent).toBe('');
  });

  it('저장 중에 화면을 떠나면 진행 중 알림이 뜬다', async () => {
    await startSaveAndLeave();

    expect(tray()?.textContent).toContain('글을 올리는 중이에요.');
    expect(closeButtons()).toHaveLength(0);
  });

  it('저장 중에 화면을 떠난 뒤 저장이 끝나면 결과와 보러 가는 링크가 알림으로 남고 호출한 쪽은 깨어나지 않는다', async () => {
    await startSaveAndLeave();
    await act(async () => settleSave.resolve(5));

    expect(tray()?.textContent).toContain('글을 올렸어요.');
    expect(tray()?.textContent).not.toContain('올리는 중');
    expect(tray()?.querySelector('a')?.getAttribute('href')).toBe('/community/5');
    expect(outcome).toEqual([]);
  });

  it('알림 닫기를 누르면 그 알림이 사라진다', async () => {
    await startSaveAndLeave();
    await act(async () => settleSave.resolve(5));

    await act(async () => closeButtons()[0]?.click());

    expect(tray()?.textContent).toBe('');
  });

  it('저장 중에 화면을 떠난 뒤 네트워크 오류로 끝나면 저장됐는지 확인하라는 안내와 확인할 곳 링크가 뜬다', async () => {
    await startSaveAndLeave();
    await act(async () => settleSave.reject(new ApiError(0, 'NETWORK_ERROR', null)));

    expect(tray()?.textContent).toContain('글을 올리지 못했어요.');
    expect(tray()?.textContent).toContain('저장됐는지 확인하지 못했어요. 다시 저장하기 전에 먼저 확인해 주세요.');
    expect(tray()?.querySelector('a')?.getAttribute('href')).toBe('/community');
  });

  it('저장 중에 화면을 떠난 뒤 서버 오류로 끝나면 오류 문구만 보이고 확인할 곳 링크는 없다', async () => {
    await startSaveAndLeave();
    await act(async () => settleSave.reject(new ApiError(500, 'INTERNAL_ERROR', null)));

    expect(tray()?.textContent).toContain('글을 올리지 못했어요.');
    expect(tray()?.textContent).not.toContain('저장됐는지 확인하지 못했어요');
    expect(tray()?.querySelector('a')).toBeNull();
  });

  it('저장 중에 화면을 떠난 뒤 로그인이 풀려 끝나면 알림을 남기지 않는다', async () => {
    await startSaveAndLeave();
    await act(async () => settleSave.reject(new ApiError(401, 'AUTHENTICATION_REQUIRED', null)));

    expect(tray()?.textContent).toBe('');
  });
});
