"use client";

interface SelectOption {
  value: string | boolean | number;
  label: string;
}

interface SelectProps {
  label: string;
  value: string | boolean | number;
  onChange: (value: string) => void;
  options: SelectOption[];
  required?: boolean;
  disabled?: boolean;
  error?: string;
}

export default function Select({
  label,
  value,
  onChange,
  options,
  required = false,
  disabled = false,
  error = "",
}: SelectProps) {
  return (
    <div className="flex flex-col gap-1 mb-4">
      <label className="text-[var(--color-on-surface-variant)] text-[0.85rem] font-semibold tracking-[0.5px]">
        {label}
        {required && (
          <span className="text-[var(--color-error)] ml-1">*</span>
        )}
      </label>
      <div className="flex flex-col gap-1">
        <select
          value={String(value)}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          className={[
            "w-full box-border p-4 text-base cursor-pointer transition-all duration-200",
            "bg-[var(--color-surface)] text-[var(--color-on-surface)]",
            "border rounded-[var(--radius-sm)]",
            "focus:outline-none focus:border-2 focus:border-[var(--color-primary)] focus:shadow-[0_0_0_4px_rgba(74,222,128,0.1)]",
            "disabled:opacity-50 disabled:bg-[#252525]",
            error
              ? "border-[var(--color-error)]"
              : "border-[var(--color-outline)]",
          ].join(" ")}
        >
          {options.map((opt) => (
            <option key={String(opt.value)} value={String(opt.value)}>
              {opt.label}
            </option>
          ))}
        </select>
        {error && (
          <span className="text-[var(--color-error)] text-xs font-medium">
            {error}
          </span>
        )}
      </div>
    </div>
  );
}
