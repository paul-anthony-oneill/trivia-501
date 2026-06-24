"use client";

interface NavRowProps {
  /** Marks "surprise me" rows with a diamond glyph instead of the accent tick. */
  random?: boolean;
  name: string;
  sub?: string;
  onClick: () => void;
  hasChildren?: boolean;
  small?: boolean;
  disabled?: boolean;
  loading?: boolean;
}

export default function NavRow({
  random = false,
  name,
  sub,
  onClick,
  hasChildren = false,
  small = false,
  disabled = false,
  loading = false,
}: NavRowProps) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="group flex items-center gap-4 py-3.5 px-2 -mx-2 rounded-sm border-b border-line hover:bg-surface transition-colors text-left w-full disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent"
    >
      {random ? (
        <span className="text-accent font-display font-bold text-lg leading-none flex-shrink-0 w-4 text-center" aria-hidden="true">
          ✦
        </span>
      ) : (
        <span className="w-1 self-stretch rounded-full flex-shrink-0 bg-line group-hover:bg-accent transition-colors" aria-hidden="true" />
      )}

      <div className="flex-1 min-w-0">
        <div className={`font-display font-bold leading-tight ${small ? "text-base" : "text-xl"}`}>
          {name}
          {loading && <span className="ml-2 kicker">Starting…</span>}
        </div>
        {sub && <div className="hint mt-0.5 text-[10px]">{sub}</div>}
      </div>

      <span className="font-display font-bold text-base text-muted group-hover:text-accent group-hover:translate-x-0.5 transition-all flex-shrink-0" aria-hidden="true">
        {loading ? "…" : hasChildren ? "→" : "↵"}
      </span>
    </button>
  );
}
