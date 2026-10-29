import { useState } from 'react';
import type { FormEvent } from 'react';
import { createComment, deleteComment, listComments, updateComment } from '../../api/community';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { createIdempotentRunner } from '../../api/idempotency';
import type { CommunityComment } from '../../api/types';
import { Link, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { buttonClassName } from '../../components/buttonClass';
import { Dialog } from '../../components/Dialog';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { TextArea } from '../../components/TextArea';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { usePagedList } from '../../components/usePagedList';
import { formatKstDateTime, formatRelativeKst } from '../../lib/datetime';
import { withNext } from '../../app/session/nextPath';
import { UnverifiedNote } from '../../app/session/GatedButton';
import { useSession } from '../../app/session/session';
import { CommunityReportDialog } from './CommunityReportDialog';
import type { CommunityReportTarget } from './CommunityReportDialog';

const CONTENT_MAX = 1000;

type CommunityCommentsProps = {
  postId: number;
  count: number;
  onChanged: () => void;
};

export function CommunityComments({ postId, count, onChanged }: CommunityCommentsProps) {
  const session = useSession();
  const myMemberId = session.status === 'authenticated' ? (session.me?.memberId ?? null) : null;
  const { current: list, loadingMore, loadMore, retry, reload } = usePagedList(true, postId, listComments);
  const [replyingId, setReplyingId] = useState<number | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [deleting, setDeleting] = useState<number | null>(null);
  const [reporting, setReporting] = useState<CommunityReportTarget | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [idempotentRunner] = useState(createIdempotentRunner);
  const showSkeleton = useDelayedFlag(list === null);

  function refreshAfterChange(message: string) {
    setStatus(message);
    setReplyingId(null);
    setEditingId(null);
    onChanged();
    reload();
  }

  return (
    <section id="comments" aria-labelledby="community-comments-title" className="mt-4 flex scroll-mt-20 flex-col gap-3">
      <h2 id="community-comments-title" className="text-[1.1875rem] font-extrabold tracking-[-0.02em] text-ink">
        댓글 <span className="font-mono tabular-nums">{count}</span>
      </h2>

      <div className="rounded-sheet bg-fill p-4">
        <NewCommentArea
          onSubmit={async (content) => {
            const request = { postId, content };
            await idempotentRunner.run(request, (key) => createComment(postId, { content }, key));
            refreshAfterChange('댓글을 남겼어요.');
          }}
        />
        {status && (
          <p role="status" className="mt-2 text-sm text-accent">
            {status}
          </p>
        )}
      </div>

      {list?.error && (
        <div role="alert" className="flex flex-col gap-2">
          <Notice tone="danger" title="댓글을 불러오지 못했어요">
            <p>{list.error}</p>
          </Notice>
          <div>
            <Button variant="secondary" onClick={retry}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}

      {showSkeleton && (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-5 w-1/2" />
          <Skeleton className="h-12 w-full" />
        </div>
      )}

      {list && list.items.length === 0 && !list.error && (
        <p className="text-base text-ink-muted">아직 댓글이 없어요.</p>
      )}

      {list && (list.items.length > 0 || list.hasNext) && (
        <ul className="flex flex-col gap-1">
          {list.items.map((comment) => (
            <li key={comment.commentId} className="py-3">
              <CommentItem
                comment={comment}
                myMemberId={myMemberId}
                editingId={editingId}
                onEdit={(id) => {
                  setStatus(null);
                  setReplyingId(null);
                  setEditingId(id);
                }}
                onCancelEdit={() => setEditingId(null)}
                onSaveEdit={async (id, content) => {
                  await updateComment(id, { content });
                  refreshAfterChange('댓글을 수정했어요.');
                }}
                onDelete={setDeleting}
                onReport={(id) => setReporting({ type: 'comment', id })}
                replying={replyingId === comment.commentId}
                onReply={() => {
                  setStatus(null);
                  setEditingId(null);
                  setReplyingId(comment.commentId);
                }}
                onCancelReply={() => setReplyingId(null)}
                onSubmitReply={async (content) => {
                  const body = { content, parentId: comment.commentId };
                  await idempotentRunner.run({ postId, ...body }, (key) => createComment(postId, body, key));
                  refreshAfterChange('답글을 남겼어요.');
                }}
              />
              {comment.replies.length > 0 && (
                <ul className="mt-3 ml-4 flex flex-col gap-4 rounded-row bg-fill p-4">
                  {comment.replies.map((reply) => (
                    <li key={reply.commentId}>
                      <CommentItem
                        comment={reply}
                        myMemberId={myMemberId}
                        editingId={editingId}
                        onEdit={(id) => {
                          setStatus(null);
                          setReplyingId(null);
                          setEditingId(id);
                        }}
                        onCancelEdit={() => setEditingId(null)}
                        onSaveEdit={async (id, content) => {
                          await updateComment(id, { content });
                          refreshAfterChange('답글을 수정했어요.');
                        }}
                        onDelete={setDeleting}
                        onReport={(id) => setReporting({ type: 'comment', id })}
                        replying={replyingId === reply.commentId}
                        onReply={() => {
                          setStatus(null);
                          setEditingId(null);
                          setReplyingId(reply.commentId);
                        }}
                        onCancelReply={() => setReplyingId(null)}
                        onSubmitReply={async (content) => {
                          const body = { content, parentId: reply.commentId };
                          await idempotentRunner.run({ postId, ...body }, (key) => createComment(postId, body, key));
                          refreshAfterChange('답글을 남겼어요.');
                        }}
                      />
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
          {list.hasNext && (
            <li>
              <Button
                variant="ghost"
                fullWidth
                loading={loadingMore}
                onClick={() => void loadMore()}
                className="rounded-none"
              >
                댓글 더 보기
              </Button>
            </li>
          )}
        </ul>
      )}

      {deleting !== null && (
        <DeleteCommentDialog
          commentId={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={async () => {
            setDeleting(null);
            refreshAfterChange('댓글을 삭제했어요.');
          }}
        />
      )}
      {reporting && <CommunityReportDialog target={reporting} onClose={() => setReporting(null)} />}
    </section>
  );
}

function NewCommentArea({ onSubmit }: { onSubmit: (content: string) => Promise<void> }) {
  const session = useSession();
  const { pathname, search } = useLocation();

  if (session.status === 'loading') return null;
  if (session.status === 'anonymous' || session.me === null) {
    return (
      <Link to={withNext('/login', pathname + search)} className={buttonClassName('secondary')}>
        댓글 쓰기
      </Link>
    );
  }
  if (session.me.status === 'UNVERIFIED') {
    return <UnverifiedNote action="좋아요를 누르고 댓글을 달거나 신고할 수 있어요." />;
  }
  return <CommentForm label="댓글" submitLabel="댓글 남기기" onSubmit={onSubmit} />;
}

type CommentItemProps = {
  comment: CommunityComment;
  myMemberId: number | null;
  editingId: number | null;
  onEdit: (commentId: number) => void;
  onCancelEdit: () => void;
  onSaveEdit: (commentId: number, content: string) => Promise<void>;
  onDelete: (commentId: number) => void;
  onReport: (commentId: number) => void;
  replying?: boolean;
  onReply?: () => void;
  onCancelReply?: () => void;
  onSubmitReply?: (content: string) => Promise<void>;
};

function CommentItem({
  comment,
  myMemberId,
  editingId,
  onEdit,
  onCancelEdit,
  onSaveEdit,
  onDelete,
  onReport,
  replying = false,
  onReply,
  onCancelReply,
  onSubmitReply,
}: CommentItemProps) {
  const session = useSession();

  if (comment.deleted) {
    return <p className="text-base text-ink-subtle">삭제된 댓글이에요</p>;
  }

  const signedIn = session.status === 'authenticated' && session.me !== null;
  const unverified = signedIn && session.me?.status === 'UNVERIFIED';
  const mine = myMemberId !== null && comment.author?.memberId === myMemberId;
  const content = comment.content ?? '';

  if (editingId === comment.commentId) {
    return (
      <CommentForm
        label="댓글 수정"
        submitLabel="수정하기"
        initial={content}
        onCancel={onCancelEdit}
        onSubmit={(next) => onSaveEdit(comment.commentId, next)}
      />
    );
  }

  const edited = comment.updatedAt !== undefined && comment.updatedAt !== comment.createdAt;
  return (
    <div>
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        {comment.author && (
          <Link
            to={`/members/${comment.author.memberId}`}
            className="text-sm font-semibold text-ink underline-offset-2 hover:underline"
          >
            {comment.author.nickname}
          </Link>
        )}
        {comment.createdAt && (
          <time
            dateTime={comment.createdAt}
            title={formatKstDateTime(comment.createdAt)}
            className="text-xs text-ink-muted"
          >
            {formatRelativeKst(comment.createdAt)}
          </time>
        )}
        {edited && <span className="text-xs text-ink-muted">수정됨</span>}
      </div>
      <p className="mt-1 whitespace-pre-wrap break-words text-base text-ink">
        {comment.replyTo && (
          <>
            <Link
              to={`/members/${comment.replyTo.memberId}`}
              className="font-semibold text-accent underline-offset-2 hover:underline"
            >
              @{comment.replyTo.nickname}
            </Link>{' '}
          </>
        )}
        {content}
      </p>
      <div className="-mx-2 flex flex-wrap items-center">
        {onReply && signedIn && (
          <Button variant="ghost" onClick={onReply} disabled={unverified} className="px-2">
            답글
          </Button>
        )}
        {mine ? (
          <>
            <Button variant="ghost" onClick={() => onEdit(comment.commentId)} className="px-2">
              수정
            </Button>
            <Button variant="danger-outline" onClick={() => onDelete(comment.commentId)} className="px-2">
              삭제
            </Button>
          </>
        ) : (
          signedIn && (
            <Button variant="ghost" onClick={() => onReport(comment.commentId)} disabled={unverified} className="px-2">
              신고
            </Button>
          )
        )}
      </div>
      {replying && onSubmitReply && (
        <div className="mt-2">
          <CommentForm label="답글" submitLabel="답글 남기기" onCancel={onCancelReply} onSubmit={onSubmitReply} />
        </div>
      )}
    </div>
  );
}

type CommentFormProps = {
  label: string;
  submitLabel: string;
  initial?: string;
  onCancel?: () => void;
  onSubmit: (content: string) => Promise<void>;
};

function CommentForm({ label, submitLabel, initial = '', onCancel, onSubmit }: CommentFormProps) {
  const [content, setContent] = useState(initial);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [formError, setFormError] = useState<ApiError | string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    setFormError(null);
    const trimmed = content.trim();
    if (trimmed === '') {
      setFieldError('내용을 적어 주세요.');
      return;
    }
    setFieldError(undefined);
    setSubmitting(true);
    try {
      await onSubmit(trimmed);
      setContent(initial);
      setSubmitting(false);
    } catch (error) {
      setSubmitting(false);
      if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
        const contentItem = error.fieldErrors.find((item) => item.field === 'content');
        if (contentItem) setFieldError(contentItem.reason);
        else setFormError(error);
        return;
      }
      setFormError(error instanceof ApiError ? error : toUserMessage(error));
    }
  }

  return (
    <form noValidate onSubmit={handleSubmit} aria-label={label} className="flex flex-col gap-3">
      {formError && (
        <div role="alert">
          <Notice tone="danger" title="저장하지 못했어요">
            <p>{typeof formError === 'string' ? formError : formError.message}</p>
            {typeof formError !== 'string' && formError.status >= 500 && formError.traceId && (
              <p className="mt-1 font-mono text-xs text-ink-muted">문의 번호 {formError.traceId}</p>
            )}
          </Notice>
        </div>
      )}
      <TextArea
        label={label}
        name="content"
        maxLength={CONTENT_MAX}
        value={content}
        disabled={submitting}
        onChange={(event) => setContent(event.target.value)}
        error={fieldError}
      />
      <div className="flex flex-wrap justify-end gap-2">
        {onCancel && (
          <Button variant="secondary" onClick={onCancel} disabled={submitting}>
            취소
          </Button>
        )}
        <Button type="submit" loading={submitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}

function DeleteCommentDialog({
  commentId,
  onClose,
  onDeleted,
}: {
  commentId: number;
  onClose: () => void;
  onDeleted: () => Promise<void>;
}) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDelete() {
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await deleteComment(commentId);
      await onDeleted();
    } catch (cause) {
      setSubmitting(false);
      setError(toUserMessage(cause));
    }
  }

  return (
    <Dialog title="댓글을 삭제할까요?" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p className="text-base text-ink">삭제한 댓글은 되돌릴 수 없어요. 달린 답글은 그대로 남아요.</p>
        {error && (
          <div role="alert">
            <Notice tone="danger" title="삭제하지 못했어요">
              <p>{error}</p>
            </Notice>
          </div>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            취소
          </Button>
          <Button variant="danger" loading={submitting} onClick={() => void handleDelete()}>
            삭제
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
