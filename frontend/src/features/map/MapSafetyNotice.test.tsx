import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { beforeEach, describe, expect, it } from 'vitest';
import { MapSafetyNotice } from './MapSafetyNotice';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

describe('지도 첫 방문 안내', () => {
  beforeEach(() => window.localStorage.clear());

  it('[SF-06] 첫 방문에 관할 기관 확인 안내를 보여 주고, 확인하면 다시 보여 주지 않는다', async () => {
    const container = document.createElement('div');
    const root = createRoot(container);
    await act(async () => root.render(<MapSafetyNotice />));

    expect(container.textContent).toContain('박지는 회원이 제보한 곳이에요.');
    expect(container.textContent).toContain('관할 기관에 직접 확인해 주세요.');

    await act(async () => container.querySelector('button')?.click());
    expect(container.textContent).toBe('');

    await act(async () => root.unmount());
    const revisit = document.createElement('div');
    const again = createRoot(revisit);
    await act(async () => again.render(<MapSafetyNotice />));
    expect(revisit.textContent).toBe('');
    await act(async () => again.unmount());
  });
});
