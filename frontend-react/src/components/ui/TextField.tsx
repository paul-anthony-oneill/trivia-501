"use client";

interface TextFieldProps {
  label: string;
  value: string | number;
  onChange: (value: string) => void;
  type?: string;
  placeholder?: string;
  required?: boolean;
  disabled?: boolean;
  error?: string;
}

export default function TextField({
  label,
  value,
  onChange,
  type = "text",
  placeholder = "",
  required = false,
  disabled = false,
  error = "",
}: TextFieldProps) {
  return (
    <div className="flex flex-col gap-1 mb-4">
      <label className="text-[var(--color-on-surface-variant)] text-[0.85rem] font-semibold tracking-[0.5px]">
        {label}
        {required && (
          <span className="text-[var(--color-error)] ml-1">*</span>
        )}
      </label>
      <div className="flex flex-col gap-1">
        <input
          type={type}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          disabled={disabled}
          className={[
            "w-full box-border p-4 text-base transition-all duration-200",
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
