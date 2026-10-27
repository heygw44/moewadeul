import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MapLoadError } from '../../map/kakaoLoader';
import { createMemoryMapView } from '../../map/MemoryMapView';
import type { MemoryMapView } from '../../map/types';
import { MemoryMap } from './MemoryMap';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

vi.mock('../../map/MemoryMapView', () => ({ createMemoryMapView: vi.fn() }));

function fakeView(): MemoryMapView {
  return {
    showPins: vi.fn(),
    onPinClick: vi.fn(() => () => {}),
    setSelected: vi.fn(),
    fitTo: vi.fn(),
    relayout: vi.fn(),
    destroy: vi.fn(),
  };
}

describe('MemoryMap', () => {
  let container: HTMLDivElement;
  let root: Root;
  const createView = vi.mocked(createMemoryMapView);
  const retryButton = () =>
    [...container.querySelectorAll('button')].find((b) => b.textContent === '지도 다시 불러오기');
  const pins: never[] = [];

  beforeEach(() => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      },
    );
    createView.mockReset();
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  async function mount() {
    await act(async () => root.render(<MemoryMap pins={pins} selectedId={null} onSelect={() => {}} />));
  }

  it('지도를 불러오지 못한 뒤 "지도 다시 불러오기"를 누르면 지도를 다시 만들고 안내를 걷는다', async () => {
    const view = fakeView();
    createView.mockRejectedValueOnce(new MapLoadError('LOAD_FAILED')).mockResolvedValueOnce(view);
    await mount();
    expect(container.textContent).toContain('지도를 볼 수 없어요');

    await act(async () => retryButton()?.click());

    expect(createView).toHaveBeenCalledTimes(2);
    expect(container.textContent).not.toContain('지도를 볼 수 없어요');
    expect(view.showPins).toHaveBeenCalled();
    expect(createView.mock.calls[1]?.[0]).not.toBe(createView.mock.calls[0]?.[0]);
    expect(container.querySelector('[role="region"]')?.children).toHaveLength(1);
  });

  it('다시 시도해도 실패하면 안내와 버튼이 다시 나온다', async () => {
    createView.mockRejectedValue(new MapLoadError('LOAD_FAILED'));
    await mount();

    await act(async () => retryButton()?.click());

    expect(createView).toHaveBeenCalledTimes(2);
    expect(container.textContent).toContain('지도를 볼 수 없어요');
    expect(retryButton()).toBeDefined();
  });
});
