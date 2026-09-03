interface UsageBarProps {
  label: string;
  current: number;
  max: number | null;
}

export function UsageBar({ label, current, max }: UsageBarProps) {
  const isUnlimited = max === null;
  const ratio = isUnlimited ? 0 : max === 0 ? 1 : Math.min(1, current / max);
  const isNearLimit = !isUnlimited && ratio >= 0.8;
  const isAtLimit = !isUnlimited && current >= (max as number);

  return (
    <div>
      <div className="flex items-center justify-between text-sm">
        <span className="text-slate-600 dark:text-slate-400">{label}</span>
        <span
          className={`font-medium ${
            isAtLimit
              ? 'text-red-600 dark:text-red-400'
              : isNearLimit
                ? 'text-amber-600 dark:text-amber-400'
                : 'text-slate-700 dark:text-slate-300'
          }`}
        >
          {current}
          {isUnlimited ? '' : ` / ${max}`}
        </span>
      </div>
      {!isUnlimited && (
        <div className="mt-1.5 h-2 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
          <div
            className={`h-full rounded-full transition-all ${
              isAtLimit ? 'bg-red-500' : isNearLimit ? 'bg-amber-500' : 'bg-brand-500'
            }`}
            style={{ width: `${ratio * 100}%` }}
          />
        </div>
      )}
    </div>
  );
}
