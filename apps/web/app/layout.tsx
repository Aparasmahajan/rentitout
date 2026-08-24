import type { Metadata, Viewport } from 'next';
import './globals.css';
import { Providers } from './providers';
import { Nav } from '@/components/Nav';

export const metadata: Metadata = {
  title: 'Radius — your neighbourhood, indexed',
  description:
    'Rent a ladder, hire an electrician, learn guitar, or post what you need — from the people who live near you.',
  manifest: '/manifest.webmanifest',
};

export const viewport: Viewport = {
  themeColor: '#fbfaf7',
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Providers>
          <Nav />
          <main className="shell">{children}</main>
        </Providers>
      </body>
    </html>
  );
}
