'use client';

import { useMutation, useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { ApiError, api, uploadPhoto } from '@/lib/api';
import { useRequireAuth } from '@/lib/auth';
import { KINDS, KIND_LABEL, UNITS, money } from '@/lib/format';
import type { ListingKind, Unit } from '@/lib/types';

/** Three steps: kind → details → review and publish. */
export default function NewListingPage() {
  const router = useRouter();
  const { me, loading } = useRequireAuth();

  const [step, setStep] = useState(1);
  const [kind, setKind] = useState<ListingKind>('RENT_ITEM');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [unit, setUnit] = useState<Unit>('DAY');
  const [deposit, setDeposit] = useState('');
  const [buyPrice, setBuyPrice] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [file, setFile] = useState<File | null>(null);
  const [homeVisit, setHomeVisit] = useState(false);

  const vocabulary = useQuery({ queryKey: ['tags'], queryFn: () => api.tags.all() });

  const toMinor = (value: string) => (value.trim() === '' ? null : Math.round(Number(value) * 100));
  const isSale = kind === 'SELL_ITEM';
  const isNeed = kind === 'OPEN_NEED';

  const publish = useMutation({
    mutationFn: async () => {
      const listing = await api.listings.create({
        kind,
        title: title.trim(),
        description: description.trim() || null,
        priceMinor: isSale || isNeed ? null : toMinor(price),
        unit: isSale || isNeed ? null : unit,
        depositMinor: toMinor(deposit) ?? 0,
        buyPriceMinor: isSale ? toMinor(buyPrice) : null,
        lat: me?.lat ?? 52.4996,
        lon: me?.lon ?? 13.418,
        tags,
        homeVisit,
      });

      if (file) {
        const objectKey = await uploadPhoto(file);
        await api.listings.attachPhoto(listing.id, objectKey);
      }
      return listing;
    },
    onSuccess: (listing) => router.push(`/l/${listing.id}`),
  });

  if (loading) return <div className="skeleton" style={{ marginTop: 24 }} />;

  const noLocation = !me?.lat;
  const canContinue = title.trim().length > 2 && (isNeed || (isSale ? buyPrice !== '' : price !== ''));

  return (
    <section className="stack" style={{ paddingTop: 'var(--space-6)' }}>
      <div className="between">
        <h1 style={{ margin: 0 }}>List something</h1>
        <span className="tiny">Step {step} of 3</span>
      </div>

      {noLocation && (
        <div className="error">
          Set your area on your profile first — a listing without a point cannot appear in anyone&apos;s feed.
        </div>
      )}

      {step === 1 && (
        <div className="stack">
          <p className="muted">What is it?</p>
          <div className="wrap">
            {KINDS.map((k) => (
              <button key={k} className="chip" data-active={kind === k} onClick={() => setKind(k)}>
                {KIND_LABEL[k]}
              </button>
            ))}
          </div>
          <button className="btn block" onClick={() => setStep(2)}>
            Continue
          </button>
        </div>
      )}

      {step === 2 && (
        <div className="stack">
          <div className="field">
            <label htmlFor="title">Title</label>
            <input id="title" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Aluminium ladder, 3 m" />
          </div>

          <div className="field">
            <label htmlFor="description">Description</label>
            <textarea
              id="description"
              rows={4}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Light enough to carry up a stairwell. Please bring it back dry."
            />
          </div>

          {!isNeed && !isSale && (
            <div className="row">
              <div className="field grow">
                <label htmlFor="price">Rate (€)</label>
                <input id="price" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} placeholder="5.00" />
              </div>
              <div className="field">
                <label htmlFor="unit">Per</label>
                <select id="unit" value={unit} onChange={(e) => setUnit(e.target.value as Unit)}>
                  {UNITS.map((u) => (
                    <option key={u} value={u}>
                      {u.toLowerCase()}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          {isSale && (
            <div className="field">
              <label htmlFor="buy">Sale price (€)</label>
              <input id="buy" inputMode="decimal" value={buyPrice} onChange={(e) => setBuyPrice(e.target.value)} placeholder="40.00" />
            </div>
          )}

          {!isNeed && (
            <div className="field">
              <label htmlFor="deposit">Refundable deposit (€, optional)</label>
              <input id="deposit" inputMode="decimal" value={deposit} onChange={(e) => setDeposit(e.target.value)} placeholder="20.00" />
            </div>
          )}

          {(kind === 'SKILL_FOR_HIRE' || kind === 'TRADE_SERVICE' || kind === 'TEACHING') && (
            <label className="card row" style={{ padding: 'var(--s4)', alignItems: 'flex-start', gap: 'var(--s3)' }}>
              <input
                type="checkbox"
                checked={homeVisit}
                onChange={(e) => setHomeVisit(e.target.checked)}
                style={{ marginTop: 3 }}
              />
              <span className="stack" style={{ gap: 4 }}>
                <strong>This means coming to the customer&apos;s home</strong>
                <span className="muted">
                  We check ID and trade details before anyone can publish these, so neighbours know
                  who is arriving.
                </span>
              </span>
            </label>
          )}

          <div className="field">
            <label htmlFor="photo">Photo</label>
            <input id="photo" type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
            <span className="muted">Uploads straight to the object store — the API only sees the key.</span>
          </div>

          <div className="stack">
            <span className="tiny">Tags — how people will find it</span>
            <div className="wrap">
              {(vocabulary.data ?? []).slice(0, 40).map((tag) => (
                <button
                  key={tag.slug}
                  className="chip"
                  data-active={tags.includes(tag.slug)}
                  onClick={() =>
                    setTags((prev) => (prev.includes(tag.slug) ? prev.filter((t) => t !== tag.slug) : [...prev, tag.slug]))
                  }
                >
                  {tag.label}
                </button>
              ))}
            </div>
          </div>

          <div className="row">
            <button className="btn ghost grow" onClick={() => setStep(1)}>
              Back
            </button>
            <button className="btn grow" onClick={() => setStep(3)} disabled={!canContinue}>
              Review
            </button>
          </div>
        </div>
      )}

      {step === 3 && (
        <div className="stack">
          <div className="card stack" style={{ padding: 'var(--space-4)' }}>
            <span className="tiny">{KIND_LABEL[kind]}</span>
            <h2 style={{ margin: 0 }}>{title}</h2>
            {description && <p className="muted">{description}</p>}
            <div className="between">
              <span className="price">
                {isSale
                  ? money(toMinor(buyPrice))
                  : isNeed
                    ? 'Asking'
                    : `${money(toMinor(price))} / ${unit.toLowerCase()}`}
              </span>
              {toMinor(deposit) ? <span className="muted">+ {money(toMinor(deposit))} deposit</span> : null}
            </div>
            <div className="wrap">
              {tags.map((tag) => (
                <span key={tag} className="chip static">
                  {tag}
                </span>
              ))}
            </div>
            <span className="muted">{me?.areaLabel ?? 'Your area'}</span>
          </div>

          {publish.isError && <div className="error">{(publish.error as ApiError).message}</div>}

          <div className="row">
            <button className="btn ghost grow" onClick={() => setStep(2)}>
              Back
            </button>
            <button className="btn grow" onClick={() => publish.mutate()} disabled={publish.isPending || noLocation}>
              {publish.isPending ? 'Publishing…' : 'Publish'}
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
