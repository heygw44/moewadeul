import { describe, expect, it, vi } from 'vitest';
import { loadFontsForFirstScreen } from './fonts';

describe('loadFontsForFirstScreen', () => {
  it('지도가 아닌 화면은 SDK를 기다리지 않고 글꼴을 바로 받는다', async () => {
    const loadSdk = vi.fn(() => new Promise<void>(() => undefined));
    const load = vi.fn(() => Promise.resolve());

    await loadFontsForFirstScreen(false, loadSdk, load);

    expect(load).toHaveBeenCalledOnce();
    expect(loadSdk).not.toHaveBeenCalled();
  });

  it('지도 화면은 SDK가 준비된 뒤에 글꼴을 받는다', async () => {
    const order: string[] = [];
    const loadSdk = () =>
      new Promise<void>((resolve) =>
        setTimeout(() => {
          order.push('sdk');
          resolve();
        }, 0),
      );
    const load = () => {
      order.push('fonts');
      return Promise.resolve();
    };

    await loadFontsForFirstScreen(true, loadSdk, load);

    expect(order).toEqual(['sdk', 'fonts']);
  });

  it('지도 SDK를 받지 못해도 글꼴은 받는다', async () => {
    const load = vi.fn(() => Promise.resolve());

    await loadFontsForFirstScreen(true, () => Promise.reject(new Error('SDK 실패')), load);

    expect(load).toHaveBeenCalledOnce();
  });
});
