import type { CapacitorConfig } from '@capacitor/cli';

// Frontend deploy URL. The mobile shell loads this URL in a webview.
// Set at build time. For Android emulator pointing at host: http://10.0.2.2:3000
// For Vercel preview: https://aroha-astrology-frontend-<preview>.vercel.app
// For production: https://app.arohaastrology.in (or wherever /frontend deploys)
// If unset, the app falls back to the bundled out/index.html ("Loading…" screen).
const serverUrl = process.env.CAPACITOR_SERVER_URL;

const config: CapacitorConfig = {
  appId: 'com.arohaastrology.app',
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
      skipNativeAuth: false,
      providers: ['phone'],
    },
  },
};

export default config;
