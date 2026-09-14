'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAuth } from '@/lib/auth';
import { ThemeToggle } from '@/lib/theme';

const TABS = [
  { href: '/', label: 'Nearby' },
  { href: '/search', label: 'Search' },
  { href: '/map', label: 'Map' },
  { href: '/new', label: 'List' },
  { href: '/me', label: 'You' },
];

function isActive(pathname: string, href: string) {
  return href === '/' ? pathname === '/' : pathname.startsWith(href);
}

export function Nav() {
  const pathname = usePathname() ?? '/';
  const { signedIn, isAdmin } = useAuth();

  if (pathname === '/sign-in') return null;

  return (
    <>
      <header className="topbar">
        <Link href="/" className="brand">
          Radius
        </Link>

        <nav className="topnav" aria-label="Primary">
          <Link href="/" data-active={isActive(pathname, '/')}>Nearby</Link>
          <Link href="/search" data-active={isActive(pathname, '/search')}>Search</Link>
          <Link href="/map" data-active={isActive(pathname, '/map')}>Map</Link>
          <Link href="/new" data-active={isActive(pathname, '/new')}>List something</Link>
          {signedIn && <Link href="/requests" data-active={isActive(pathname, '/requests')}>Requests</Link>}
          {isAdmin && <Link href="/admin" data-active={isActive(pathname, '/admin')}>Review</Link>}
        </nav>

        <div className="row" style={{ gap: 'var(--s2)' }}>
          <ThemeToggle />
          {signedIn ? (
            <Link href="/me" className="chip" style={{ minHeight: 36, padding: '6px 14px' }}>
              You
            </Link>
          ) : (
            <Link href="/sign-in" className="chip" style={{ minHeight: 36, padding: '6px 14px' }}>
              Sign in
            </Link>
          )}
        </div>
      </header>

      {/* Phone only. Browsing is open, so these are all reachable as a guest. */}
      <nav className="tabbar" aria-label="Primary">
        {TABS.map((tab) => (
          <Link key={tab.href} href={tab.href} data-active={isActive(pathname, tab.href)}>
            {tab.label}
          </Link>
        ))}
      </nav>
    </>
  );
}
