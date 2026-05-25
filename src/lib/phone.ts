export function normaliseIndianPhone(raw: string): string {
  const digits = raw.replace(/\D/g, '').replace(/^0/, '');
  return `+91${digits}`;
}

export function isValidIndianMobile(local: string): boolean {
  return /^[6-9]\d{9}$/.test(local);
}
