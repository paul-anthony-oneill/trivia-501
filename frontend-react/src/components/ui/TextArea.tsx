"use client";

interface TextAreaProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  required?: boolean;
  disabled?: boolean;
  rows?: number;
  error?: string;
}

export default function TextArea({
  label,
  value,
  onChange,
  placeholder = "",
  required = false,
  disabled = false,
  rows = 3,
  error = "",
}: TextAreaProps) {
  return (
    <div className="flex flex-col gap-1 mb-4">
      <label className="text-[var(--color-on-surface-variant)] text-[0.85rem] font-semibold tracking-[0.5px]">
        {label}
        {required && (
          <span className="text-[var(--color-error)] ml-1">*</span>
        )}
      </label>
      <div className="flex flex-col gap-1">
        <textarea
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          disabled={disabled}
          rows={rows}
          className={[
            "w-full box-border p-4 text-base resize-y font-inherit transition-all duration-200",
            "bg-[var(--color-surface)] text-[var(--color-on-surface)]",
            "border rounded-[var(--radius-sm)]",
            "focus:outline-none focus:border-2 focus:border-[var(--color-primary)] focus:shadow-[0_0_0_4px_rgba(74,222,128,0.1)]",
            "disabled:opacity-50 disabled:bg-[#252525]",
            error
              ? "border-[var(--color-error)]"
              : "border-[var(--color-outline)]",
          ].join(" ")}
        />
        {error && (
          <span className="text-[var(--color-error)] text-xs font-medium">
            {error}
          </span>
        )}
      </div>
    </div>
  );
}
