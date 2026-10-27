import type { ReactNode } from 'react';
import { serverTraceId, toUserMessage } from '../api/errors';
import { Notice } from './Notice';

type ErrorNoticeProps = {
  title: string;
  error: unknown;
  message?: string;
  traceId?: string;
  children?: ReactNode;
};

export function ErrorNotice({ title, error, message, traceId, children }: ErrorNoticeProps) {
  const text = message ?? (typeof error === 'string' ? error : toUserMessage(error));
  const trace = traceId ?? serverTraceId(error);
  return (
    <Notice tone="danger" title={title}>
      <p>{text}</p>
      {children}
      {trace && <p className="mt-1 font-mono text-xs text-ink-muted">문의 번호 {trace}</p>}
    </Notice>
  );
}
