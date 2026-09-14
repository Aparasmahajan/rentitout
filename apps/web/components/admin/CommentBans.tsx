'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { ApiError, api } from '@/lib/api';
import { when } from '@/lib/format';

/**
 * Who cannot comment right now.
 *
 * Every ban here has an end date — the server stores a timestamp rather than a
 * flag, so a ban nobody remembers to lift lifts itself. This list only ever
 * shows live ones; expired rows stay in the table as history.
 */
export function CommentBans() {
  const queryClient = useQueryClient();

  const bans = useQuery({ queryKey: ['moderation-bans'], queryFn: () => api.moderation.bans() });

  const lift = useMutation({
    mutationFn: (userId: string) => api.moderation.liftBan(userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['moderation-bans'] }),
  });

  const items = bans.data ?? [];

  return (
    <div className="stack" style={{ gap: 'var(--s4)' }}>
      {bans.isPending && <div className="skeleton" style={{ height: 120 }} />}
      {bans.isError && <div className="error">{(bans.error as ApiError).message}</div>}
      {lift.isError && <div className="error">{(lift.error as ApiError).message}</div>}

      {!bans.isPending && items.length === 0 && (
        <p className="muted">Nobody is restricted. Bans are set from a report.</p>
      )}

      {items.map((ban) => (
        <div key={ban.userId} className="card stack" style={{ padding: 'var(--s5)', gap: 6 }}>
          <div className="between" style={{ flexWrap: 'wrap' }}>
            <Link href={`/u/${ban.userId}`}>
              <strong>{ban.userId.slice(0, 8)}…</strong>
            </Link>
            <span className="badge" data-tone="warn">
              until {new Date(ban.bannedUntil).toLocaleDateString()}
            </span>
          </div>
          <span className="muted">
            {ban.reason} · set {when(ban.setAt)}
          </span>
          <button
            className="btn quiet"
            style={{ alignSelf: 'flex-start' }}
            onClick={() => lift.mutate(ban.userId)}
            disabled={lift.isPending}
          >
            Lift now
          </button>
        </div>
      ))}
    </div>
  );
}
