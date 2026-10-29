import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { useDocumentTitle } from './useDocumentTitle';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function Probe({ title, resetOnLeave }: { title: string | null; resetOnLeave?: boolean }) {
  useDocumentTitle(title, { resetOnLeave });
  return null;
}

describe('useDocumentTitle', () => {
  let root: Root;

  beforeEach(() => {
    document.title = '처음';
    root = createRoot(document.createElement('div'));
  });

  afterEach(() => act(() => root.unmount()));

  it('받은 제목을 문서 제목으로 두고, 제목이 바뀌면 따라 바꾼다', async () => {
    await act(async () => root.render(<Probe title="알림 · 뫼와들" />));
    expect(document.title).toBe('알림 · 뫼와들');

    await act(async () => root.render(<Probe title="피드 · 뫼와들" />));
    expect(document.title).toBe('피드 · 뫼와들');
  });

  it('제목이 null이면 문서 제목을 건드리지 않는다', async () => {
    await act(async () => root.render(<Probe title={null} />));
    expect(document.title).toBe('처음');
  });

  it('화면을 떠나도 제목을 그대로 두지만, resetOnLeave를 주면 서비스 이름으로 되돌린다', async () => {
    await act(async () => root.render(<Probe title="알림 · 뫼와들" />));
    await act(async () => root.render(null));
    expect(document.title).toBe('알림 · 뫼와들');

    await act(async () => root.render(<Probe title="설악산 · 뫼와들" resetOnLeave />));
    await act(async () => root.render(null));
    expect(document.title).toBe('뫼와들');
  });
});
