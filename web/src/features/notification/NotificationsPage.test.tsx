import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchNotifications,
  fetchNotificationSettings,
  markAllNotificationsRead,
  updateNotificationSettings,
} from '../../api/notifications';
import type { NotificationItem, NotificationSetting, Page } from '../../api/types';
import { NotificationsPage } from './NotificationsPage';

vi.mock('../../api/notifications', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/notifications')>()),
  fetchNotifications: vi.fn(),
  fetchNotificationSettings: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  updateNotificationSettings: vi.fn(),
}));

vi.mock('./unreadCount', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./unreadCount')>()),
  refreshUnreadCount: vi.fn(),
}));

const session = vi.hoisted(() => ({ memberStatus: 'ACTIVE' }));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => ({
    status: 'authenticated',
    me: { memberId: 1, nickname: '산사람', status: session.memberStatus },
  }),
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
    session.memberStatus = 'ACTIVE';
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

  it('이메일 인증을 마치지 않은 회원에게는 알림 설정 저장 버튼이 잠겨 있다', async () => {
    session.memberStatus = 'UNVERIFIED';
    await act(async () => root.render(<NotificationsPage key="unverified" />));

    expect(button('저장')?.disabled).toBe(true);
    expect(container.textContent).toContain('이메일 인증을 마치면 저장할 수 있어요');
  });

  it('이메일 인증을 마친 회원은 알림 설정을 저장할 수 있다', () => {
    expect(button('저장')?.disabled).toBe(false);
  });

  it('빈자리 알림 이메일 수신을 끄고 저장하면 서버로 꺼짐이 가고, 다시 열어도 꺼져 있다', async () => {
    const seatReleased = () =>
      [...container.querySelectorAll('label')]
        .find((label) => label.textContent?.includes('빈자리 알림을 신청한 행사에 자리가 났을 때'))
        ?.querySelector('input');
    let stored: NotificationSetting[] = [{ type: 'PROGRAM_SEAT_RELEASED', emailEnabled: true }];
    vi.mocked(fetchNotificationSettings).mockImplementation(async () => stored);
    vi.mocked(updateNotificationSettings).mockImplementation(async (items) => (stored = items));
    await act(async () => root.render(<NotificationsPage key="settings" />));
    expect(seatReleased()?.checked).toBe(true);

    await act(async () => seatReleased()?.click());
    await act(async () => button('저장')?.click());

    const sent = vi.mocked(updateNotificationSettings).mock.calls[0]?.[0];
    expect(sent).toContainEqual({ type: 'PROGRAM_SEAT_RELEASED', emailEnabled: false });

    await act(async () => root.render(<NotificationsPage key="reopened" />));
    expect(seatReleased()?.checked).toBe(false);
  });
});
