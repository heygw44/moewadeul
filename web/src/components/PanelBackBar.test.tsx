import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PanelBackBar } from './PanelBackBar';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

describe('PanelBackBar', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    container = document.createElement('div');
    root = createRoot(container);
  });

  afterEach(() => act(() => root.unmount()));

  it('label을 주면 글자가 보이는 버튼을 그리고 누르면 onBack을 부른다', async () => {
    const onBack = vi.fn();
    await act(async () => root.render(<PanelBackBar label="목록" onBack={onBack} />));

    const button = container.querySelector('button');
    expect(button?.textContent).toBe('목록');
    await act(async () => button?.click());

    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('ariaLabel만 주면 글자 없이 접근 이름만 가진 아이콘 버튼을 그린다', async () => {
    const onBack = vi.fn();
    await act(async () => root.render(<PanelBackBar ariaLabel="지도로 돌아가기" onBack={onBack} />));

    const button = container.querySelector('button');
    expect(button?.getAttribute('aria-label')).toBe('지도로 돌아가기');
    expect(button?.textContent).toBe('');
    await act(async () => button?.click());

    expect(onBack).toHaveBeenCalledTimes(1);
  });
});
