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
