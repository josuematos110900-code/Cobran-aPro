export type ReportPeriodPreset =
  | 'today'
  | 'last_7_days'
  | 'this_month'
  | 'last_month'
  | 'last_3_months'
  | 'this_year'
  | 'custom';

export type ReportGranularity = 'day' | 'week' | 'month';

export interface ReportPeriod {
  preset: ReportPeriodPreset;
  from: string; // 'YYYY-MM-DD', calendário local — nunca via toISOString()
  to: string; // 'YYYY-MM-DD'
}

export const PERIOD_PRESET_LABELS: Record<ReportPeriodPreset, string> = {
  today: 'Hoje',
  last_7_days: 'Últimos 7 dias',
  this_month: 'Este mês',
  last_month: 'Mês anterior',
  last_3_months: 'Últimos 3 meses',
  this_year: 'Este ano',
  custom: 'Período personalizado',
};

// Formata uma data para 'YYYY-MM-DD' usando os componentes LOCAIS
// (getFullYear/getMonth/getDate), nunca toISOString() — que converte
// para UTC e pode desviar o dia junto à meia-noite, dependendo do fuso
// horário do browser. Isto é o que garante que "hoje" no seletor de
// período corresponde exactamente a "hoje" para quem está a usá-lo.
export function toDateKey(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function addDays(date: Date, days: number): Date {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}

export function computePeriodRange(preset: ReportPeriodPreset, customFrom?: string, customTo?: string): { from: string; to: string } {
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());

  switch (preset) {
    case 'today':
      return { from: toDateKey(today), to: toDateKey(today) };
    case 'last_7_days':
      return { from: toDateKey(addDays(today, -6)), to: toDateKey(today) };
    case 'this_month':
      return {
        from: toDateKey(new Date(today.getFullYear(), today.getMonth(), 1)),
        to: toDateKey(new Date(today.getFullYear(), today.getMonth() + 1, 0)),
      };
    case 'last_month': {
      const lastMonth = today.getMonth() - 1;
      const year = lastMonth < 0 ? today.getFullYear() - 1 : today.getFullYear();
      const month = (lastMonth + 12) % 12;
      return {
        from: toDateKey(new Date(year, month, 1)),
        to: toDateKey(new Date(year, month + 1, 0)),
      };
    }
    case 'last_3_months':
      return {
        from: toDateKey(new Date(today.getFullYear(), today.getMonth() - 2, 1)),
        to: toDateKey(new Date(today.getFullYear(), today.getMonth() + 1, 0)),
      };
    case 'this_year':
      return {
        from: toDateKey(new Date(today.getFullYear(), 0, 1)),
        to: toDateKey(new Date(today.getFullYear(), 11, 31)),
      };
    case 'custom':
      return { from: customFrom || toDateKey(today), to: customTo || toDateKey(today) };
    default:
      return { from: toDateKey(today), to: toDateKey(today) };
  }
}

// Granularidade por defeito, sensata para cada preset — o utilizador
// pode sempre substituir manualmente no seletor de granularidade.
export function defaultGranularityForPreset(preset: ReportPeriodPreset): ReportGranularity {
  switch (preset) {
    case 'today':
    case 'last_7_days':
    case 'this_month':
    case 'last_month':
      return 'day';
    case 'last_3_months':
      return 'week';
    case 'this_year':
      return 'month';
    default:
      return 'day';
  }
}

// ---------------------------------------------------------------------
// Utilitário CSV local — sem nova dependência. Faz o "escaping" mínimo
// exigido pelo formato (RFC 4180): campos com vírgula, aspas ou quebras
// de linha ficam entre aspas, com aspas internas duplicadas.
// ---------------------------------------------------------------------
function escapeCsvField(value: string | number | null | undefined): string {
  const str = value === null || value === undefined ? '' : String(value);
  if (/[",\n]/.test(str)) {
    return `"${str.replace(/"/g, '""')}"`;
  }
  return str;
}

export function buildCsv(headers: string[], rows: (string | number | null | undefined)[][]): string {
  const lines = [headers.map(escapeCsvField).join(',')];
  for (const row of rows) {
    lines.push(row.map(escapeCsvField).join(','));
  }
  // BOM UTF-8 no início para o Excel abrir acentos/Kz correctamente.
  return '\uFEFF' + lines.join('\r\n');
}

export function downloadCsv(filename: string, csvContent: string): void {
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
