import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import {
  addAdminDisposableEmailDomain,
  deleteAdminDisposableEmailDomain,
  fetchAdminDisposableEmailDomains,
} from '../../api/admin';
import type { AdminDisposableEmailDomain } from '../../api/types';
import { Button } from '../../components/Button';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Skeleton } from '../../components/Skeleton';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstDateTime } from '../../lib/datetime';
import { AdminPage } from './AdminGate';

const DOMAIN_PATTERN = /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$/i;
const DOMAIN_MAX_LENGTH = 253;

type ListState = { items: AdminDisposableEmailDomain[]; error: null } | { items: null; error: unknown };

export function AdminEmailDomainsPage() {
  return (
    <AdminPage title="가입 차단 도메인" current="/admin/email-domains">
      <EmailDomains />
    </AdminPage>
  );
}

function EmailDomains() {
  const [tick, setTick] = useState(0);
  const [list, setList] = useState<ListState | null>(null);
  const showSkeleton = useDelayedFlag(list === null);

  useEffect(() => {
    const controller = new AbortController();
    fetchAdminDisposableEmailDomains(controller.signal).then(
      (items) => {
        if (!controller.signal.aborted) setList({ items, error: null });
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) setList({ items: null, error: caught });
      },
    );
    return () => controller.abort();
  }, [tick]);

  const reload = () => setTick((value) => value + 1);

  return (
    <section className="flex flex-col gap-6" aria-label="가입 차단 도메인">
      <p className="text-base text-ink-muted">
        공개된 일회용 이메일 목록에 더해, 여기에 넣은 도메인과 그 하위 도메인의 이메일로는 가입할 수 없어요. 이미 가입한
        회원에게는 영향이 없어요.
      </p>
      <AddDomainForm onAdded={reload} />
      {list?.error != null && (
        <div role="alert" className="flex flex-col gap-3">
          <ErrorNotice title="도메인 목록을 불러오지 못했어요" error={list.error} />
          <div>
            <Button variant="secondary" onClick={reload}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}
      {list === null && showSkeleton && <Skeleton className="h-40 w-full" />}
      {list?.items?.length === 0 && (
        <EmptyState framed title="직접 추가한 도메인이 없어요" description="추가한 도메인이 여기에 모여요." />
      )}
      {list?.items != null && list.items.length > 0 && (
        <ul className="flex flex-col gap-2" aria-label="추가한 도메인">
          {list.items.map((item) => (
            <DomainRow key={item.id} item={item} onDeleted={reload} />
          ))}
        </ul>
      )}
    </section>
  );
}

function AddDomainForm({ onAdded }: { onAdded: () => void }) {
  const [value, setValue] = useState('');
  const [invalid, setInvalid] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const domain = value.trim();
    if (!DOMAIN_PATTERN.test(domain) || domain.length > DOMAIN_MAX_LENGTH) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    setBusy(true);
    setError(null);
    try {
      await addAdminDisposableEmailDomain(domain);
      setValue('');
      onAdded();
    } catch (caught) {
      setError(caught);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="flex flex-col gap-3" onSubmit={(event) => void submit(event)} noValidate>
      <div className="flex flex-wrap items-start gap-2">
        <TextField
          label="도메인"
          className="min-w-0 flex-1"
          value={value}
          autoComplete="off"
          spellCheck={false}
          maxLength={DOMAIN_MAX_LENGTH}
          placeholder="mailinator.com"
          hint="@ 뒤의 도메인만 적어 주세요."
          error={invalid ? '도메인 형식이 아니에요. mailinator.com처럼 적어 주세요.' : undefined}
          onChange={(event) => setValue(event.target.value)}
        />
        <Button type="submit" className="mt-6" loading={busy}>
          추가
        </Button>
      </div>
      {error !== null && (
        <div role="alert">
          <ErrorNotice title="도메인을 추가하지 못했어요" error={error} />
        </div>
      )}
    </form>
  );
}

function DomainRow({ item, onDeleted }: { item: AdminDisposableEmailDomain; onDeleted: () => void }) {
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function remove() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await deleteAdminDisposableEmailDomain(item.id);
      setConfirm(false);
      onDeleted();
    } catch (caught) {
      setError(caught);
      setConfirm(false);
      setBusy(false);
    }
  }

  return (
    <li className="flex flex-col gap-2 rounded-row bg-fill px-4 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex min-w-0 flex-col">
          <span className="break-all font-mono text-base text-ink">{item.domain}</span>
          <span className="text-sm text-ink-muted">
            추가한 때 <span className="font-mono tabular-nums">{formatKstDateTime(item.createdAt)}</span>
          </span>
        </div>
        <Button variant="secondary" disabled={busy} onClick={() => setConfirm(true)}>
          삭제
        </Button>
      </div>
      {error !== null && (
        <div role="alert">
          <ErrorNotice title="도메인을 삭제하지 못했어요" error={error} />
        </div>
      )}
      {confirm && (
        <Dialog title="도메인을 삭제할까요?" onClose={() => !busy && setConfirm(false)}>
          <div className="flex flex-col gap-4">
            <p className="text-base text-ink">삭제하면 {item.domain} 이메일로 다시 가입할 수 있어요.</p>
            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="secondary" disabled={busy} onClick={() => setConfirm(false)}>
                취소
              </Button>
              <Button variant="danger" loading={busy} onClick={() => void remove()}>
                삭제
              </Button>
            </div>
          </div>
        </Dialog>
      )}
    </li>
  );
}
