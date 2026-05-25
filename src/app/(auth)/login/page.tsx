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
