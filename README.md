# Aroha Astrology — Mobile

Thin **Capacitor 6** native shell for iOS and Android. Loads the deployed `/frontend` Next.js app in a webview. The mobile repo carries only the native shell, plugins, and a fallback page — all UI and product code lives in `/frontend`.

## Branches

- `main` — production
- `develop` — integration; feature PRs target this
- `staging` — release candidate
- `feature/*` — feature branches
- `legacy/expo-app` — archived pre-rebuild Expo (React Native) codebase

## How it works

```
┌─────────────────────────┐
│  Native shell (iOS/And) │
│  ─────────────────────  │
│  Capacitor + plugins:   │
│   • @capacitor-firebase │
│     /authentication     │
│                         │
│   webview ──────────────┼──► CAPACITOR_SERVER_URL
│                         │     (your /frontend deploy)
└─────────────────────────┘
```

`CAPACITOR_SERVER_URL` is set at build time (see `.env.example`). When unset, the app shows a static fallback "Loading…" page from `out/`.

## Local development

```powershell
pnpm install

# Run Next.js dev server (only serves the fallback page; the real app is /frontend)
pnpm dev

# Build the fallback static export
pnpm build

# Sync to native and build Android debug APK pointing at host machine:
$env:CAPACITOR_SERVER_URL = "http://10.0.2.2:3000"   # Android emulator → host
pnpm cap:android
```

Then start the `/frontend` dev server (`cd ../frontend && pnpm dev`) so the webview has something to load.

## CI

- `.github/workflows/pr.yml` — lint + typecheck + build on PRs to `develop` or `main`
- `.github/workflows/develop-android.yml` — Android debug APK artifact on every push to `develop`

Required GitHub Actions secret for develop-android.yml:
- `GOOGLE_SERVICES_JSON` (base64-encoded contents of `google-services.json`)
- `CAPACITOR_SERVER_URL` (URL of the deployed frontend that the APK should point at)

## See also

- [docs/superpowers/specs/2026-05-25-hello-world-foundation-design.md](docs/superpowers/specs/2026-05-25-hello-world-foundation-design.md) — original design
- [docs/superpowers/plans/2026-05-25-hello-world-foundation.md](docs/superpowers/plans/2026-05-25-hello-world-foundation.md) — original task plan (pre-pivot)

> Note: The original design built a standalone Next.js login flow in `/mobile`. After deciding to consolidate UI in `/frontend`, that code was removed and this repo became a thin Capacitor shell. The docs preserve the original design history.
