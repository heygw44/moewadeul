import { Link } from '../../app/router';
import { Icon } from '../../components/icons';
import { useSession } from '../member/session';
import { notificationLabel, useUnreadCount } from './unreadCount';

export function UnreadBadge({ count }: { count: number }) {
  if (count <= 0) return null;
  return (
    <span
      aria-hidden="true"
      className="rounded-control bg-danger px-1.5 font-mono text-xs font-semibold tabular-nums text-white"
    >
      {count > 99 ? '99+' : count}
    </span>
  );
}

export function NotificationLink() {
  const session = useSession();
  const count = useUnreadCount();

  if (session.status !== 'authenticated' || session.me === null) return null;

  return (
    <Link
      to="/me/notifications"
      aria-label={notificationLabel(count)}
      className="inline-flex min-h-11 items-center gap-2 rounded-control bg-fill px-4 text-ink hover:bg-fill-strong"
    >
      <Icon name="bell" size={20} />
      <span className="text-sm">알림</span>
      <UnreadBadge count={count} />
    </Link>
  );
}

export function NotificationBellLink({ className }: { className: string }) {
  const session = useSession();
  const count = useUnreadCount();

  if (session.status !== 'authenticated' || session.me === null) return null;

  return (
    <Link to="/me/notifications" aria-label={notificationLabel(count)} className={className}>
      <Icon name="bell" size={20} />
      <UnreadBadge count={count} />
    </Link>
  );
}
