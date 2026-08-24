'use client';

import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { distance, initials } from '@/lib/format';

const RELATION_LABEL: Record<string, string> = {
  has: 'Can do',
  teaches: 'Teaches',
  needs: 'Needs',
  enjoys: 'Enjoys',
};

export default function ProfilePage() {
  const { id } = useParams<{ id: string }>();
  const { loading } = useAuth();

  const profile = useQuery({ queryKey: ['profile', id], queryFn: () => api.users.profile(id) });

  if (loading || profile.isPending) return <div className="skeleton" style={{ marginTop: 24 }} />;
  if (profile.isError) return <div className="error">{(profile.error as Error).message}</div>;

  const p = profile.data;
  const groups = Object.keys(RELATION_LABEL)
    .map((relation) => ({ relation, tags: p.tags.filter((t) => t.relation === relation) }))
    .filter((group) => group.tags.length > 0);

  return (
    <section className="stack" style={{ paddingTop: 'var(--space-6)' }}>
      <div className="row">
        <div className="thumb-placeholder" style={{ width: 64, aspectRatio: '1', borderRadius: 999 }}>
          {initials(p.displayName)}
        </div>
        <div>
          <h1 style={{ margin: 0 }}>{p.displayName}</h1>
          <span className="muted">
            {p.areaLabel} {p.distanceKm !== null ? `· ${distance(p.distanceKm)}` : ''}
          </span>
        </div>
      </div>

      <div className="wrap">
        {p.idChecked && <span className="badge" data-tone="checked">ID checked</span>}
        {p.professional && <span className="badge" data-tone="pro">Professional</span>}
        {p.ratingAvg !== null && <span className="badge">{p.ratingAvg.toFixed(1)} ★</span>}
        <span className="badge">{p.completedCount} completed</span>
        {!p.openToRequests && <span className="badge" data-tone="paused">Not taking requests</span>}
      </div>

      {p.bio && <p>{p.bio}</p>}

      {groups.map((group) => (
        <div key={group.relation} className="stack">
          <span className="tiny">{RELATION_LABEL[group.relation]}</span>
          <div className="wrap">
            {group.tags.map((tag) => (
              <span key={tag.id} className="chip static">
                {tag.label}
              </span>
            ))}
          </div>
        </div>
      ))}
    </section>
  );
}
