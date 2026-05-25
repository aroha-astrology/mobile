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
