import { useState } from 'react';
import type { FormEvent } from 'react';
import { fetchAdminAuditLogs } from '../../api/admin';
import type { AdminAuditLog, Page, PageRequest } from '../../api/types';
import { Link } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { SelectField } from '../../components/SelectField';
import { Skeleton } from '../../components/Skeleton';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { usePagedList } from '../../components/usePagedList';
import { formatKstDateTimeWithYear } from '../../lib/datetime';
import { AdminPage } from './AdminGate';
import {
  EMPTY_AUDIT_FORM,
  EMPTY_AUDIT_QUERY,
  hasAuditCondition,
  hasAuditFormInput,
  toAuditQuery,
} from './adminAuditFilter';
import type { AuditFilterErrors, AuditFilterForm, AuditQuery } from './adminAuditFilter';
import {
  AUDIT_ACTION_LABELS,
  AUDIT_TARGET_LABELS,
  AUDIT_TARGET_TYPES,
  auditTargetPath,
  summarizeAuditDetail,
} from './adminAuditLabels';

const itemKey = (item: AdminAuditLog) => item.auditLogId;

const TARGET_OPTIONS = [
  { value: '', label: '전체' },
  ...AUDIT_TARGET_TYPES.map((type) => ({ value: type, label: AUDIT_TARGET_LABELS[type] })),
];

function fetchLogs(filter: AuditQuery, { page }: PageRequest, signal?: AbortSignal): Promise<Page<AdminAuditLog>> {
  return fetchAdminAuditLogs({ ...filter, page }, signal);
}

export function AdminAuditLogsPage() {
  return (
    <AdminPage title="감사 기록" current="/admin/audit-logs">
      <AuditLogList />
    </AdminPage>
  );
}

function AuditLogList() {
  const [form, setForm] = useState<AuditFilterForm>(EMPTY_AUDIT_FORM);
  const [errors, setErrors] = useState<AuditFilterErrors>({});
  const [applied, setApplied] = useState<AuditQuery>(EMPTY_AUDIT_QUERY);
  const { current, loadingMore, loadMore, retry, reload } = usePagedList(true, applied, fetchLogs, itemKey);
  const showSkeleton = useDelayedFlag(current === null);
  const filtered = hasAuditCondition(applied);

  function edit(patch: Partial<AuditFilterForm>) {
    setForm((previous) => ({ ...previous, ...patch }));
    setErrors({});
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const result = toAuditQuery(form);
    if (!result.ok) {
      setErrors(result.errors);
      return;
    }
    setErrors({});
    if (JSON.stringify(result.query) === JSON.stringify(applied)) reload();
    else setApplied(result.query);
  }

  function clear() {
    setForm(EMPTY_AUDIT_FORM);
    setErrors({});
    setApplied(EMPTY_AUDIT_QUERY);
  }

  return (
    <section className="flex flex-col gap-4" aria-label="감사 기록">
      <form className="flex flex-wrap items-start gap-3" aria-label="기록 조건" onSubmit={submit} noValidate>
        <SelectField
          className="w-full sm:w-52"
          label="대상 종류"
          options={TARGET_OPTIONS}
          value={form.targetType}
          onChange={(event) => edit({ targetType: event.target.value })}
        />
        <TextField
          className="w-full sm:w-40"
          label="관리자 번호"
          inputMode="numeric"
          value={form.adminId}
          error={errors.adminId}
          onChange={(event) => edit({ adminId: event.target.value })}
        />
        <TextField
          className="w-full sm:w-44"
          label="시작일"
          type="date"
          value={form.startDate}
          onChange={(event) => edit({ startDate: event.target.value })}
        />
        <TextField
          className="w-full sm:w-44"
          label="종료일"
          type="date"
          value={form.endDate}
          error={errors.endDate}
          onChange={(event) => edit({ endDate: event.target.value })}
        />
        <div className="flex w-full flex-wrap gap-2 sm:mt-6 sm:w-auto">
          <Button type="submit">조회</Button>
          {(filtered || hasAuditFormInput(form)) && (
            <Button variant="secondary" onClick={clear}>
              조건 지우기
            </Button>
          )}
        </div>
      </form>
      {current?.error && (
        <div role="alert" className="flex flex-col gap-3">
          <ErrorNotice
            title="감사 기록을 불러오지 못했어요"
            error={current.error}
            traceId={current.traceId ?? undefined}
          />
          {current.failedOn === 'first' && (
            <div>
              <Button variant="secondary" onClick={retry}>
                다시 불러오기
              </Button>
            </div>
          )}
        </div>
      )}
      {current === null && showSkeleton && <Skeleton className="h-40 w-full" />}
      {current !== null && current.items.length === 0 && !current.error && (
        <EmptyState
          framed
          title={filtered ? '조건에 맞는 기록이 없어요' : '아직 관리자 기록이 없어요'}
          action={
            filtered ? (
              <Button variant="secondary" onClick={clear}>
                조건 지우기
              </Button>
            ) : undefined
          }
        />
      )}
      {current !== null && current.items.length > 0 && (
        <ul className="flex flex-col gap-1 rounded-sheet bg-fill p-1.5" aria-label="감사 기록 목록">
          {current.items.map((item) => (
            <AuditLogRow key={item.auditLogId} item={item} />
          ))}
        </ul>
      )}
      {current?.hasNext && (
        <div>
          <Button variant="secondary" loading={loadingMore} onClick={() => void loadMore()}>
            더 보기
          </Button>
        </div>
      )}
    </section>
  );
}

function AuditLogRow({ item }: { item: AdminAuditLog }) {
  const targetLabel = AUDIT_TARGET_LABELS[item.targetType];
  const targetPath = auditTargetPath(item.targetType, item.targetId);
  const target = `${targetLabel} ${item.targetId}번`;
  const entries = summarizeAuditDetail(item.detail, item.targetType);
  return (
    <li className="flex min-w-0 flex-col gap-1 rounded-row bg-surface px-4 py-3">
      <p className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <span className="text-base font-bold text-ink">{AUDIT_ACTION_LABELS[item.action]}</span>
        <span className="font-mono text-sm tabular-nums text-ink-muted">
          {formatKstDateTimeWithYear(item.createdAt)}
        </span>
      </p>
      <p className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-ink-muted">
        <span className="min-w-0 break-words">
          대상{' '}
          {targetPath === null ? (
            <span className="text-ink">{target}</span>
          ) : (
            <Link to={targetPath} className="text-ink underline">
              {target}
            </Link>
          )}
        </span>
        <span>관리자 {item.adminId}번</span>
      </p>
      {entries.length > 0 && (
        <p className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-ink-muted">
          {entries.map((entry) => (
            <span key={entry.key} className="min-w-0 break-words">
              {entry.label}: <span className="text-ink">{entry.text}</span>
            </span>
          ))}
        </p>
      )}
    </li>
  );
}
