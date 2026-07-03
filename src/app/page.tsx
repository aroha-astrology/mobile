export default function FallbackPage() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center px-6 text-center gap-4">
      <h1 className="text-2xl font-semibold">Aroha Astrology</h1>
      <p className="text-sm opacity-60 max-w-xs">
        Connecting to the app… If this screen stays, check your internet connection.
      </p>
    </main>
  );
}
