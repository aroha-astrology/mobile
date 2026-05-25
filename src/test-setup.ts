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
