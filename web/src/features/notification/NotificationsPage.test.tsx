import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchNotifications, fetchNotificationSettings, markAllNotificationsRead } from '../../api/notifications';
import type { NotificationItem, Page } from '../../api/types';
import { NotificationsPage } from './NotificationsPage';

vi.mock('../../api/notifications', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/notifications')>()),
  fetchNotifications: vi.fn(),
  fetchNotificationSettings: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}));

vi.mock('./unreadCount', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./unreadCount')>()),
  refreshUnreadCount: vi.fn(),
}));

vi.mock('../member/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../member/session')>()),
  useSession: () => ({ status: 'authenticated', me: { memberId: 1, nickname: '산사람' } }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function notification(id: number, readAt: string | null): NotificationItem {
  return {
    notificationId: id,
    type: 'BASECAMP_APPROVED',
    title: `알림${id}`,
    body: '',
    link: null,
    readAt,
    createdAt: '2026-10-01T00:00:00Z',
  } as NotificationItem;
}

function page(items: NotificationItem[], pageNumber: number, hasNext: boolean): Page<NotificationItem> {
  return { content: items, page: pageNumber, size: 20, hasNext } as Page<NotificationItem>;
}

describe('알림 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const unreadCount = () => container.querySelectorAll('section[aria-label="알림 목록"] .sr-only').length;

  beforeEach(async () => {
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    vi.mocked(fetchNotificationSettings).mockResolvedValue([]);
    vi.mocked(markAllNotificationsRead).mockResolvedValue(undefined as never);
    vi.mocked(fetchNotifications).mockImplementation(async (pageNumber) =>
      pageNumber === 0
        ? page([notification(1, null), notification(2, null)], 0, true)
        : page([notification(3, null)], 1, false),
    );
    await act(async () => root.render(<NotificationsPage />));
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('더 보기를 누르면 다음 쪽 알림이 아래에 붙고 더 보기 버튼이 사라진다', async () => {
    expect(container.textContent).not.toContain('알림3');

    await act(async () => button('더 보기')?.click());

    expect(container.textContent).toMatch(/알림1.*알림2.*알림3/);
    expect(button('더 보기')).toBeUndefined();
  });

  it('모두 읽음 표시를 누르면 받아 둔 알림이 모두 읽은 상태가 된다', async () => {
    await act(async () => button('더 보기')?.click());
    expect(unreadCount()).toBe(3);

    await act(async () => button('모두 읽음 표시')?.click());

    expect(markAllNotificationsRead).toHaveBeenCalledTimes(1);
    expect(unreadCount()).toBe(0);
  });
});
