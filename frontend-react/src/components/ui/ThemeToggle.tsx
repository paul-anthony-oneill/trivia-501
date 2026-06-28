"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/context/AuthContext";

const STORAGE_KEY = "t501-theme";
type Theme = "dark" | "light";

/**
 * Dark/light mode switch. The initial theme is applied before paint by an
 * inline script in layout.tsx; this component just reflects and flips it.
 */
export default function ThemeToggle({ className = "" }: { className?: string }) {
  const [theme, setTheme] = useState<Theme | null>(null);
  const { user } = useAuth();

  useEffect(() => {
    setTheme((document.documentElement.dataset.theme as Theme) || "light");
  }, []);

  const toggle = () => {
    const next: Theme = theme === "dark" ? "light" : "dark";
    document.documentElement.dataset.theme = next;
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      /* private mode — theme just won't persist */
    }
    // ponytail: sync to user account for cross-device persistence
    if (user) {
      import("@/utils/supabase/client").then(({ createClient }) => {
        createClient().auth.updateUser({ data: { theme: next } }).catch(() => {});
      }).catch(() => {});
    }
    setTheme(next);
  };

  return (
    <button
      onClick={toggle}
      aria-label={theme === "dark" ? "Switch to light mode" : "Switch to dark mode"}
      title={theme === "dark" ? "Light mode" : "Dark mode"}
      className={`w-9 h-9 flex items-center justify-center rounded-full border border-line text-muted hover:text-ink hover:border-line-strong transition-colors ${className}`}
    >
      {/* Render both icons; CSS picks one — avoids a hydration flash */}
      <svg
        viewBox="0 0 24 24"
        width="16"
        height="16"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        className="theme-icon-sun"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="4.2" />
        <path d="M12 2.5v2.4M12 19.1v2.4M21.5 12h-2.4M4.9 12H2.5M18.7 5.3l-1.7 1.7M7 17l-1.7 1.7M18.7 18.7L17 17M7 7 5.3 5.3" />
      </svg>
      <svg
        viewBox="0 0 24 24"
        width="16"
        height="16"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="theme-icon-moon"
        aria-hidden="true"
      >
        <path d="M20.5 14.5A8.5 8.5 0 0 1 9.5 3.5a8.5 8.5 0 1 0 11 11Z" />
      </svg>
    </button>
  );
}
