# Aroha Astrology Mobile — Hello World Foundation

**Date:** 2026-05-25
**Owner:** subir@saleslife.ai
**Status:** Design approved, awaiting implementation plan

## Goal

Replace the existing Expo mobile app at `C:\dev\aroha-astrology\mobile` with a clean **Next.js 15 + Capacitor 6** foundation that:

1. Renders a "Hello, {phone}" home screen on iOS and Android
2. Authenticates users via **Firebase Phone OTP**, then exchanges the Firebase ID token for a **Supabase session** (reusing the existing `arohaastrology.in/api/auth/phone-signin` backend)
3. Captures basic product events to **PostHog**
4. Ships with **GitHub Actions** running lint/typecheck on every PR and producing a debug Android APK on every push to `dev`

This is the foundational base that all subsequent product feature development will sit on top of. No astrology features are in scope here.

## Non-Goals

- iOS builds in CI (deferred — built locally via `npx cap open ios`)
- Production signing or store uploads
- Push notifications, deep linking, OTA updates
- Any product feature beyond the Hello World home screen
- Migrating the ~40 legacy feature screens from the Expo app (those will be re-implemented as Next.js routes after the foundation is stable)

## Architecture

**Stack:**
- **Next.js 15** (App Router) with `output: 'export'` — entire app builds to static HTML/JS
- **Capacitor 6** wraps the static export into native iOS + Android shells
- **Firebase Phone Auth** via `@capacitor-firebase/authentication` (native Firebase SDKs, no reCAPTCHA on mobile)
- **Supabase** for session + future data layer; session obtained by exchanging Firebase ID token at `arohaastrology.in/api/auth/phone-signin`
- **PostHog** (`posthog-js`) for product analytics — single SDK, includes feature flags + session replay for later
- **Zustand** for client state, **TanStack Query** ready but not used in Hello World
- **Tailwind** for styling, **NativeWind** dropped (web-first approach)
- **pnpm** workspace (matches monorepo conventions in `C:\dev\aroha-astrology`)

**Bundle ID:** `com.arohaastrology.app` (reuses existing Firebase project, `google-services.json`, and `GoogleService-Info.plist`).

**Why static export over hosted shell:** Static export keeps the app installable and functional offline-of-CDN, avoids App Store "webview wrapper" rejection risk, and gives proper native feel. Tradeoff: no Next.js server actions or API routes available to mobile — backend calls go to the existing `arohaastrology.in` API.

## File Layout

```
C:\dev\aroha-astrology\mobile\
├── src/
│   ├── app/
│   │   ├── layout.tsx              # Root: PostHogProvider, QueryClient, Supabase auth listener
│   │   ├── page.tsx                # Entry: routes to /home or /login based on session
│   │   ├── globals.css
│   │   ├── (auth)/
│   │   │   ├── layout.tsx          # Redirects to /home if already signed in
│   │   │   ├── login/page.tsx      # Phone input (E.164, default +91)
│   │   │   └── verify/page.tsx     # 6-digit OTP entry + resend timer
│   │   └── (app)/
│   │       ├── layout.tsx          # Auth-required gate via <AuthGate>
│   │       └── home/page.tsx       # "Hello, {phone}" + Logout
│   ├── components/
│   │   ├── AuthGate.tsx            # Redirects unauth'd users to /login
│   │   └── PostHogProvider.tsx     # Client-only init + pageview tracking
│   ├── lib/
│   │   ├── env.ts                  # zod schema for NEXT_PUBLIC_* vars
│   │   ├── firebase.ts             # Capacitor Firebase Auth init + helpers
│   │   ├── supabase.ts             # Supabase client singleton (Preferences storage)
│   │   ├── auth.ts                 # sendOTP / verifyOTP (orchestration)
│   │   └── analytics.ts            # PostHog wrapper
│   └── store/
│       └── authStore.ts            # Zustand: session, phone, verificationId, isNewUser
├── ios/                            # Capacitor iOS project (committed)
├── android/                        # Capacitor Android project (committed)
├── public/
│   └── icon.png                    # Aroha logo (1024×1024)
├── .github/workflows/
│   ├── pr.yml
│   └── dev-android.yml
├── capacitor.config.ts             # appId: com.arohaastrology.app, webDir: out
├── next.config.ts                  # output: 'export', images.unoptimized: true
├── tailwind.config.ts
├── tsconfig.json
├── package.json
└── .env.example
```

## Routes

| Path | Purpose | Auth |
|---|---|---|
| `/` | Redirect to `/home` if session exists, else `/login` | — |
| `/login` | Phone input (E.164, default +91 prefix) | Unauth only |
| `/verify` | 6-digit OTP entry + resend (30s lockout) | Unauth only |
| `/home` | "Hello, {phone}" + Logout button | Required |

## Auth Flow

```
[/login]
  user types Indian mobile (10 digits)
  validate via isValidIndianMobile(local)
  normalize to E.164: +91XXXXXXXXXX

  sendOTP(phone) → FirebaseAuthentication.signInWithPhoneNumber()
                 → returns verificationId
  stash verificationId + phone in Zustand
  router.push('/verify')

[/verify]
  user types 6-digit code
  verifyOTP(code) calls:
    1. FirebaseAuthentication.confirmVerificationCode({ verificationId, verificationCode })
    2. FirebaseAuthentication.getCurrentUser().then(u => u.getIdToken())
    3. POST { idToken, referralCode? } → arohaastrology.in/api/auth/phone-signin
       returns { tokenHash, type, isNewUser }
    4. supabase.auth.verifyOtp({ token_hash: tokenHash, type })
       → Supabase session persisted via @capacitor/preferences storage adapter
    5. posthog.identify(supabaseUser.id, { phone, isNewUser })
  router.replace('/home')

[/home]
  reads session.user from Zustand
  shows "Hello, {phone}"
  Logout button:
    supabase.auth.signOut()
    FirebaseAuthentication.signOut()
    posthog.reset()
    clear @capacitor/preferences
    router.replace('/login')
```

**Error cases:**

| Stage | Failure | Handling |
|---|---|---|
| Phone validation | Invalid format | Inline message, no network call |
| OTP send | Firebase network/quota | Toast + retry button |
| OTP verify | Wrong code | "Invalid code, try again" — Firebase error code mapped |
| OTP resend | Within 30s window | Disabled button with countdown |
| Backend exchange | `/api/auth/phone-signin` returns !ok | Sign out Firebase, toast error |
| Supabase verifyOtp | Returns error | Sign out Firebase, toast error |

## Analytics

**SDK:** `posthog-js`, initialized in `PostHogProvider.tsx` on first client mount.

**Config:**
- `api_host`: `NEXT_PUBLIC_POSTHOG_HOST` (default `https://us.i.posthog.com`)
- `persistence`: `localStorage+cookie`
- `autocapture`: true
- `capture_pageview`: false (manual on App Router route change)

**Identify:** Call `posthog.identify(supabaseUser.id, { phone, isNewUser })` after Supabase session set. Call `posthog.reset()` on logout.

**Events:**

| Event | Where fired | Properties |
|---|---|---|
| `app_opened` | Root layout client mount | `platform`, `app_version` |
| `auth_phone_submitted` | `/login` send tap | `phone_country` |
| `auth_otp_sent` | sendOTP resolved | — |
| `auth_otp_send_failed` | sendOTP rejected | `error_code` |
| `auth_otp_verified` | confirmVerificationCode succeeded | — |
| `auth_otp_failed` | confirmVerificationCode rejected | `error_code` |
| `auth_session_established` | Supabase session set | `is_new_user` |
| `auth_exchange_failed` | Backend or verifyOtp failed | `stage`, `error` |
| `logout` | Sign-out button | — |
| `$pageview` | Route change | `path` |

No PII in event names or properties. Phone is sent only as a person property via `identify`.

## CI / GitHub Actions

**Branching:**
- `main` — production-ready
- `dev` — integration branch; all feature PRs target this
- `feature/*` — feature branches; PR to `dev`
- `fix/*` — hotfix branches; PR direct to `main`, then backport to `dev`

**Workflow: `mobile/.github/workflows/pr.yml`**

Trigger: `pull_request` targeting `dev` or `main`.

```
runs-on: ubuntu-latest
steps:
  - checkout
  - setup-node@v4 (node 20)
  - setup-pnpm@v3
  - pnpm install --frozen-lockfile
  - pnpm lint
  - pnpm typecheck     # tsc --noEmit
  - pnpm test          # vitest, smoke test for env.ts
  - pnpm build         # next build (catches static export errors)
```

Branch protection on `dev` and `main`: require `pr.yml` to pass before merge.

**Workflow: `mobile/.github/workflows/dev-android.yml`**

Trigger: `push` to `dev`.

```
runs-on: ubuntu-latest
steps:
  - checkout
  - setup-node@v4 (node 20)
  - setup-pnpm@v3
  - setup-java@v4 (java 17, temurin)
  - decode GOOGLE_SERVICES_JSON secret → android/app/google-services.json
  - pnpm install --frozen-lockfile
  - pnpm build                            # outputs to ./out
  - npx cap sync android
  - cd android && ./gradlew assembleDebug
  - upload-artifact: app-debug.apk (retention 14 days)
```

**Secrets (configured later in repo settings):**

| Secret | Used by | Source |
|---|---|---|
| `NEXT_PUBLIC_FIREBASE_API_KEY` | dev-android | Firebase console |
| `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN` | dev-android | Firebase console |
| `NEXT_PUBLIC_FIREBASE_PROJECT_ID` | dev-android | Firebase console |
| `NEXT_PUBLIC_FIREBASE_APP_ID` | dev-android | Firebase console |
| `NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID` | dev-android | Firebase console |
| `NEXT_PUBLIC_SUPABASE_URL` | dev-android | Supabase project settings |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | dev-android | Supabase project settings |
| `NEXT_PUBLIC_POSTHOG_KEY` | dev-android | PostHog project settings |
| `NEXT_PUBLIC_POSTHOG_HOST` | dev-android | PostHog (default `https://us.i.posthog.com`) |
| `GOOGLE_SERVICES_JSON` | dev-android | base64-encoded contents of `google-services.json` |

## Environment Variables

`.env.example` (committed):

```
NEXT_PUBLIC_FIREBASE_API_KEY=
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=arohaastrology.firebaseapp.com
NEXT_PUBLIC_FIREBASE_PROJECT_ID=
NEXT_PUBLIC_FIREBASE_APP_ID=
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=

NEXT_PUBLIC_SUPABASE_URL=
NEXT_PUBLIC_SUPABASE_ANON_KEY=

NEXT_PUBLIC_API_URL=https://arohaastrology.in

NEXT_PUBLIC_POSTHOG_KEY=
NEXT_PUBLIC_POSTHOG_HOST=https://us.i.posthog.com
```

`src/lib/env.ts` validates all required vars at module load via zod. Missing vars cause an immediate `throw` at startup — fail loudly.

## Legacy Migration

Before any new code lands in `C:\dev\aroha-astrology\mobile`:

1. From the existing repo, create branch `legacy/expo-app` from the current default branch and push to origin. Preserves the entire Expo codebase as a recoverable reference.
2. On the new working branch `feature/foundation` (cut from `dev`): delete everything except:
   - `.git/`
   - `google-services.json`
   - `aroha-astrology.keystore`
   - `assets/` (logo + icons)
   - `README.md`
3. Scaffold the new Next.js + Capacitor project in place.
4. Open PR `feature/foundation` → `dev`, get CI green, merge.

**Assets reused from legacy:**
- `google-services.json` → moves to `mobile/android/app/google-services.json` (also kept at repo root for CI base64 encoding)
- `aroha-astrology.keystore` → kept at root for future release signing (not used in Hello World)
- `assets/icon.png` + Aroha logo → moved to `mobile/public/`, used by Capacitor asset generation

## Acceptance Criteria

Hello World is "done" when:

- `pnpm dev` opens `http://localhost:3000` and shows `/login` with phone input
- Entering a real Indian mobile number triggers a Firebase SMS OTP (real SMS in manual testing; Firebase test phone numbers for automated tests)
- Entering the correct OTP lands the user on `/home` showing `Hello, +91XXXXXXXXXX` and a Logout button
- Clicking Logout returns to `/login` with no session
- `pnpm build && npx cap sync android && cd android && ./gradlew assembleDebug` produces a working APK installable on a physical Android device
- `npx cap open ios` opens the Xcode project; running on simulator reproduces the same login → home flow
- PostHog dashboard shows the events listed in Analytics section within seconds of triggering them
- Opening a PR from `feature/foundation` → `dev` runs `pr.yml` and passes
- Pushing to `dev` produces a downloadable `app-debug.apk` artifact on the workflow run page

## Risks & Open Questions

1. **`@capacitor-firebase/authentication` web fallback** uses Firebase JS SDK with invisible reCAPTCHA. We need to verify the dev (`pnpm dev` in browser) experience is workable, or restrict phone auth testing to native builds only.
2. **`output: 'export'` constraint** — App Router server features (server actions, dynamic API routes) are unavailable. All backend calls must go to the external `arohaastrology.in` API. Acceptable for Hello World; future features may force a hybrid hosted approach.
3. **Firebase project reuse** — the new app uses the same `com.arohaastrology.app` bundle ID, so it shares Firebase Auth users with the legacy Expo app. Sessions issued before the cutover will still be valid (users won't be force-logged-out, which is what we want).
4. **`google-services.json` in repo** — currently committed to legacy repo root. Plan keeps it committed (not a secret per Google's guidance) for local dev, plus base64-encoded as a CI secret. Confirm this is acceptable to the team.

## Next Step

Hand off to the `writing-plans` skill to produce a step-by-step implementation plan with task breakdown, dependencies, and verification commands.
