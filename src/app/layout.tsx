import './globals.css';

export const metadata = {
  title: 'Aroha Astrology',
  description: 'Vedic astrology guidance',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
