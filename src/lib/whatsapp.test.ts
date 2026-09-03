import { describe, expect, it } from 'vitest';
import { buildWhatsAppLink, normalizePhoneForWhatsApp } from './whatsapp';

describe('normalizePhoneForWhatsApp', () => {
  it('rejects an empty phone', () => {
    const result = normalizePhoneForWhatsApp('');
    expect(result.ok).toBe(false);
  });

  it('accepts a bare Angolan mobile number (9 digits, starts with 9)', () => {
    const result = normalizePhoneForWhatsApp('923456789');
    expect(result).toEqual({ ok: true, e164Digits: '244923456789' });
  });

  it('accepts a local number with leading 0', () => {
    const result = normalizePhoneForWhatsApp('0923456789');
    expect(result).toEqual({ ok: true, e164Digits: '244923456789' });
  });

  it('accepts a number already carrying the country code', () => {
    const result = normalizePhoneForWhatsApp('244923456789');
    expect(result).toEqual({ ok: true, e164Digits: '244923456789' });
  });

  it('accepts an explicit international format with +', () => {
    const result = normalizePhoneForWhatsApp('+244 923 456 789');
    expect(result).toEqual({ ok: true, e164Digits: '244923456789' });
  });

  it('rejects an unrecognizable format instead of guessing', () => {
    const result = normalizePhoneForWhatsApp('12345');
    expect(result.ok).toBe(false);
  });
});

describe('buildWhatsAppLink', () => {
  it('builds a wa.me link with the message encoded', () => {
    const result = buildWhatsAppLink('923456789', 'Olá, tudo bem?');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.url).toBe('https://wa.me/244923456789?text=Ol%C3%A1%2C%20tudo%20bem%3F');
    }
  });

  it('fails with a clear reason when the client has no phone', () => {
    const result = buildWhatsAppLink(null, 'Olá!');
    expect(result.ok).toBe(false);
  });
});
