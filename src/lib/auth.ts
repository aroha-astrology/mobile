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
