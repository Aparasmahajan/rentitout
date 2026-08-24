'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { ApiError, api } from '@/lib/api';
import { useRequireAuth } from '@/lib/auth';
import { TRADES } from '@/lib/types';

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
  OTHER: 'Something else',
};

/**
 * Trade details. The shop address is stored exactly, unlike a home point —
 * it is a business address being published on purpose, and a neighbour
 * deciding whether to let someone in deserves to see where they trade from.
 */
export default function ProfessionalPage() {
  const { loading } = useRequireAuth();
  const queryClient = useQueryClient();

  const [trade, setTrade] = useState('ELECTRICIAN');
  const [businessName, setBusinessName] = useState('');
  const [about, setAbout] = useState('');
  const [shopAddress, setShopAddress] = useState('');
  const [shopPoint, setShopPoint] = useState<{ lat: number; lon: number } | null>(null);
  const [serviceRadiusKm, setServiceRadiusKm] = useState(10);
  const [yearsExperience, setYearsExperience] = useState('');
  const [licenceRef, setLicenceRef] = useState('');
  const [insuranceRef, setInsuranceRef] = useState('');
  const [languages, setLanguages] = useState('');
  const [contactPhone, setContactPhone] = useState('');
  const [locating, setLocating] = useState(false);

  const existing = useQuery({
    queryKey: ['professional'],
    queryFn: () => api.professionals.mine(),
    retry: false,
  });

  useEffect(() => {
    const p = existing.data;
    if (!p) return;
    setTrade(p.trade);
    setBusinessName(p.businessName ?? '');
    setAbout(p.about ?? '');
    setShopAddress(p.shopAddress ?? '');
    if (p.shopLat != null && p.shopLon != null) setShopPoint({ lat: p.shopLat, lon: p.shopLon });
    setServiceRadiusKm(p.serviceRadiusKm);
    setYearsExperience(p.yearsExperience?.toString() ?? '');
    setLicenceRef(p.licenceRef ?? '');
    setInsuranceRef(p.insuranceRef ?? '');
    setLanguages(p.languages ?? '');
    setContactPhone(p.contactPhone ?? '');
  }, [existing.data]);

  const save = useMutation({
    mutationFn: () =>
      api.professionals.save({
        trade,
        businessName: businessName.trim() || null,
        about: about.trim() || null,
        shopAddress: shopAddress.trim() || null,
        shopLat: shopPoint?.lat ?? null,
        shopLon: shopPoint?.lon ?? null,
        serviceRadiusKm,
        yearsExperience: yearsExperience === '' ? null : Number(yearsExperience),
        licenceRef: licenceRef.trim() || null,
        insuranceRef: insuranceRef.trim() || null,
        languages: languages.trim() || null,
        contactPhone: contactPhone.trim() || null,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['professional'] });
      void queryClient.invalidateQueries({ queryKey: ['verification'] });
    },
  });

  if (loading) return <div className="skeleton" style={{ marginTop: 'var(--s6)' }} />;

  const needsIdCheck =
    existing.isError && (existing.error as ApiError)?.code === 'id_check_first';
  const state = existing.data?.state;

  return (
    <section className="stack" style={{ paddingTop: 'var(--s6)', gap: 'var(--s5)' }}>
      <div className="stack" style={{ gap: 6 }}>
        <span className="eyebrow">Your account</span>
        <h1>
          Your <em>trade details</em>
        </h1>
      </div>

      {needsIdCheck && (
        <div className="notice">
          We check ID before anyone lists a service that visits homes.{' '}
          <Link href="/me/verification" style={{ textDecoration: 'underline' }}>
            Start with the ID check
          </Link>
          .
        </div>
      )}

      {state && (
        <div className="between">
          <span className="muted">Your professional listing</span>
          <span className="badge" data-tone={state === 'ACTIVE' ? 'checked' : state === 'SUSPENDED' ? 'warn' : 'pending'}>
            {state === 'ACTIVE' ? 'Live' : state === 'SUSPENDED' ? 'Paused' : 'Waiting on review'}
          </span>
        </div>
      )}

      <div className="stack">
        <span className="eyebrow">What do you do</span>
        <div className="wrap">
          {TRADES.map((t) => (
            <button key={t} className="chip" data-active={trade === t} onClick={() => setTrade(t)}>
              {TRADE_LABEL[t]}
            </button>
          ))}
        </div>
      </div>

      <div className="field">
        <label htmlFor="business">Business name</label>
        <input id="business" value={businessName} onChange={(e) => setBusinessName(e.target.value)} placeholder="Okafor Electrical" />
      </div>

      <div className="field">
        <label htmlFor="about">About your work</label>
        <textarea
          id="about"
          value={about}
          onChange={(e) => setAbout(e.target.value)}
          placeholder="Twelve years on domestic wiring. Small jobs welcome, no fuse-box replacements."
        />
      </div>

      <div className="field">
        <label htmlFor="address">Shop or workshop address</label>
        <input
          id="address"
          value={shopAddress}
          onChange={(e) => setShopAddress(e.target.value)}
          placeholder="Oranienstraße 40, 10999 Berlin"
        />
        <span className="muted">
          Shown to neighbours in full. This is a business address, not your home — your home point
          stays approximate.
        </span>
      </div>

      <div className="row" style={{ flexWrap: 'wrap' }}>
        <button
          className="btn quiet"
          disabled={locating}
          onClick={() => {
            setLocating(true);
            navigator.geolocation.getCurrentPosition(
              (pos) => {
                setShopPoint({ lat: pos.coords.latitude, lon: pos.coords.longitude });
                setLocating(false);
              },
              () => setLocating(false),
              { enableHighAccuracy: true, timeout: 10000 },
            );
          }}
        >
          {locating ? 'Locating…' : 'Pin the shop where I am now'}
        </button>
        {shopPoint && (
          <span className="muted">
            {shopPoint.lat.toFixed(5)}, {shopPoint.lon.toFixed(5)}
          </span>
        )}
      </div>

      <div className="stack">
        <span className="eyebrow">How far will you travel</span>
        <div className="wrap">
          {[2, 5, 10, 25, 50].map((km) => (
            <button
              key={km}
              className="chip"
              data-active={serviceRadiusKm === km}
              onClick={() => setServiceRadiusKm(km)}
            >
              {km} km
            </button>
          ))}
        </div>
      </div>

      <div className="field">
        <label htmlFor="years">Years doing this</label>
        <input id="years" inputMode="numeric" value={yearsExperience} onChange={(e) => setYearsExperience(e.target.value)} />
      </div>

      <div className="field">
        <label htmlFor="licence">Licence or registration number</label>
        <input id="licence" value={licenceRef} onChange={(e) => setLicenceRef(e.target.value)} />
      </div>

      <div className="field">
        <label htmlFor="insurance">Liability insurance reference</label>
        <input id="insurance" value={insuranceRef} onChange={(e) => setInsuranceRef(e.target.value)} />
        <span className="muted">
          Not required, but neighbours letting someone into their home tend to look for it.
        </span>
      </div>

      <div className="field">
        <label htmlFor="languages">Languages you work in</label>
        <input id="languages" value={languages} onChange={(e) => setLanguages(e.target.value)} placeholder="English, German, Yoruba" />
      </div>

      <div className="field">
        <label htmlFor="phone">Business phone</label>
        <input id="phone" inputMode="tel" value={contactPhone} onChange={(e) => setContactPhone(e.target.value)} />
      </div>

      {save.isError && <div className="error">{(save.error as ApiError).message}</div>}
      {save.isSuccess && <p className="muted">Saved.</p>}

      <button className="btn block" onClick={() => save.mutate()} disabled={save.isPending || needsIdCheck}>
        {save.isPending ? 'Saving…' : 'Save trade details'}
      </button>

      <Link href="/me/verification" className="btn ghost block">
        Back to your checks
      </Link>
    </section>
  );
}
