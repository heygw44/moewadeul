import { useEffect, useState } from 'react';
import {
  fetchNotifications,
  fetchNotificationSettings,
  markAllNotificationsRead,
  markNotificationRead,
  updateNotificationSettings,
} from '../../api/notifications';
import type { NotificationItem, NotificationSetting, NotificationType } from '../../api/types';
import { navigate } from '../../app/router';
import { Button } from '../../components/Button';
import { Checkbox } from '../../components/Checkbox';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { ListPanel } from '../../components/HubLayout';
import { PageCard, PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { useDocumentTitle } from '../../components/useDocumentTitle';
import { usePagedList } from '../../components/usePagedList';
import { formatRelativeKst } from '../../lib/datetime';
import { GatedButton } from '../../app/session/GatedButton';
import { safeInternalPath } from '../../app/session/nextPath';
import { useRequireAuth } from '../../app/session/useRequireAuth';
import { NOTIFICATION_LABELS, NOTIFICATION_TYPES } from './notificationLabels';
import { refreshUnreadCount } from './unreadCount';

const itemKey = (item: NotificationItem) => item.notificationId;

export function NotificationsPage() {
  const session = useRequireAuth();
  const showSkeleton = useDelayedFlag(session.status === 'loading');

  useDocumentTitle('알림 · 뫼와들');

  if (session.status !== 'authenticated' || !session.me) {
    return <PageLayout title="알림">{showSkeleton ? <Skeleton className="h-40 w-full" /> : null}</PageLayout>;
  }

  return <NotificationList />;
}

function fetchPage(_filter: string, page: number, signal?: AbortSignal) {
  return fetchNotifications(page, signal);
}

function NotificationList() {
  const { current: state, loadingMore, loadMore, updateItems, retry } = usePagedList(true, '', fetchPage, itemKey);
  const [markingAll, setMarkingAll] = useState(false);
  const [actionError, setActionError] = useState<{ cause: unknown } | null>(null);
  const showSkeleton = useDelayedFlag(state === null);

  function markLocallyRead(notificationId: number) {
    const readAt = new Date().toISOString();
    updateItems((items) =>
      items.map((item) =>
        item.notificationId === notificationId && item.readAt === null ? { ...item, readAt } : item,
      ),
    );
  }

  async function open(item: NotificationItem) {
    setActionError(null);
    const target = item.link ? safeInternalPath(item.link) : null;
    try {
      if (item.readAt === null) {
        await markNotificationRead(item.notificationId);
        markLocallyRead(item.notificationId);
        void refreshUnreadCount();
      }
    } catch (caught) {
      if (target === null) {
        setActionError({ cause: caught });
        return;
      }
    }
    if (target !== null) navigate(target);
  }

  async function readAll() {
    if (markingAll) return;
    setMarkingAll(true);
    setActionError(null);
    try {
      await markAllNotificationsRead();
      const readAt = new Date().toISOString();
      updateItems((items) => items.map((item) => (item.readAt === null ? { ...item, readAt } : item)));
      void refreshUnreadCount();
    } catch (caught) {
      setActionError({ cause: caught });
    } finally {
      setMarkingAll(false);
    }
  }

  if (state === null) {
    return (
      <PageLayout title="알림">
        {showSkeleton ? <Skeleton className="h-40 w-full" /> : null}
        <EmailSettingsCard />
      </PageLayout>
    );
  }

  const hasUnread = state.items.some((item) => item.readAt === null);

  return (
    <PageLayout
      title="알림"
      actions={
        hasUnread ? (
          <Button variant="secondary" loading={markingAll} onClick={() => void readAll()}>
            모두 읽음 표시
          </Button>
        ) : undefined
      }
    >
      <section className="flex flex-col gap-3" aria-label="알림 목록">
        {(state.error || actionError) && (
          <div role="alert" className="flex flex-col gap-3">
            {state.error ? (
              <ErrorNotice title="알림을 불러오지 못했어요" error={state.error} traceId={state.traceId ?? undefined} />
            ) : (
              <ErrorNotice title="읽음으로 바꾸지 못했어요" error={actionError?.cause} />
            )}
            {state.error && state.items.length === 0 && (
              <div>
                <Button variant="secondary" onClick={retry}>
                  다시 불러오기
                </Button>
              </div>
            )}
          </div>
        )}
        {state.items.length === 0 && !state.error ? (
          <EmptyState framed title="알림이 없어요" description="합류 신청이나 신고 처리 결과가 오면 여기에 보여요." />
        ) : (
          <ListPanel>
            {state.items.map((item) => (
              <NotificationRow key={item.notificationId} item={item} onOpen={() => void open(item)} />
            ))}
          </ListPanel>
        )}
        {state.hasNext && (
          <div>
            <Button variant="secondary" loading={loadingMore} onClick={() => void loadMore()}>
              더 보기
            </Button>
          </div>
        )}
      </section>
      <EmailSettingsCard />
    </PageLayout>
  );
}

function NotificationRow({ item, onOpen }: { item: NotificationItem; onOpen: () => void }) {
  const unread = item.readAt === null;
  return (
    <li>
      <button
        type="button"
        onClick={onOpen}
        className="flex min-h-11 w-full items-start gap-3 bg-surface px-4 py-3 text-left hover:bg-fill"
      >
        <span className="flex w-2 shrink-0 justify-center pt-2">
          {unread && (
            <>
              <span aria-hidden="true" className="size-2 rounded-full bg-brand" />
              <span className="sr-only">안 읽음</span>
            </>
          )}
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-1">
          <span className={`text-base text-ink ${unread ? 'font-bold' : ''}`}>{item.title}</span>
          <span className="whitespace-pre-line break-words text-sm text-ink">{item.body}</span>
          <span className="font-mono text-sm tabular-nums text-ink-muted">{formatRelativeKst(item.createdAt)}</span>
        </span>
      </button>
    </li>
  );
}

type SettingsState =
  | { status: 'loading' }
  | { status: 'error'; cause: unknown }
  | { status: 'ready'; enabled: Record<NotificationType, boolean> };

function toEnabledMap(items: NotificationSetting[]): Record<NotificationType, boolean> {
  const map = Object.fromEntries(NOTIFICATION_TYPES.map((type) => [type, true])) as Record<NotificationType, boolean>;
  for (const item of items) {
    if (item.type in map) map[item.type] = item.emailEnabled;
  }
  return map;
}

type SaveResult = 'saved' | { failed: unknown } | null;

function EmailSettingsCard() {
  const [state, setState] = useState<SettingsState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<SaveResult>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchNotificationSettings(controller.signal).then(
      (items) => {
        if (!controller.signal.aborted) setState({ status: 'ready', enabled: toEnabledMap(items) });
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) setState({ status: 'error', cause: caught });
      },
    );
    return () => controller.abort();
  }, [attempt]);

  function toggle(type: NotificationType, checked: boolean) {
    setResult(null);
    setState((previous) =>
      previous.status === 'ready' ? { status: 'ready', enabled: { ...previous.enabled, [type]: checked } } : previous,
    );
  }

  async function save() {
    if (state.status !== 'ready' || saving) return;
    setSaving(true);
    setResult(null);
    try {
      const saved = await updateNotificationSettings(
        NOTIFICATION_TYPES.map((type) => ({ type, emailEnabled: state.enabled[type] })),
      );
      setState({ status: 'ready', enabled: toEnabledMap(saved) });
      setResult('saved');
    } catch (caught) {
      setResult({ failed: caught });
    } finally {
      setSaving(false);
    }
  }

  return (
    <PageCard title="이메일 수신 설정">
      <p className="text-sm text-ink-muted">꺼도 알림함에는 알림이 계속 쌓여요.</p>
      {state.status === 'loading' && <Skeleton className="h-40 w-full" />}
      {state.status === 'error' && (
        <div className="flex flex-col gap-2">
          <ErrorNotice title="이메일 수신 설정을 불러오지 못했어요" error={state.cause} />
          <div>
            <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}
      {state.status === 'ready' && (
        <>
          <fieldset className="flex min-w-0 flex-col">
            <legend className="sr-only">이메일로 받을 알림</legend>
            {NOTIFICATION_TYPES.map((type) => (
              <Checkbox
                key={type}
                label={NOTIFICATION_LABELS[type]}
                checked={state.enabled[type]}
                disabled={saving}
                onChange={(event) => toggle(type, event.target.checked)}
                className="rounded-row"
              />
            ))}
          </fieldset>
          {result === 'saved' && (
            <p role="status" className="text-sm font-semibold text-accent-deep">
              저장했어요.
            </p>
          )}
          {result !== null && result !== 'saved' && (
            <div role="alert">
              <ErrorNotice title="저장하지 못했어요" error={result.failed} />
            </div>
          )}
          <div>
            <GatedButton
              variant="primary"
              loading={saving}
              unverifiedReason="이메일 인증을 마치면 저장할 수 있어요"
              onClick={() => void save()}
            >
              저장
            </GatedButton>
          </div>
        </>
      )}
    </PageCard>
  );
}
