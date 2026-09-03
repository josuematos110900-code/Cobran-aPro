import { Select } from '@/components/ui/Select';
import { Input } from '@/components/ui/Input';
import { PERIOD_PRESET_LABELS, toDateKey, type ReportPeriodPreset } from './types';

interface PeriodSelectorProps {
  preset: ReportPeriodPreset;
  customFrom: string;
  customTo: string;
  onPresetChange: (preset: ReportPeriodPreset) => void;
  onCustomChange: (from: string, to: string) => void;
}

export function PeriodSelector({ preset, customFrom, customTo, onPresetChange, onCustomChange }: PeriodSelectorProps) {
  const today = toDateKey(new Date());

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
      <div className="sm:w-56">
        <Select
          aria-label="Seleccionar período"
          value={preset}
          onChange={(e) => onPresetChange(e.target.value as ReportPeriodPreset)}
        >
          {Object.entries(PERIOD_PRESET_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
      </div>

      {preset === 'custom' && (
        <div className="flex flex-1 flex-col gap-3 sm:flex-row">
          <Input
            label="De"
            type="date"
            value={customFrom}
            max={customTo || today}
            onChange={(e) => onCustomChange(e.target.value, customTo)}
          />
          <Input
            label="Até"
            type="date"
            value={customTo}
            min={customFrom}
            max={today}
            onChange={(e) => onCustomChange(customFrom, e.target.value)}
          />
        </div>
      )}
    </div>
  );
}
