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
