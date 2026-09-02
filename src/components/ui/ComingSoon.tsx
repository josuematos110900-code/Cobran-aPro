import { Construction } from 'lucide-react';

export function ComingSoon({ title }: { title: string }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 p-10 text-center">
      <Construction className="h-10 w-10 text-slate-400" aria-hidden="true" />
      <h2 className="text-lg font-semibold text-slate-900 dark:text-white">{title}</h2>
      <p className="max-w-sm text-sm text-slate-500 dark:text-slate-400">
        Esta funcionalidade estará disponível em breve.
      </p>
    </div>
  );
}
