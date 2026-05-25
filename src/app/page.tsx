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
