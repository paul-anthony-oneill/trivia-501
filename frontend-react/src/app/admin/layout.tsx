import Sidebar from "@/components/admin/Sidebar";
import ErrorBoundary from "@/components/ErrorBoundary";

export const metadata = {
  title: "Admin — Trivia 501",
};

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    // Admin keeps its original dark palette regardless of the app theme —
    // --color-surface is re-pinned here because the global token now follows
    // the dark/light toggle.
    <div
      className="flex min-h-screen"
      style={{ "--color-surface": "#1a1a1a" } as React.CSSProperties}
    >
      <Sidebar />
      {/* Main content offset by sidebar width (set via CSS custom property) */}
      <ErrorBoundary section="admin page">
        <main
          className="flex-1 p-8 bg-[var(--color-background)] min-h-screen transition-all duration-300"
          style={{ marginLeft: "var(--sidebar-width, 250px)" }}
        >
          {children}
        </main>
      </ErrorBoundary>
    </div>
  );
}
