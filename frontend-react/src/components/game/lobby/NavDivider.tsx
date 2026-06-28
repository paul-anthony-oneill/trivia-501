"use client";

export default function NavDivider({ label }: { label: string }) {
  return (
    <div className="py-3 flex items-center gap-3">
      <div className="flex-1 border-t border-line" />
      <span className="kicker text-[9px]">{label}</span>
      <div className="flex-1 border-t border-line" />
    </div>
  );
}
