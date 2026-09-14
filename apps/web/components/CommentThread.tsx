'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiError, api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { when } from '@/lib/format';
import type { CommentView } from '@/lib/types';
import { ReportControl } from './ReportControl';

/**
 * The thread under a listing. Threaded exactly one level: a reply to a reply
 * lands beside its sibling rather than starting a third column, because the
 * server flattens it — and a client that pretended otherwise would render a
 * nesting the data does not have.
 *
 * Reading is open to a guest. Writing asks for a sign-in at the moment of
 * writing, not before.
 */
export function CommentThread({ listingId }: { listingId: string }) {
  const { signedIn, requireSignIn } = useAuth();
  const queryClient = useQueryClient();
  const [body, setBody] = useState('');

  const thread = useQuery({
    queryKey: ['comments', listingId],
    queryFn: () => api.comments.thread(listingId),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['comments', listingId] });

  const post = useMutation({
    mutationFn: () => api.comments.post(listingId, body.trim()),
    onSuccess: () => {
      setBody('');
      void invalidate();
    },
  });

  const comments = thread.data ?? [];
  const total = comments.reduce((sum, c) => sum + 1 + c.replies.length, 0);

  return (
    <section className="stack" style={{ gap: 'var(--s4)' }}>
      <div className="between">
        <span className="eyebrow">Questions and comments</span>
        {total > 0 && <span className="muted">{total}</span>}
      </div>

      <div className="field">
        <label htmlFor="new-comment">Ask the owner something</label>
        <textarea
          id="new-comment"
          rows={3}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder="Does the ladder fold down to 1.8 m?"
        />
      </div>

      {post.isError && <div className="error">{(post.error as ApiError).message}</div>}

      <button
        className="btn"
        style={{ alignSelf: 'flex-start' }}
        disabled={post.isPending || (signedIn && body.trim() === '')}
        onClick={() => (signedIn ? post.mutate() : requireSignIn())}
      >
        {!signedIn ? 'Sign in to comment' : post.isPending ? 'Posting…' : 'Post'}
      </button>

      {thread.isPending && <div className="skeleton" style={{ height: 90 }} />}
      {thread.isError && <div className="error">{(thread.error as ApiError).message}</div>}

      {!thread.isPending && comments.length === 0 && (
        <p className="muted">No questions yet. Yours would be the first.</p>
      )}

      <div className="stack" style={{ gap: 'var(--s4)' }}>
        {comments.map((comment) => (
          <Comment key={comment.id} comment={comment} listingId={listingId} onChanged={invalidate} />
        ))}
      </div>
    </section>
  );
}

function Comment({
  comment,
  listingId,
  onChanged,
  isReply = false,
}: {
  comment: CommentView;
  listingId: string;
  onChanged: () => void;
  isReply?: boolean;
}) {
  const { signedIn, requireSignIn } = useAuth();
  const [replying, setReplying] = useState(false);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');

  const reply = useMutation({
    mutationFn: () => api.comments.post(listingId, draft.trim(), comment.id),
    onSuccess: () => {
      setReplying(false);
      setDraft('');
      onChanged();
    },
  });

  const edit = useMutation({
    mutationFn: () => api.comments.edit(comment.id, draft.trim()),
    onSuccess: () => {
      setEditing(false);
      setDraft('');
      onChanged();
    },
  });

  const remove = useMutation({
    mutationFn: () => api.comments.remove(comment.id),
    onSuccess: onChanged,
  });

  const error = (reply.error ?? edit.error ?? remove.error) as ApiError | null;

  return (
    <article className="comment stack" style={{ gap: 6 }}>
      <div className="between" style={{ flexWrap: 'wrap', gap: 'var(--s2)' }}>
        <strong>{comment.deleted ? 'Removed' : (comment.authorName ?? 'A neighbour')}</strong>
        <span className="muted" style={{ fontSize: '0.8rem' }}>
          {when(comment.createdAt)}
          {comment.editedAt ? ' · edited' : ''}
        </span>
      </div>

      {/* The tombstone stays: a reply underneath a removed comment still needs
          something to hang from, and a moderator needs to see there was one. */}
      {comment.deleted ? (
        <p className="muted" style={{ margin: 0, fontStyle: 'italic' }}>
          This comment was removed.
        </p>
      ) : editing ? (
        <div className="stack">
          <textarea rows={3} value={draft} onChange={(e) => setDraft(e.target.value)} />
          <div className="row">
            <button className="btn" onClick={() => edit.mutate()} disabled={edit.isPending || draft.trim() === ''}>
              Save
            </button>
            <button className="btn quiet" onClick={() => setEditing(false)}>
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <p style={{ margin: 0, maxWidth: '62ch' }}>{comment.body}</p>
      )}

      {error && <div className="error">{error.message}</div>}

      {!comment.deleted && !editing && (
        <div className="wrap" style={{ gap: 'var(--s4)', alignItems: 'center' }}>
          {/* One level only, so a reply carries no reply control of its own. */}
          {!isReply && (
            <button
              className="linklike muted"
              style={{ fontSize: '0.85rem' }}
              onClick={() => (signedIn ? setReplying(!replying) : requireSignIn())}
            >
              {replying ? 'Cancel' : 'Reply'}
            </button>
          )}
          {comment.canEdit && (
            <button
              className="linklike muted"
              style={{ fontSize: '0.85rem' }}
              onClick={() => {
                setDraft(comment.body ?? '');
                setEditing(true);
              }}
            >
              Edit
            </button>
          )}
          {comment.canDelete && (
            <button
              className="linklike muted"
              style={{ fontSize: '0.85rem' }}
              onClick={() => remove.mutate()}
              disabled={remove.isPending}
            >
              Remove
            </button>
          )}
          {!comment.canEdit && <ReportControl targetType="COMMENT" targetId={comment.id} />}
        </div>
      )}

      {replying && (
        <div className="stack" style={{ paddingTop: 'var(--s2)' }}>
          <textarea
            rows={2}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder={`Reply to ${comment.authorName ?? 'them'}`}
          />
          <button
            className="btn"
            style={{ alignSelf: 'flex-start' }}
            onClick={() => reply.mutate()}
            disabled={reply.isPending || draft.trim() === ''}
          >
            {reply.isPending ? 'Posting…' : 'Reply'}
          </button>
        </div>
      )}

      {comment.replies.length > 0 && (
        <div className="replies">
          {comment.replies.map((child) => (
            <Comment key={child.id} comment={child} listingId={listingId} onChanged={onChanged} isReply />
          ))}
        </div>
      )}
    </article>
  );
}
