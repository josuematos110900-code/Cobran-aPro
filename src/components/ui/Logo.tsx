// Ícone da marca, embutido em SVG (mesmo desenho de public/favicon.svg)
// para poder herdar `className` e ser usado em qualquer sítio — sidebar,
// páginas de autenticação, ecrãs vazios — sem depender de carregar um
// ficheiro externo.
export function Logo({ className = 'h-8 w-8' }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className} aria-hidden="true">
      <rect width="64" height="64" rx="14" fill="#1f4fed" />
      <path
        d="M20 32c0-6.6 5.4-12 12-12 4.6 0 8.6 2.6 10.6 6.4l-5.2 2.8c-1.1-2-3.1-3.2-5.4-3.2-3.6 0-6.5 2.9-6.5 6s2.9 6 6.5 6c2.4 0 4.4-1.3 5.5-3.4l5.2 2.9C40.6 41.4 36.6 44 32 44c-6.6 0-12-5.4-12-12z"
        fill="#ffffff"
      />
    </svg>
  );
}
