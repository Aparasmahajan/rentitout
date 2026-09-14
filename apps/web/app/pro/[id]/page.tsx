'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { api } from '@/lib/api';
import { when } from '@/lib/format';

const TRADE_LABEL: Record<string, string> = {
  ELECTRICIAN: 'Electrician',
  PLUMBER: 'Plumber',
  AC_SERVICE: 'AC service',
  APPLIANCE_REPAIR: 'Appliance repair',
  CARPENTER: 'Carpenter',
  PAINTER: 'Painter',
  PEST_CONTROL: 'Pest control',
  CLEANING: 'Cleaning',
  HOUSE_HELP: 'House help',
  COOK: 'Cook',
  DRIVER: 'Driver',
  MOVER: 'Mover',
  GARDENER: 'Gardener',
  BEAUTICIAN: 'Beautician',
  TUTOR: 'Tutor',
  IT_SUPPORT: 'IT support',
  OTHER: 'Service',
};

/**
 * The page a neighbour reads before deciding to let someone into their flat.
 * Public on purpose — no account needed to check who is coming.
 */
export default function ProfessionalPublicPage() {
  const { id } = useParams<{ id: string }>();

  const pro = useQuery({
    queryKey: ['professional-public', id],
    queryFn: () => api.professionals.get(id),
  });

  if (pro.isPending) return <div className="skeleton" style={{ marginTop: 'var(--s6)' }} />;
  if (pro.isError) {
    return (
      <div className="error" style={{ marginTop: 'var(--s6)' }}>
        {(pro.error as Error).message}
      </div>
    );
  }

  const p = pro.data;
  const trade = TRADE_LABEL[p.trade] ?? p.trade;

  return (
    <section className="stack" style={{ paddingTop: 'var(--s6)', gap: 'var(--s5)' }}>
      <div className="stack" style={{ gap: 6 }}>
        <span className="eyebrow">{trade}</span>
        <h1>{p.businessName || p.displayName || 'A professional'}</h1>
        {p.businessName && p.displayName && <span className="muted">{p.displayName}</span>}
      </div>

      <div className="wrap">
        {p.idChecked && <span className="badge" data-tone="checked">ID checked</span>}
        {p.state === 'ACTIVE' ? (
          <span className="badge" data-tone="pro">Listed professional</span>
        ) : (
          <span className="badge" data-tone="pending">
            {p.state === 'PENDING' ? 'Awaiting review' : 'Paused'}
          </span>
        )}
        <span className="badge">Travels {p.serviceRadiusKm} km</span>
        {p.yearsExperience != null && <span className="badge">{p.yearsExperience} years</span>}
      </div>

      {p.about && <p style={{ maxWidth: '62ch' }}>{p.about}</p>}

      <div className="card stack" style={{ padding: 'var(--s5)' }}>
        <span className="eyebrow">What we checked</span>
        <p style={{ margin: 0 }}>
          {p.idChecked
            ? `We have seen government ID for this person${p.idCheckedAt ? `, ${when(p.idCheckedAt)}` : ''}, and the trade details below.`
            : 'This person has not completed an ID check.'}
        </p>
        <p className="muted" style={{ margin: 0 }}>
          That is a check on who they say they are. It is not a recommendation of their work, and
          Radius does not inspect anybody&apos;s workmanship. Read the reviews and agree the job
          before they start.
        </p>
      </div>

      <div className="stack" style={{ gap: 0 }}>
        {[
          ['Trade', trade],
          ['Shop address', p.shopAddress],
          ['Licence', p.licenceRef],
          ['Insurance', p.insuranceRef],
          ['Languages', p.languages],
          ['Phone', p.contactPhone],
        ]
          .filter(([, value]) => Boolean(value))
          .map(([label, value]) => (
            <div
              key={label as string}
              className="between"
              style={{ padding: 'var(--s3) 0', borderTop: '1px solid var(--rule)' }}
            >
              <span className="muted">{label}</span>
              <span style={{ textAlign: 'right' }}>{value}</span>
            </div>
          ))}
      </div>

      <Link href={`/u/${p.userId}`} className="btn ghost block">
        Their neighbour profile
      </Link>
    </section>
  );
}
