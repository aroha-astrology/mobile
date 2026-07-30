import type { CapacitorConfig } from '@capacitor/cli';

// Frontend deploy URL. The mobile shell loads this URL in a webview.
// Set at build time to override. For Android emulator pointing at host:
//   http://10.0.2.2:3000
// For a Vercel preview: https://aroha-astrology-frontend-<preview>.vercel.app
// Defaults to production so distributable builds always load the real app;
// if that URL is unreachable, the bundled loading screen (out/index.html)
// shows and redirects once the connection returns.
const serverUrl = process.env.CAPACITOR_SERVER_URL || 'https://app.arohaastrology.in';

const config: CapacitorConfig = {
  appId: 'com.aroha.astrology',
  appName: 'Aroha Astrology',
  webDir: 'out',
  ...(serverUrl
    ? {
        server: {
          url: serverUrl,
          cleartext: serverUrl.startsWith('http://'),
        },
      }
    : {}),
  ios: {
    contentInset: 'always',
  },
  android: {
    allowMixedContent: false,
  },
  plugins: {
    FirebaseAuthentication: {
      // Global skipNativeAuth stays false so the plugin keeps transparently
      // intercepting the JS SDK's signInWithPhoneNumber() (used by
      // hooks/usePhoneAuth.ts, unchanged) and running it natively instead of
      // an invisible reCAPTCHA inside the webview. Google sign-in opts into
      // skipNativeAuth per-call instead (see hooks/useGoogleAuth.ts) — the
      // webview's Firebase JS SDK session, not the native one, is what
      // authHeader() in lib/api.ts reads from. Apple sign-in (iOS only,
      // hooks/useAppleAuth.ts) follows the same per-call skipNativeAuth
      // pattern as Google.
      skipNativeAuth: false,
      providers: ['phone', 'google.com', 'apple.com'],
    },
  },
};

export default config;
