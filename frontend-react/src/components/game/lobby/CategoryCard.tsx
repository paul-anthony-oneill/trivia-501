"use client";

interface CategoryCardProps {
  name: string;
  description: string;
  onClick: () => void;
  hasChildren?: boolean;
  disabled?: boolean;
  loading?: boolean;
}

export default function CategoryCard({
  name,
  description,
  onClick,
  hasChildren = false,
  disabled = false,
  loading = false,
}: CategoryCardProps) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="group flex flex-col bg-surface border border-line rounded-md p-5 text-left transition-all duration-200 hover:-translate-y-0.5 hover:border-line-strong hover:shadow-[var(--shadow-card)] disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0"
    >
      <span className="font-display font-bold text-lg leading-tight mb-1">
        {name}
        {loading && <span className="ml-2 kicker">Starting…</span>}
      </span>
      <span className="hint text-[10px] leading-snug">{description}</span>
      <span className="mt-3 font-display font-bold text-base text-muted group-hover:text-accent group-hover:translate-x-0.5 transition-all self-end" aria-hidden="true">
        {loading ? "…" : hasChildren ? "→ Browse" : "↵ Play"}
      </span>
    </button>
  );
}
