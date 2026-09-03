import { createClient } from '@supabase/supabase-js';
import type { Database } from './types/database';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;

if (!supabaseUrl || !supabaseAnonKey) {
  // Falha alto e cedo: é preferível parar a aplicação a correr sem
  // ligação à base de dados e mascarar erros confusos mais tarde.
  throw new Error(
    'Configuração do Supabase em falta. Verifique se VITE_SUPABASE_URL e ' +
      'VITE_SUPABASE_ANON_KEY estão definidas no seu ficheiro .env.local ' +
      '(veja .env.example).'
  );
}

// Apenas a chave "anon" (pública) é usada aqui. Nunca importar ou usar a
// SERVICE_ROLE_KEY no frontend — essa chave ignora o Row Level Security
// e só deve existir em ambiente de servidor (funções serverless / cron).
export const supabase = createClient<Database>(supabaseUrl, supabaseAnonKey, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
    detectSessionInUrl: true,
  },
});
