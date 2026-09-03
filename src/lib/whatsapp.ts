// Utilitários para a funcionalidade "click-to-chat" do WhatsApp (wa.me).
// Não existe (nem se finge existir) nenhuma integração oficial da
// WhatsApp Business API — isto apenas abre a aplicação do utilizador com
// uma mensagem pré-preenchida, tal como descrito na especificação do
// CobrançaPro. Nunca confirmamos entrega ou leitura da mensagem.

export type PhoneNormalizationResult =
  | { ok: true; e164Digits: string }
  | { ok: false; reason: string };

// Normaliza um número de telefone para o formato que o wa.me espera
// (código do país + número, só dígitos), com regras específicas para
// Angola (código 244, números móveis de 9 dígitos a começar por 9).
// Nunca "adivinha" de forma destrutiva: se o formato não for reconhecível
// com confiança, devolve um motivo claro em vez de gerar um link inválido.
export function normalizePhoneForWhatsApp(rawPhone: string | null | undefined): PhoneNormalizationResult {
  const trimmed = (rawPhone ?? '').trim();

  if (!trimmed) {
    return { ok: false, reason: 'Este cliente não tem telefone registado.' };
  }

  const hasLeadingPlus = trimmed.startsWith('+');
  const digits = trimmed.replace(/\D/g, '');

  if (!digits) {
    return { ok: false, reason: 'O telefone deste cliente não contém números válidos.' };
  }

  // Já em formato internacional explícito (+244... ou outro país).
  if (hasLeadingPlus) {
    if (digits.length < 8) {
      return { ok: false, reason: 'O telefone deste cliente parece incompleto.' };
    }
    return { ok: true, e164Digits: digits };
  }

  // Código de Angola já incluído sem "+" (244 + 9 dígitos = 12 dígitos).
  if (digits.startsWith('244') && digits.length === 12) {
    return { ok: true, e164Digits: digits };
  }

  // Formato local com "0" inicial (0 + 9 dígitos = 10 dígitos), típico de
  // números angolanos escritos localmente (ex: 0923 456 789).
  if (digits.length === 10 && digits.startsWith('0') && digits[1] === '9') {
    return { ok: true, e164Digits: `244${digits.slice(1)}` };
  }

  // Número móvel angolano "nu", sem indicativo (9 dígitos a começar por 9).
  if (digits.length === 9 && digits.startsWith('9')) {
    return { ok: true, e164Digits: `244${digits}` };
  }

  return {
    ok: false,
    reason:
      'Não foi possível reconhecer o formato deste número de telefone. Verifique o telefone do cliente antes de enviar por WhatsApp.',
  };
}

export type WhatsAppLinkResult = { ok: true; url: string } | { ok: false; reason: string };

export function buildWhatsAppLink(phone: string | null | undefined, message: string): WhatsAppLinkResult {
  const normalized = normalizePhoneForWhatsApp(phone);
  if (!normalized.ok) {
    return { ok: false, reason: normalized.reason };
  }
  return { ok: true, url: `https://wa.me/${normalized.e164Digits}?text=${encodeURIComponent(message)}` };
}
