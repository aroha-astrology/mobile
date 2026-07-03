# Hello World Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild `C:\dev\aroha-astrology\mobile` as a Next.js 15 + Capacitor 6 app that authenticates with Firebase Phone OTP → Supabase session, captures events to PostHog, and ships through a `feature → dev → main` GitHub Actions pipeline that produces a debug Android APK on every push to `dev`.

**Architecture:** Next.js App Router with `output: 'export'` produces a static bundle that Capacitor wraps into iOS + Android shells. Firebase Phone Auth uses the `@capacitor-firebase/authentication` plugin for native SMS auto-retrieval. OTP verification exchanges the Firebase ID token at the existing `arohaastrology.in/api/auth/phone-signin` endpoint for a Supabase session.

**Tech Stack:** Next.js 15, React 19, Capacitor 6, `@capacitor-firebase/authentication`, `@supabase/supabase-js`, `posthog-js`, Zustand, Tailwind v4, Vitest, pnpm, TypeScript 5.7.

**Working directory for all commands:** `C:\dev\aroha-astrology\mobile` unless stated otherwise.

**Reference spec:** [docs/superpowers/specs/2026-05-25-hello-world-foundation-design.md](../specs/2026-05-25-hello-world-foundation-design.md)

**Branch conventions (verified against the actual repo `github.com/aroha-astrology/mobile`):**
- `main` — production-ready (GitHub default)
- `develop` — integration branch; feature PRs target this
- `staging` — release candidate branch (exists, not used in this plan)
- `feature/*` — feature branches, branched from `develop`

Wherever the plan or workflows below mention `dev`, the actual branch name is `develop`. Use `develop`.

---

## Phase 0 — Legacy Preservation

### Task 0.1: Archive existing Expo code to a recoverable branch

**Files:** None (git-only)

**Repo conventions (already verified):** default branch is `main` (production), integration is `develop`, current branch is `develop`. The legacy archive is cut from `main` (preserves what's actually in production). The new work branches off `develop` (matches the team's PR flow).

- [ ] **Step 1: Confirm git state is clean**

Run from `C:\dev\aroha-astrology\mobile`:
```powershell
git status
```
Expected: `nothing to commit, working tree clean`. If `docs/` shows as untracked, that's the spec+plan we just wrote — they get committed in the prep step before this task runs. If anything else is uncommitted, stop and ask the user.

- [ ] **Step 2: Fetch latest from origin**

```powershell
git fetch origin
```

- [ ] **Step 3: Create and push the legacy archive branch from `origin/main`**

```powershell
git checkout -b legacy/expo-app origin/main
git push -u origin legacy/expo-app
```
Expected: branch created from production `main`, pushed to origin. This preserves the shipping Expo app exactly as users have it.

- [ ] **Step 4: Create the working branch from `develop`**

```powershell
git checkout develop
git pull --ff-only origin develop
git checkout -b feature/foundation
```
Expected: now on `feature/foundation`, branched from latest `develop`.

- [ ] **Step 5: Verify branch state**

```powershell
git log --oneline -1
git branch --show-current
```
Expected: same head commit as `develop`, current branch `feature/foundation`.

---

## Phase 1 — Clean Slate and Scaffold

### Task 1.1: Delete legacy code, keep critical assets

**Files:**
- Delete: everything in `C:\dev\aroha-astrology\mobile` except `.git/`, `google-services.json`, `aroha-astrology.keystore`, `assets/`, `README.md`, `docs/`

- [ ] **Step 1: List what currently exists**

```powershell
Get-ChildItem -Force | Select-Object Name
```
Note every entry that is NOT in the keep list above.

- [ ] **Step 2: Delete legacy directories**

Delete in this order (each one is its own command so failures are visible):
```powershell
Remove-Item -Recurse -Force android
Remove-Item -Recurse -Force app
Remove-Item -Recurse -Force astrologer
Remove-Item -Recurse -Force node_modules -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force plugins
Remove-Item -Recurse -Force src
```
Expected: no errors. If `app-debug.apk` exists at root, also delete it: `Remove-Item app-debug.apk`.

- [ ] **Step 3: Delete legacy files**

```powershell
Remove-Item app.json
Remove-Item babel.config.js
Remove-Item eas.json
Remove-Item expo-env.d.ts
Remove-Item global.css
Remove-Item metro.config.js
Remove-Item nativewind-env.d.ts
Remove-Item package.json
Remove-Item pnpm-lock.yaml
Remove-Item tailwind.config.js
Remove-Item tsconfig.json
```

- [ ] **Step 4: Verify the keep list survived**

```powershell
Get-ChildItem -Force | Select-Object Name
```
Expected output contains: `.git`, `google-services.json`, `aroha-astrology.keystore`, `assets`, `README.md`, `docs`. Nothing else.

- [ ] **Step 5: Commit the clean slate**

```powershell
git add -A
git commit -m "chore: remove legacy Expo code, preserve google-services.json and keystore"
```

### Task 1.1b: Add .gitignore

**Files:**
- Create: `.gitignore`

- [ ] **Step 1: Create .gitignore**

```
# dependencies
node_modules/
.pnp
.pnp.js

# next.js
.next/
out/
next-env.d.ts

# capacitor / native
android/.gradle/
android/build/
android/app/build/
android/local.properties
android/captures/
ios/App/Pods/
ios/App/build/
ios/DerivedData/

# testing
coverage/

# logs
npm-debug.log*
yarn-debug.log*
yarn-error.log*
*.log

# editor / os
.DS_Store
Thumbs.db
.vscode/
.idea/
*.swp

# env
.env*.local
.env

# typescript
*.tsbuildinfo
```

- [ ] **Step 2: Commit**

```powershell
git add .gitignore
git commit -m "chore: add .gitignore"
```

### Task 1.2: Initialize package.json with all dependencies

**Files:**
- Create: `package.json`

- [ ] **Step 1: Create package.json**

```json
{
  "name": "@aroha-astrology/mobile",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev": "next dev -p 3000",
    "build": "next build",
    "start": "next start",
    "lint": "next lint",
    "typecheck": "tsc --noEmit",
    "test": "vitest run",
    "cap:sync": "pnpm build && cap sync",
    "cap:android": "pnpm cap:sync && cd android && ./gradlew assembleDebug",
    "cap:ios": "pnpm cap:sync && cap open ios"
  },
  "dependencies": {
    "@capacitor-firebase/authentication": "^6.0.0",
    "@capacitor/android": "^6.0.0",
    "@capacitor/core": "^6.0.0",
    "@capacitor/ios": "^6.0.0",
    "@capacitor/preferences": "^6.0.0",
    "@supabase/supabase-js": "^2.47.0",
    "firebase": "^11.0.0",
    "next": "^15.1.0",
    "posthog-js": "^1.200.0",
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "zod": "^3.23.0",
    "zustand": "^5.0.0"
  },
  "devDependencies": {
    "@capacitor/cli": "^6.0.0",
    "@tailwindcss/postcss": "^4.0.0",
    "@testing-library/jest-dom": "^6.6.0",
    "@types/node": "^22.10.0",
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "autoprefixer": "^10.4.20",
    "eslint": "^9.0.0",
    "eslint-config-next": "^15.1.0",
    "jsdom": "^25.0.0",
    "postcss": "^8.4.49",
    "tailwindcss": "^4.0.0",
    "typescript": "^5.7.2",
    "vitest": "^2.1.0"
  },
  "engines": {
    "node": ">=20"
  }
}
```

- [ ] **Step 2: Install dependencies**

```powershell
pnpm install
```
Expected: installs without errors. A `pnpm-lock.yaml` and `node_modules/` appear.

- [ ] **Step 3: Commit**

```powershell
git add package.json pnpm-lock.yaml
git commit -m "feat: add package.json with Next.js + Capacitor dependencies"
```

### Task 1.3: Configure TypeScript and Next.js

**Files:**
- Create: `tsconfig.json`
- Create: `next.config.ts`
- Create: `next-env.d.ts` (auto-generated, but bootstrap)

- [ ] **Step 1: Create tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["dom", "dom.iterable", "esnext"],
    "allowJs": false,
    "skipLibCheck": true,
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "module": "esnext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "jsx": "preserve",
    "incremental": true,
    "plugins": [{ "name": "next" }],
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx", ".next/types/**/*.ts"],
  "exclude": ["node_modules", "android", "ios", "out"]
}
```

- [ ] **Step 2: Create next.config.ts**

```typescript
import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  output: 'export',
  images: { unoptimized: true },
  reactStrictMode: true,
  typescript: { ignoreBuildErrors: false },
  eslint: { ignoreDuringBuilds: false },
};

export default nextConfig;
```

- [ ] **Step 3: Create next-env.d.ts**

```typescript
/// <reference types="next" />
/// <reference types="next/image-types/global" />

// NOTE: This file should not be edited
// see https://nextjs.org/docs/basic-features/typescript for more information.
```

- [ ] **Step 4: Verify TypeScript compiles**

```powershell
pnpm typecheck
```
Expected: passes with no source files to check yet (or warnings about no `app/` directory — that's fine, we'll add it).

- [ ] **Step 5: Commit**

```powershell
git add tsconfig.json next.config.ts next-env.d.ts
git commit -m "feat: configure TypeScript and Next.js with static export"
```

### Task 1.4: Configure Tailwind v4 + global styles

**Files:**
- Create: `postcss.config.mjs`
- Create: `src/app/globals.css`

- [ ] **Step 1: Create postcss.config.mjs**

```javascript
export default {
  plugins: {
    '@tailwindcss/postcss': {},
    autoprefixer: {},
  },
};
```

- [ ] **Step 2: Create src/app/globals.css**

```css
@import 'tailwindcss';

:root {
  --background: #11131A;
  --foreground: #ffffff;
  --accent: #D4AF37;
}

html, body {
  background: var(--background);
  color: var(--foreground);
  -webkit-tap-highlight-color: transparent;
}

body {
  min-height: 100vh;
  font-family: system-ui, -apple-system, sans-serif;
}
```

- [ ] **Step 3: Commit**

```powershell
git add postcss.config.mjs src/app/globals.css
git commit -m "feat: configure Tailwind v4 and global styles"
```

### Task 1.5: Configure ESLint

**Files:**
- Create: `.eslintrc.json`

- [ ] **Step 1: Create .eslintrc.json**

```json
{
  "extends": "next/core-web-vitals"
}
```

- [ ] **Step 2: Commit**

```powershell
git add .eslintrc.json
git commit -m "chore: add ESLint config"
```

### Task 1.6: Configure Vitest

**Files:**
- Create: `vitest.config.ts`
- Create: `src/test-setup.ts`

- [ ] **Step 1: Create vitest.config.ts**

```typescript
import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    globals: true,
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
});
```

- [ ] **Step 2: Create src/test-setup.ts**

```typescript
import '@testing-library/jest-dom/vitest';
```

- [ ] **Step 3: Verify Vitest runs (no tests yet)**

```powershell
pnpm test
```
Expected: `No test files found, exiting with code 0` (or similar — exits 0).

- [ ] **Step 4: Commit**

```powershell
git add vitest.config.ts src/test-setup.ts
git commit -m "chore: add Vitest config"
```

---

## Phase 2 — Pure Utility Libraries (TDD)

### Task 2.1: Phone normalization and validation helpers

**Files:**
- Create: `src/lib/phone.ts`
- Test: `src/lib/phone.test.ts`

- [ ] **Step 1: Write the failing test**

Create `src/lib/phone.test.ts`:
```typescript
import { describe, it, expect } from 'vitest';
import { normaliseIndianPhone, isValidIndianMobile } from './phone';

describe('normaliseIndianPhone', () => {
  it('prepends +91 to a bare 10-digit number', () => {
    expect(normaliseIndianPhone('9876543210')).toBe('+919876543210');
  });

  it('strips a leading 0 from a 10-digit number', () => {
    expect(normaliseIndianPhone('09876543210')).toBe('+919876543210');
  });

  it('strips non-digit characters', () => {
    expect(normaliseIndianPhone('98765-43210')).toBe('+919876543210');
  });

  it('handles spaces and parentheses', () => {
    expect(normaliseIndianPhone(' (987) 654 3210 ')).toBe('+919876543210');
  });
});

describe('isValidIndianMobile', () => {
  it('accepts a 10-digit number starting with 6-9', () => {
    expect(isValidIndianMobile('9876543210')).toBe(true);
    expect(isValidIndianMobile('6000000000')).toBe(true);
    expect(isValidIndianMobile('7123456789')).toBe(true);
    expect(isValidIndianMobile('8123456789')).toBe(true);
  });

  it('rejects numbers starting with 0-5', () => {
    expect(isValidIndianMobile('5123456789')).toBe(false);
    expect(isValidIndianMobile('0123456789')).toBe(false);
  });

  it('rejects numbers shorter or longer than 10 digits', () => {
    expect(isValidIndianMobile('987654321')).toBe(false);
    expect(isValidIndianMobile('98765432109')).toBe(false);
  });

  it('rejects non-numeric input', () => {
    expect(isValidIndianMobile('abcdefghij')).toBe(false);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
pnpm test
```
Expected: FAIL — `Cannot find module './phone'`.

- [ ] **Step 3: Write the implementation**

Create `src/lib/phone.ts`:
```typescript
export function normaliseIndianPhone(raw: string): string {
  const digits = raw.replace(/\D/g, '').replace(/^0/, '');
  return `+91${digits}`;
}

export function isValidIndianMobile(local: string): boolean {
  return /^[6-9]\d{9}$/.test(local);
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
pnpm test
```
Expected: PASS — all 8 cases green.

- [ ] **Step 5: Commit**

```powershell
git add src/lib/phone.ts src/lib/phone.test.ts
git commit -m "feat: add Indian phone normalization and validation helpers"
```

### Task 2.2: Environment variable validation with zod

**Files:**
- Create: `src/lib/env.ts`
- Test: `src/lib/env.test.ts`

- [ ] **Step 1: Write the failing test**

Create `src/lib/env.test.ts`:
```typescript
import { describe, it, expect } from 'vitest';
import { parseEnv } from './env';

const validEnv = {
  NEXT_PUBLIC_FIREBASE_API_KEY: 'AIzaSyTest',
  NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN: 'arohaastrology.firebaseapp.com',
  NEXT_PUBLIC_FIREBASE_PROJECT_ID: 'arohaastrology',
  NEXT_PUBLIC_FIREBASE_APP_ID: '1:123:android:abc',
  NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID: '123456',
  NEXT_PUBLIC_SUPABASE_URL: 'https://test.supabase.co',
  NEXT_PUBLIC_SUPABASE_ANON_KEY: 'eyJ...test',
  NEXT_PUBLIC_API_URL: 'https://arohaastrology.in',
  NEXT_PUBLIC_POSTHOG_KEY: 'phc_test',
  NEXT_PUBLIC_POSTHOG_HOST: 'https://us.i.posthog.com',
};

describe('parseEnv', () => {
  it('parses a complete env object', () => {
    const parsed = parseEnv(validEnv);
    expect(parsed.NEXT_PUBLIC_SUPABASE_URL).toBe('https://test.supabase.co');
  });

  it('throws when a required field is missing', () => {
    const incomplete = { ...validEnv };
    delete (incomplete as Record<string, string>).NEXT_PUBLIC_FIREBASE_API_KEY;
    expect(() => parseEnv(incomplete)).toThrow();
  });

  it('throws when Supabase URL is not a URL', () => {
    expect(() => parseEnv({ ...validEnv, NEXT_PUBLIC_SUPABASE_URL: 'not-a-url' })).toThrow();
  });

  it('defaults POSTHOG_HOST to the US instance when omitted', () => {
    const withoutHost = { ...validEnv };
    delete (withoutHost as Record<string, string>).NEXT_PUBLIC_POSTHOG_HOST;
    const parsed = parseEnv(withoutHost);
    expect(parsed.NEXT_PUBLIC_POSTHOG_HOST).toBe('https://us.i.posthog.com');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
pnpm test
```
Expected: FAIL — `Cannot find module './env'`.

- [ ] **Step 3: Write the implementation**

Create `src/lib/env.ts`:
```typescript
import { z } from 'zod';

const envSchema = z.object({
  NEXT_PUBLIC_FIREBASE_API_KEY: z.string().min(1),
  NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN: z.string().min(1),
  NEXT_PUBLIC_FIREBASE_PROJECT_ID: z.string().min(1),
  NEXT_PUBLIC_FIREBASE_APP_ID: z.string().min(1),
  NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID: z.string().min(1),
  NEXT_PUBLIC_SUPABASE_URL: z.string().url(),
  NEXT_PUBLIC_SUPABASE_ANON_KEY: z.string().min(1),
  NEXT_PUBLIC_API_URL: z.string().url(),
  NEXT_PUBLIC_POSTHOG_KEY: z.string().min(1),
  NEXT_PUBLIC_POSTHOG_HOST: z.string().url().default('https://us.i.posthog.com'),
});

export type Env = z.infer<typeof envSchema>;

export function parseEnv(source: Record<string, string | undefined>): Env {
  return envSchema.parse(source);
}

export const env: Env = parseEnv(process.env as Record<string, string | undefined>);
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
pnpm test
```
Expected: PASS — all 4 cases green. Note: the module-level `parseEnv(process.env)` call may throw during test collection if env vars are missing. If so, see Step 5.

- [ ] **Step 5: Handle test environment safely**

If Step 4 failed with a zod parse error on import, set test env vars in `src/test-setup.ts`:

```typescript
import '@testing-library/jest-dom/vitest';

process.env.NEXT_PUBLIC_FIREBASE_API_KEY ??= 'test';
process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN ??= 'test.firebaseapp.com';
process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID ??= 'test';
process.env.NEXT_PUBLIC_FIREBASE_APP_ID ??= 'test';
process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID ??= 'test';
process.env.NEXT_PUBLIC_SUPABASE_URL ??= 'https://test.supabase.co';
process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ??= 'test';
process.env.NEXT_PUBLIC_API_URL ??= 'https://arohaastrology.in';
process.env.NEXT_PUBLIC_POSTHOG_KEY ??= 'test';
process.env.NEXT_PUBLIC_POSTHOG_HOST ??= 'https://us.i.posthog.com';
```

Re-run `pnpm test`. Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add src/lib/env.ts src/lib/env.test.ts src/test-setup.ts
git commit -m "feat: validate env vars at startup with zod"
```

---

## Phase 3 — Clients

### Task 3.1: Supabase client with Capacitor Preferences storage

**Files:**
- Create: `src/lib/supabase.ts`

- [ ] **Step 1: Create src/lib/supabase.ts**

```typescript
import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { Preferences } from '@capacitor/preferences';
import { env } from './env';

const capacitorStorage = {
  getItem: async (key: string): Promise<string | null> => {
    const { value } = await Preferences.get({ key });
    return value;
  },
  setItem: async (key: string, value: string): Promise<void> => {
    await Preferences.set({ key, value });
  },
  removeItem: async (key: string): Promise<void> => {
    await Preferences.remove({ key });
  },
};

export const supabase: SupabaseClient = createClient(
  env.NEXT_PUBLIC_SUPABASE_URL,
  env.NEXT_PUBLIC_SUPABASE_ANON_KEY,
  {
    auth: {
      storage: capacitorStorage,
      autoRefreshToken: true,
      persistSession: true,
      detectSessionInUrl: false,
    },
  },
);
```

- [ ] **Step 2: Verify TypeScript compiles**

```powershell
pnpm typecheck
```
Expected: PASS.

- [ ] **Step 3: Commit**

```powershell
git add src/lib/supabase.ts
git commit -m "feat: add Supabase client with Capacitor Preferences storage"
```

### Task 3.2: Firebase Phone Auth wrapper

**Files:**
- Create: `src/lib/firebase.ts`

- [ ] **Step 1: Create src/lib/firebase.ts**

```typescript
import { initializeApp, getApps, FirebaseApp } from 'firebase/app';
import { FirebaseAuthentication } from '@capacitor-firebase/authentication';
import { env } from './env';

let app: FirebaseApp | undefined;

export function getFirebaseApp(): FirebaseApp {
  if (app) return app;
  if (getApps().length > 0) {
    app = getApps()[0];
    return app;
  }
  app = initializeApp({
    apiKey: env.NEXT_PUBLIC_FIREBASE_API_KEY,
    authDomain: env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
    projectId: env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
    appId: env.NEXT_PUBLIC_FIREBASE_APP_ID,
    messagingSenderId: env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  });
  return app;
}

export async function sendPhoneOTP(phone: string): Promise<{ verificationId: string }> {
  getFirebaseApp();
  const result = await FirebaseAuthentication.signInWithPhoneNumber({
    phoneNumber: phone,
  });
  if (!result.verificationId) {
    throw new Error('Firebase did not return a verificationId');
  }
  return { verificationId: result.verificationId };
}

export async function confirmPhoneOTP(
  verificationId: string,
  verificationCode: string,
): Promise<{ idToken: string }> {
  await FirebaseAuthentication.confirmVerificationCode({
    verificationId,
    verificationCode,
  });
  const { token } = await FirebaseAuthentication.getIdToken();
  if (!token) {
    throw new Error('Firebase did not return an ID token');
  }
  return { idToken: token };
}

export async function firebaseSignOut(): Promise<void> {
  await FirebaseAuthentication.signOut();
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```powershell
pnpm typecheck
```
Expected: PASS.

- [ ] **Step 3: Commit**

```powershell
git add src/lib/firebase.ts
git commit -m "feat: add Firebase Phone Auth wrapper via Capacitor plugin"
```

### Task 3.3: PostHog analytics wrapper

**Files:**
- Create: `src/lib/analytics.ts`

- [ ] **Step 1: Create src/lib/analytics.ts**

```typescript
import posthog from 'posthog-js';
import { env } from './env';

let initialized = false;

export function initAnalytics(): void {
  if (initialized) return;
  if (typeof window === 'undefined') return;
  posthog.init(env.NEXT_PUBLIC_POSTHOG_KEY, {
    api_host: env.NEXT_PUBLIC_POSTHOG_HOST,
    persistence: 'localStorage+cookie',
    autocapture: true,
    capture_pageview: false,
  });
  initialized = true;
}

export function identifyUser(userId: string, properties: Record<string, unknown> = {}): void {
  if (!initialized) return;
  posthog.identify(userId, properties);
}

export function capture(event: string, properties: Record<string, unknown> = {}): void {
  if (!initialized) return;
  posthog.capture(event, properties);
}

export function resetAnalytics(): void {
  if (!initialized) return;
  posthog.reset();
}

export function capturePageview(path: string): void {
  capture('$pageview', { path });
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```powershell
pnpm typecheck
```
Expected: PASS.

- [ ] **Step 3: Commit**

```powershell
git add src/lib/analytics.ts
git commit -m "feat: add PostHog analytics wrapper"
```

---

## Phase 4 — Orchestration

### Task 4.1: Zustand auth store

**Files:**
- Create: `src/store/authStore.ts`

- [ ] **Step 1: Create src/store/authStore.ts**

```typescript
import { create } from 'zustand';
import type { Session } from '@supabase/supabase-js';

interface AuthState {
  session: Session | null;
  phone: string | null;
  verificationId: string | null;
  isNewUser: boolean;
  setSession: (session: Session | null) => void;
  setPhone: (phone: string | null) => void;
  setVerificationId: (id: string | null) => void;
  setIsNewUser: (isNew: boolean) => void;
  reset: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  session: null,
  phone: null,
  verificationId: null,
  isNewUser: false,
  setSession: (session) => set({ session }),
  setPhone: (phone) => set({ phone }),
  setVerificationId: (verificationId) => set({ verificationId }),
  setIsNewUser: (isNewUser) => set({ isNewUser }),
  reset: () => set({ session: null, phone: null, verificationId: null, isNewUser: false }),
}));
```

- [ ] **Step 2: Verify TypeScript compiles**

```powershell
pnpm typecheck
```
Expected: PASS.

- [ ] **Step 3: Commit**

```powershell
git add src/store/authStore.ts
git commit -m "feat: add Zustand auth store"
```

### Task 4.2: Auth orchestration (sendOTP → verifyOTP → Supabase exchange)

**Files:**
- Create: `src/lib/auth.ts`

- [ ] **Step 1: Create src/lib/auth.ts**

```typescript
import { sendPhoneOTP, confirmPhoneOTP, firebaseSignOut } from './firebase';
import { supabase } from './supabase';
import { env } from './env';
import { capture, identifyUser, resetAnalytics } from './analytics';

export interface VerifyResult {
  isNewUser: boolean;
}

export async function sendOTP(phone: string): Promise<{ verificationId: string }> {
  capture('auth_phone_submitted', { phone_country: '+91' });
  try {
    const { verificationId } = await sendPhoneOTP(phone);
    capture('auth_otp_sent');
    return { verificationId };
  } catch (err) {
    const error = err as { code?: string; message?: string };
    capture('auth_otp_send_failed', { error_code: error.code ?? 'unknown' });
    throw err;
  }
}

export async function verifyOTP(
  verificationId: string,
  code: string,
  phone: string,
  referralCode?: string,
): Promise<VerifyResult> {
  let idToken: string;
  try {
    const result = await confirmPhoneOTP(verificationId, code);
    idToken = result.idToken;
    capture('auth_otp_verified');
  } catch (err) {
    const error = err as { code?: string };
    capture('auth_otp_failed', { error_code: error.code ?? 'unknown' });
    throw err;
  }

  let tokenHash: string;
  let type: string;
  let isNewUser: boolean;
  try {
    const res = await fetch(`${env.NEXT_PUBLIC_API_URL}/api/auth/phone-signin`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken, ...(referralCode ? { referralCode } : {}) }),
    });
    if (!res.ok) {
      const body = (await res.json().catch(() => ({}))) as { error?: string };
      throw new Error(body.error ?? `Sign-in failed (HTTP ${res.status})`);
    }
    const json = (await res.json()) as { tokenHash: string; type: string; isNewUser: boolean };
    tokenHash = json.tokenHash;
    type = json.type;
    isNewUser = json.isNewUser;
  } catch (err) {
    capture('auth_exchange_failed', { stage: 'backend', error: (err as Error).message });
    await firebaseSignOut();
    throw err;
  }

  const { data, error } = await supabase.auth.verifyOtp({
    token_hash: tokenHash,
    type: type as 'email',
  });
  if (error || !data.session) {
    capture('auth_exchange_failed', { stage: 'supabase', error: error?.message ?? 'no session' });
    await firebaseSignOut();
    throw error ?? new Error('Supabase did not return a session');
  }

  capture('auth_session_established', { is_new_user: isNewUser });
  if (data.user) {
    identifyUser(data.user.id, { phone, isNewUser });
  }

  return { isNewUser };
}

export async function signOut(): Promise<void> {
  capture('logout');
  await supabase.auth.signOut();
  await firebaseSignOut();
  resetAnalytics();
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```powershell
pnpm typecheck
```
Expected: PASS.

- [ ] **Step 3: Commit**

```powershell
git add src/lib/auth.ts
git commit -m "feat: orchestrate Firebase OTP -> Supabase session exchange"
```

---

## Phase 5 — UI Components and Providers

### Task 5.1: PostHogProvider component

**Files:**
- Create: `src/components/PostHogProvider.tsx`

- [ ] **Step 1: Create src/components/PostHogProvider.tsx**

```typescript
'use client';

import { useEffect } from 'react';
import { usePathname } from 'next/navigation';
import { initAnalytics, capture, capturePageview } from '@/lib/analytics';

export function PostHogProvider({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  useEffect(() => {
    initAnalytics();
    capture('app_opened', {
      platform: typeof window !== 'undefined' && /Android|iPhone|iPad/.test(navigator.userAgent)
        ? /Android/.test(navigator.userAgent) ? 'android' : 'ios'
        : 'web',
      app_version: '0.1.0',
    });
  }, []);

  useEffect(() => {
    if (pathname) capturePageview(pathname);
  }, [pathname]);

  return <>{children}</>;
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```powershell
pnpm typecheck
```
Expected: PASS.

- [ ] **Step 3: Commit**

```powershell
git add src/components/PostHogProvider.tsx
git commit -m "feat: add PostHogProvider with pageview tracking"
```

### Task 5.2: AuthGate component

**Files:**
- Create: `src/components/AuthGate.tsx`

- [ ] **Step 1: Create src/components/AuthGate.tsx**

```typescript
'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/authStore';

export function AuthGate({ children }: { children: React.ReactNode }) {
  const session = useAuthStore((s) => s.session);
  const router = useRouter();

  useEffect(() => {
    if (session === null) {
      router.replace('/login');
    }
  }, [session, router]);

  if (!session) return null;
  return <>{children}</>;
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```powershell
pnpm typecheck
```
Expected: PASS.

- [ ] **Step 3: Commit**

```powershell
git add src/components/AuthGate.tsx
git commit -m "feat: add AuthGate that redirects unauthenticated users"
```

### Task 5.3: Root layout with Supabase session listener

**Files:**
- Create: `src/app/layout.tsx`

- [ ] **Step 1: Create src/app/layout.tsx**

```typescript
import './globals.css';
import { Suspense } from 'react';
import { PostHogProvider } from '@/components/PostHogProvider';
import { SessionBootstrap } from '@/components/SessionBootstrap';

export const metadata = {
  title: 'Aroha Astrology',
  description: 'Vedic astrology guidance',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Suspense fallback={null}>
          <PostHogProvider>
            <SessionBootstrap />
            {children}
          </PostHogProvider>
        </Suspense>
      </body>
    </html>
  );
}
```

- [ ] **Step 2: Create src/components/SessionBootstrap.tsx**

```typescript
'use client';

import { useEffect } from 'react';
import { supabase } from '@/lib/supabase';
import { useAuthStore } from '@/store/authStore';

export function SessionBootstrap() {
  const setSession = useAuthStore((s) => s.setSession);

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      setSession(data.session);
    });
    const { data: sub } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session);
    });
    return () => {
      sub.subscription.unsubscribe();
    };
  }, [setSession]);

  return null;
}
```

- [ ] **Step 3: Verify TypeScript compiles**

```powershell
pnpm typecheck
```
Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add src/app/layout.tsx src/components/SessionBootstrap.tsx
git commit -m "feat: root layout with Supabase session listener"
```

---

## Phase 6 — Pages

### Task 6.1: Entry page with session-based redirect

**Files:**
- Create: `src/app/page.tsx`

- [ ] **Step 1: Create src/app/page.tsx**

```typescript
'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/authStore';

export default function EntryPage() {
  const session = useAuthStore((s) => s.session);
  const router = useRouter();

  useEffect(() => {
    if (session === null) router.replace('/login');
    else if (session) router.replace('/home');
  }, [session, router]);

  return (
    <main className="flex min-h-screen items-center justify-center">
      <p className="text-sm opacity-60">Loading…</p>
    </main>
  );
}
```

- [ ] **Step 2: Commit**

```powershell
git add src/app/page.tsx
git commit -m "feat: entry page redirects based on Supabase session"
```

### Task 6.2: (auth) layout and /login page

**Files:**
- Create: `src/app/(auth)/layout.tsx`
- Create: `src/app/(auth)/login/page.tsx`

- [ ] **Step 1: Create src/app/(auth)/layout.tsx**

```typescript
'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/authStore';

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  const session = useAuthStore((s) => s.session);
  const router = useRouter();

  useEffect(() => {
    if (session) router.replace('/home');
  }, [session, router]);

  return <main className="min-h-screen flex flex-col px-6 py-12">{children}</main>;
}
```

- [ ] **Step 2: Create src/app/(auth)/login/page.tsx**

```typescript
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { normaliseIndianPhone, isValidIndianMobile } from '@/lib/phone';
import { sendOTP } from '@/lib/auth';
import { useAuthStore } from '@/store/authStore';

export default function LoginPage() {
  const [raw, setRaw] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const setPhone = useAuthStore((s) => s.setPhone);
  const setVerificationId = useAuthStore((s) => s.setVerificationId);
  const router = useRouter();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const digits = raw.replace(/\D/g, '').replace(/^0/, '');
    if (!isValidIndianMobile(digits)) {
      setError('Enter a valid 10-digit Indian mobile starting with 6, 7, 8, or 9.');
      return;
    }
    const phone = normaliseIndianPhone(digits);
    setLoading(true);
    try {
      const { verificationId } = await sendOTP(phone);
      setPhone(phone);
      setVerificationId(verificationId);
      router.push('/verify');
    } catch (err) {
      setError((err as Error).message || 'Failed to send OTP. Try again.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex-1 flex flex-col justify-center max-w-sm mx-auto w-full">
      <h1 className="text-3xl font-semibold mb-2">Welcome</h1>
      <p className="text-sm opacity-60 mb-8">Enter your mobile to receive a one-time code.</p>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="flex gap-2">
          <span className="px-3 py-3 rounded bg-white/5 text-sm">+91</span>
          <input
            type="tel"
            inputMode="numeric"
            maxLength={10}
            value={raw}
            onChange={(e) => setRaw(e.target.value)}
            placeholder="9876543210"
            className="flex-1 px-3 py-3 rounded bg-white/5 outline-none border border-white/10 focus:border-white/30"
          />
        </div>
        {error && <p className="text-sm text-red-400">{error}</p>}
        <button
          type="submit"
          disabled={loading}
          className="w-full py-3 rounded font-semibold disabled:opacity-50"
          style={{ background: 'var(--accent)', color: '#000' }}
        >
          {loading ? 'Sending…' : 'Send code'}
        </button>
      </form>
    </div>
  );
}
```

- [ ] **Step 3: Verify TypeScript compiles**

```powershell
pnpm typecheck
```
Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add "src/app/(auth)/layout.tsx" "src/app/(auth)/login/page.tsx"
git commit -m "feat: add /login page with phone OTP submission"
```

### Task 6.3: /verify page

**Files:**
- Create: `src/app/(auth)/verify/page.tsx`

- [ ] **Step 1: Create src/app/(auth)/verify/page.tsx**

```typescript
'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { verifyOTP, sendOTP } from '@/lib/auth';
import { useAuthStore } from '@/store/authStore';

const RESEND_LOCKOUT_SECONDS = 30;

export default function VerifyPage() {
  const phone = useAuthStore((s) => s.phone);
  const verificationId = useAuthStore((s) => s.verificationId);
  const setVerificationId = useAuthStore((s) => s.setVerificationId);
  const setIsNewUser = useAuthStore((s) => s.setIsNewUser);
  const router = useRouter();

  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [resendIn, setResendIn] = useState(RESEND_LOCKOUT_SECONDS);

  useEffect(() => {
    if (!phone || !verificationId) {
      router.replace('/login');
    }
  }, [phone, verificationId, router]);

  useEffect(() => {
    if (resendIn <= 0) return;
    const t = setTimeout(() => setResendIn((n) => n - 1), 1000);
    return () => clearTimeout(t);
  }, [resendIn]);

  async function handleVerify(e: React.FormEvent) {
    e.preventDefault();
    if (!verificationId || !phone) return;
    if (code.length !== 6) {
      setError('Enter the 6-digit code.');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const { isNewUser } = await verifyOTP(verificationId, code, phone);
      setIsNewUser(isNewUser);
      router.replace('/home');
    } catch (err) {
      setError((err as Error).message || 'Invalid code, try again.');
    } finally {
      setLoading(false);
    }
  }

  async function handleResend() {
    if (resendIn > 0 || !phone) return;
    setError(null);
    try {
      const { verificationId: newId } = await sendOTP(phone);
      setVerificationId(newId);
      setResendIn(RESEND_LOCKOUT_SECONDS);
    } catch (err) {
      setError((err as Error).message || 'Failed to resend. Try again.');
    }
  }

  return (
    <div className="flex-1 flex flex-col justify-center max-w-sm mx-auto w-full">
      <h1 className="text-3xl font-semibold mb-2">Verify</h1>
      <p className="text-sm opacity-60 mb-8">
        Sent to <strong>{phone}</strong>
      </p>
      <form onSubmit={handleVerify} className="space-y-4">
        <input
          type="text"
          inputMode="numeric"
          maxLength={6}
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
          placeholder="123456"
          className="w-full px-3 py-3 rounded bg-white/5 outline-none border border-white/10 focus:border-white/30 text-center text-2xl tracking-widest"
        />
        {error && <p className="text-sm text-red-400">{error}</p>}
        <button
          type="submit"
          disabled={loading}
          className="w-full py-3 rounded font-semibold disabled:opacity-50"
          style={{ background: 'var(--accent)', color: '#000' }}
        >
          {loading ? 'Verifying…' : 'Verify'}
        </button>
        <button
          type="button"
          onClick={handleResend}
          disabled={resendIn > 0}
          className="w-full py-2 text-sm opacity-70 disabled:opacity-30"
        >
          {resendIn > 0 ? `Resend in ${resendIn}s` : 'Resend code'}
        </button>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```powershell
pnpm typecheck
```
Expected: PASS.

- [ ] **Step 3: Commit**

```powershell
git add "src/app/(auth)/verify/page.tsx"
git commit -m "feat: add /verify page with OTP entry and resend timer"
```

### Task 6.4: (app) layout and /home page

**Files:**
- Create: `src/app/(app)/layout.tsx`
- Create: `src/app/(app)/home/page.tsx`

- [ ] **Step 1: Create src/app/(app)/layout.tsx**

```typescript
import { AuthGate } from '@/components/AuthGate';

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGate>
      <main className="min-h-screen px-6 py-12">{children}</main>
    </AuthGate>
  );
}
```

- [ ] **Step 2: Create src/app/(app)/home/page.tsx**

```typescript
'use client';

import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/authStore';
import { signOut } from '@/lib/auth';

export default function HomePage() {
  const session = useAuthStore((s) => s.session);
  const router = useRouter();

  const phone = session?.user?.phone || session?.user?.user_metadata?.phone || 'friend';

  async function handleLogout() {
    await signOut();
    router.replace('/login');
  }

  return (
    <div className="max-w-sm mx-auto w-full flex flex-col gap-6">
      <h1 className="text-4xl font-semibold">Hello, {phone}</h1>
      <p className="text-sm opacity-60">
        You are signed in. This is the foundation — product features land in the next plan.
      </p>
      <button
        onClick={handleLogout}
        className="self-start py-2 px-4 rounded border border-white/20 text-sm"
      >
        Log out
      </button>
    </div>
  );
}
```

- [ ] **Step 3: Verify TypeScript compiles**

```powershell
pnpm typecheck
```
Expected: PASS.

- [ ] **Step 4: Verify production build succeeds**

```powershell
pnpm build
```
Expected: builds successfully, producing `./out/`. If errors, fix before commit.

- [ ] **Step 5: Commit**

```powershell
git add "src/app/(app)/layout.tsx" "src/app/(app)/home/page.tsx"
git commit -m "feat: add /home Hello World screen with logout"
```

---

## Phase 7 — Capacitor Native

### Task 7.1: Initialize Capacitor configuration

**Files:**
- Create: `capacitor.config.ts`

- [ ] **Step 1: Create capacitor.config.ts**

```typescript
import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.arohaastrology.app',
  appName: 'Aroha Astrology',
  webDir: 'out',
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
```

- [ ] **Step 2: Commit**

```powershell
git add capacitor.config.ts
git commit -m "feat: add Capacitor config"
```

### Task 7.2: Add Android platform and wire google-services.json

**Files:**
- Create: `android/` (Capacitor-generated)
- Modify: `android/app/google-services.json` (copied from root)

- [ ] **Step 1: Build first so `out/` exists**

```powershell
pnpm build
```
Expected: produces `out/`.

- [ ] **Step 2: Add Android platform**

```powershell
pnpm dlx @capacitor/cli@latest add android
```
Expected: creates `android/` directory with Gradle project.

- [ ] **Step 3: Copy google-services.json into Android app module**

```powershell
Copy-Item google-services.json android/app/google-services.json
```
Expected: file exists at `android/app/google-services.json`.

- [ ] **Step 4: Add Google Services Gradle plugin**

Edit `android/build.gradle`. Find the `dependencies` block inside `buildscript` and add:
```
classpath 'com.google.gms:google-services:4.4.2'
```

Edit `android/app/build.gradle`. At the very top of the file, after the existing `apply plugin` lines, add:
```
apply plugin: 'com.google.gms.google-services'
```

- [ ] **Step 5: Sync web assets into Android**

```powershell
pnpm dlx @capacitor/cli@latest sync android
```
Expected: copies `out/` into `android/app/src/main/assets/public`.

- [ ] **Step 6: Verify Android Gradle build succeeds (debug)**

```powershell
cd android
./gradlew assembleDebug
cd ..
```
Expected: BUILD SUCCESSFUL. APK at `android/app/build/outputs/apk/debug/app-debug.apk`.

If Gradle wrapper isn't executable on Windows, run `./gradlew.bat assembleDebug` instead.

- [ ] **Step 7: Commit**

```powershell
git add android capacitor.config.ts
git commit -m "feat: add Android platform with Firebase google-services.json"
```

### Task 7.3: Add iOS platform

**Files:**
- Create: `ios/` (Capacitor-generated)

> Note: This task requires macOS for full verification. On Windows, create the project but skip the Xcode build verification step — it will be performed by the developer with a Mac. Document this in the commit message.

- [ ] **Step 1: Add iOS platform**

```powershell
pnpm dlx @capacitor/cli@latest add ios
```
Expected: creates `ios/` directory. On Windows this may warn about CocoaPods being unavailable — that's acceptable; pods are installed on macOS during the Xcode build.

- [ ] **Step 2: Sync web assets**

```powershell
pnpm dlx @capacitor/cli@latest sync ios
```
Expected: copies `out/` into `ios/App/App/public`.

- [ ] **Step 3: Document GoogleService-Info.plist requirement**

Create `ios/README-firebase-setup.md`:
```markdown
# iOS Firebase Setup

The Firebase Authentication plugin needs `GoogleService-Info.plist` to be added to the Xcode project.

## Steps (macOS only)

1. Download `GoogleService-Info.plist` from the Firebase Console for the `com.arohaastrology.app` iOS app.
2. Open `ios/App/App.xcworkspace` in Xcode.
3. Drag `GoogleService-Info.plist` into the `App` target group. Check "Copy items if needed" and select the `App` target.
4. Run `pod install` in `ios/App/`.
5. Build and run on a simulator or physical device.
```

- [ ] **Step 4: Commit**

```powershell
git add ios
git commit -m "feat: add iOS platform (GoogleService-Info.plist setup required on macOS)"
```

---

## Phase 8 — CI

### Task 8.1: Add .env.example

**Files:**
- Create: `.env.example`

- [ ] **Step 1: Create .env.example**

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

- [ ] **Step 2: Commit**

```powershell
git add .env.example
git commit -m "chore: document required environment variables"
```

### Task 8.2: PR validation workflow

**Files:**
- Create: `.github/workflows/pr.yml`

- [ ] **Step 1: Create .github/workflows/pr.yml**

```yaml
name: PR validation

on:
  pull_request:
    branches: [develop, main]

jobs:
  checks:
    runs-on: ubuntu-latest
    env:
      NEXT_PUBLIC_FIREBASE_API_KEY: ci-placeholder
      NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN: arohaastrology.firebaseapp.com
      NEXT_PUBLIC_FIREBASE_PROJECT_ID: ci-placeholder
      NEXT_PUBLIC_FIREBASE_APP_ID: ci-placeholder
      NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID: '123456'
      NEXT_PUBLIC_SUPABASE_URL: https://ci.supabase.co
      NEXT_PUBLIC_SUPABASE_ANON_KEY: ci-placeholder
      NEXT_PUBLIC_API_URL: https://arohaastrology.in
      NEXT_PUBLIC_POSTHOG_KEY: ci-placeholder
      NEXT_PUBLIC_POSTHOG_HOST: https://us.i.posthog.com
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v4
        with:
          version: 9
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: pnpm
      - run: pnpm install --frozen-lockfile
      - run: pnpm lint
      - run: pnpm typecheck
      - run: pnpm test
      - run: pnpm build
```

- [ ] **Step 2: Commit**

```powershell
git add .github/workflows/pr.yml
git commit -m "ci: add PR validation workflow (lint, typecheck, test, build)"
```

### Task 8.3: Android debug APK workflow (push to `develop`)

**Files:**
- Create: `.github/workflows/develop-android.yml`

- [ ] **Step 1: Create .github/workflows/develop-android.yml**

```yaml
name: Android debug build

on:
  push:
    branches: [develop]

jobs:
  android-debug:
    runs-on: ubuntu-latest
    env:
      NEXT_PUBLIC_FIREBASE_API_KEY: ${{ secrets.NEXT_PUBLIC_FIREBASE_API_KEY }}
      NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN: ${{ secrets.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN }}
      NEXT_PUBLIC_FIREBASE_PROJECT_ID: ${{ secrets.NEXT_PUBLIC_FIREBASE_PROJECT_ID }}
      NEXT_PUBLIC_FIREBASE_APP_ID: ${{ secrets.NEXT_PUBLIC_FIREBASE_APP_ID }}
      NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID: ${{ secrets.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID }}
      NEXT_PUBLIC_SUPABASE_URL: ${{ secrets.NEXT_PUBLIC_SUPABASE_URL }}
      NEXT_PUBLIC_SUPABASE_ANON_KEY: ${{ secrets.NEXT_PUBLIC_SUPABASE_ANON_KEY }}
      NEXT_PUBLIC_API_URL: https://arohaastrology.in
      NEXT_PUBLIC_POSTHOG_KEY: ${{ secrets.NEXT_PUBLIC_POSTHOG_KEY }}
      NEXT_PUBLIC_POSTHOG_HOST: https://us.i.posthog.com
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v4
        with:
          version: 9
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: pnpm
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - name: Decode google-services.json
        run: |
          echo "${{ secrets.GOOGLE_SERVICES_JSON }}" | base64 -d > android/app/google-services.json
      - run: pnpm install --frozen-lockfile
      - run: pnpm build
      - run: pnpm dlx @capacitor/cli@latest sync android
      - name: Assemble debug APK
        working-directory: android
        run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: android/app/build/outputs/apk/debug/app-debug.apk
          retention-days: 14
```

- [ ] **Step 2: Commit**

```powershell
git add .github/workflows/develop-android.yml
git commit -m "ci: build Android debug APK on push to dev"
```

---

## Phase 9 — Verification

### Task 9.1: Local dev smoke test

**Files:** None (manual verification)

- [ ] **Step 1: Create local .env.local with real values**

Create `.env.local` (already gitignored via Task 1.1b) with actual Firebase, Supabase, and PostHog values. Do NOT commit. Template:

```
NEXT_PUBLIC_FIREBASE_API_KEY=<real value from Firebase console>
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=arohaastrology.firebaseapp.com
NEXT_PUBLIC_FIREBASE_PROJECT_ID=<real value>
NEXT_PUBLIC_FIREBASE_APP_ID=<real value>
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=<real value>
NEXT_PUBLIC_SUPABASE_URL=<real value>
NEXT_PUBLIC_SUPABASE_ANON_KEY=<real value>
NEXT_PUBLIC_API_URL=https://arohaastrology.in
NEXT_PUBLIC_POSTHOG_KEY=<real value>
NEXT_PUBLIC_POSTHOG_HOST=https://us.i.posthog.com
```

- [ ] **Step 2: Run dev server**

```powershell
pnpm dev
```
Expected: server starts at http://localhost:3000.

- [ ] **Step 3: Open browser and verify /login renders**

Open http://localhost:3000 in a browser. Expected:
- Redirect to `/login`
- See "Welcome" heading
- See `+91` prefix + phone input + "Send code" button

> Phone OTP testing in the browser dev server is **not in scope** for this verification (Firebase Phone Auth requires reCAPTCHA on web, which is fragile in localhost). Phone OTP is verified on device in Task 9.2.

- [ ] **Step 4: Stop the dev server**

`Ctrl+C` in the terminal.

### Task 9.2: Build and install Android debug APK on a physical device

**Files:** None (manual verification)

- [ ] **Step 1: Connect an Android device with USB debugging enabled**

Run `adb devices` to confirm the device appears.

- [ ] **Step 2: Build and install**

```powershell
pnpm build
pnpm dlx @capacitor/cli@latest sync android
cd android
./gradlew installDebug
cd ..
```
Expected: app installs on the device.

- [ ] **Step 3: Open the app on the device and verify the full auth flow**

- Verify `/login` renders with the phone input
- Enter a real Indian mobile number
- Verify SMS OTP arrives within 30 seconds
- Enter the OTP code
- Verify `/home` renders showing `Hello, +91XXXXXXXXXX`
- Tap "Log out"
- Verify return to `/login` with no session

- [ ] **Step 4: Verify PostHog received events**

Open the PostHog dashboard. Expected events received within 1 minute:
- `app_opened`
- `auth_phone_submitted`
- `auth_otp_sent`
- `auth_otp_verified`
- `auth_session_established`
- `logout`

Plus `$pageview` for each route change.

### Task 9.3: Push branch and open PR

**Files:** None (git/GitHub)

- [ ] **Step 1: Push the working branch**

```powershell
git push -u origin feature/foundation
```

- [ ] **Step 2: Open PR `feature/foundation` → `develop`**

```powershell
gh pr create --base develop --title "feat: Hello World foundation (Next.js + Capacitor)" --body "Implements docs/superpowers/specs/2026-05-25-hello-world-foundation-design.md."
```
Expected: PR created. The `pr.yml` workflow should run automatically.

- [ ] **Step 3: Wait for `pr.yml` workflow to pass**

```powershell
gh pr checks --watch
```
Expected: all checks pass. If any fail, diagnose, push fixes, repeat.

- [ ] **Step 4: Merge the PR to `dev`**

Either via GitHub UI or:
```powershell
gh pr merge --squash
```

- [ ] **Step 5: Verify develop-android.yml ran and produced an APK artifact**

```powershell
gh run list --workflow develop-android.yml --branch dev --limit 1
```

Open the latest run's page in the browser; confirm `app-debug` artifact is downloadable.

- [ ] **Step 6: Download and install the CI-built APK on a device**

Download the APK from the workflow run page, install it, and repeat the auth flow from Task 9.2 Step 3. Confirm it works.

---

## Done

When all tasks above are complete and verified:
- `feature/foundation` is merged to `dev`
- `develop-android.yml` produces a working APK
- A physical Android device can complete the full OTP → Hello World flow
- PostHog dashboard shows events
- iOS project exists but is verified manually on macOS by the developer
