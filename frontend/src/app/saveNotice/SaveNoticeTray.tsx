import { Icon } from '../../components/icons';
import { Link } from '../router';
import { dismissSaveNotice, useSaveNotices } from './saveNotices';
import type { SaveNotice } from './saveNotices';

const LINK_CLASS = 'inline-flex min-h-11 items-center text-sm text-accent underline underline-offset-2';

export function SaveNoticeTray() {
  const notices = useSaveNotices();
  return (
    <div
      role="status"
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-(--bottom-nav-h) z-30 flex justify-center px-4"
    >
      {notices.length > 0 && (
        <ul className="flex w-full max-w-md flex-col gap-2 pb-4">
          {notices.map((notice) => (
            <NoticeCard key={notice.id} notice={notice} />
          ))}
        </ul>
      )}
    </div>
  );
}

function NoticeCard({ notice }: { notice: SaveNotice }) {
  const failed = notice.state === 'failed';
  const link = notice.state === 'pending' ? undefined : notice.link;
  return (
    <li
      className={`pointer-events-auto flex items-start gap-2 rounded-sheet py-3 pl-4 pr-2 shadow-raise ${failed ? 'bg-danger-soft' : 'bg-surface'}`}
    >
      <div className="flex min-w-0 flex-1 flex-col py-1">
        <p className={`text-base font-semibold ${failed ? 'text-danger' : 'text-ink'}`}>{notice.title}</p>
        {notice.state === 'failed' && <p className="mt-1 text-sm text-ink">{notice.body}</p>}
        {link && (
          <div>
            <Link to={link.to} className={LINK_CLASS} onClick={() => dismissSaveNotice(notice.id)}>
              {link.label}
            </Link>
          </div>
        )}
      </div>
      {notice.state !== 'pending' && (
        <button
          type="button"
          aria-label="알림 닫기"
          onClick={() => dismissSaveNotice(notice.id)}
          className="inline-flex size-11 shrink-0 items-center justify-center rounded-control text-ink-muted hover:text-ink"
        >
          <Icon name="close" size={18} />
        </button>
      )}
    </li>
  );
}
