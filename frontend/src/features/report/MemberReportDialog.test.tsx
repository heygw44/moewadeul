import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemberReportDialog } from './MemberReportDialog';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

describe('회원 신고 대화상자', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(async () => {
    container = document.createElement('div');
    document.body.appendChild(container);
    document.cookie = 'XSRF-TOKEN=token';
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.open = true;
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.open = false;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(JSON.stringify({ reportId: 1 }), {
            status: 201,
            headers: { 'Content-Type': 'application/json' },
          }),
      ),
    );
    root = createRoot(container);
    await act(async () => {
      root.render(
        <MemberReportDialog
          kind="REVIEW"
          targetMemberId={7}
          targetNickname="새벽능선"
          basecampId={3}
          companionReviewId={9}
          onClose={() => {}}
        />,
      );
    });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('신고를 접수하면 결과를 알림으로 보낸다고 알리고 급할 때 쓸 운영자 메일을 보인다', async () => {
    const textarea = container.querySelector('textarea') as HTMLTextAreaElement;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')?.set?.call(
        textarea,
        '후기에 욕설이 있어요',
      );
      textarea.dispatchEvent(new Event('input', { bubbles: true }));
    });
    const submit = [...container.querySelectorAll('button')].find((element) => element.textContent === '신고하기');
    await act(async () => submit?.click());

    expect(container.textContent).toContain('신고를 접수했어요');
    expect(container.textContent).toContain('급히 알릴 일이 있으면 moewadeul@gmail.com으로 메일을 보내 주세요.');
    expect(container.querySelector('a')?.getAttribute('href')).toBe('mailto:moewadeul@gmail.com');
  });
});
