// Traduz mensagens de erro comuns do Supabase Auth para português,
// evitando mostrar stack traces ou mensagens técnicas ao utilizador final.
export function translateAuthError(message: string): string {
  const normalized = message.toLowerCase();

  if (normalized.includes('invalid login credentials')) {
    return 'Email ou palavra-passe incorrectos.';
  }
  if (normalized.includes('user already registered')) {
    return 'Já existe uma conta registada com este email.';
  }
  if (normalized.includes('email not confirmed')) {
    return 'Confirme o seu email antes de iniciar sessão. Verifique a sua caixa de entrada.';
  }
  if (normalized.includes('password should be at least')) {
    return 'A palavra-passe deve ter pelo menos 6 caracteres.';
  }
  if (normalized.includes('rate limit')) {
    return 'Demasiadas tentativas. Aguarde alguns minutos e tente novamente.';
  }
  if (normalized.includes('network')) {
    return 'Não foi possível ligar ao servidor. Verifique a sua ligação à internet.';
  }

  return 'Ocorreu um erro inesperado. Tente novamente em instantes.';
}
