import type { ReactNode } from 'react';
import { serverTraceId, toUserMessage } from '../api/errors';
import { Notice } from './Notice';

type ErrorNoticeProps = {
  title: string;
  // 요청에서 받은 오류다. 화면이 직접 만든 안내 문구만 있으면 문자열로 넘긴다.
  error: unknown;
  // 오류 코드 표와 다른 문구를 보여 줄 때 넘긴다. 없으면 오류에서 문구를 고른다.
  message?: string;
  // 오류 객체를 문자열로 바꿔 둔 목록 상태처럼 error에서 ID를 읽을 수 없을 때 넘긴다.
  traceId?: string;
  children?: ReactNode;
};

// 요청이 실패했을 때 쓰는 위험 안내 상자다.
// 서버가 5xx로 답한 예상 못 한 오류에는 추적 ID를 작게 붙인다. 회원이 문의할 때 운영자가 그 번호로 서버 로그를 찾는다.
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
