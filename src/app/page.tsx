'use client';

import { useEffect, useState } from 'react';

// Where the shell should send the user. Normally the app is loaded directly
// via CAPACITOR_SERVER_URL (see capacitor.config.ts) and this bundled page is
// never shown. It only appears as an offline/first-paint fallback, so we also
// redirect here as a safety net when a build shipped without a server URL.
const APP_URL = process.env.NEXT_PUBLIC_APP_URL || 'https://app.arohaastrology.in';

export default function LoadingScreen() {
  const [offline, setOffline] = useState(false);

  useEffect(() => {
    const go = () => {
      window.location.replace(APP_URL);
    };

    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      setOffline(true);
    } else {
      const t = setTimeout(go, 1200);
      return () => clearTimeout(t);
    }

    // Retry automatically when connectivity returns.
    const onOnline = () => {
      setOffline(false);
      go();
    };
    window.addEventListener('online', onOnline);
    return () => window.removeEventListener('online', onOnline);
  }, []);

  return (
    <main className="aroha-loader">
      <div className="aroha-emblem-wrap">
        <span className="aroha-glow" />
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img className="aroha-emblem" src="/logo.png" alt="Aroha Astrology" />
        <span className="aroha-ring" />
      </div>

      <h1 className="aroha-wordmark">Aroha Astrology</h1>
      <p className="aroha-tagline">Aligning the stars…</p>

      {offline ? (
        <>
          <p className="aroha-hint">
            You appear to be offline. Check your internet connection to continue.
          </p>
          <button
            className="aroha-retry"
            onClick={() => window.location.replace(APP_URL)}
          >
            Retry
          </button>
        </>
      ) : (
        <div className="aroha-dots" aria-hidden>
          <span />
          <span />
          <span />
        </div>
      )}
    </main>
  );
}
