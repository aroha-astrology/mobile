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
