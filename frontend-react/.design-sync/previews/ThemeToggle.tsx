import { ThemeToggle } from 'frontend-react';

export function DarkMode() {
  return (
    <div data-theme="dark" style={{ background: '#0f0f0f', padding: '24px', display: 'flex', gap: '12px', alignItems: 'center' }}>
      <ThemeToggle />
      <span style={{ color: '#a0a0a0', fontSize: '13px' }}>Dark mode active</span>
    </div>
  );
}

export function LightMode() {
  return (
    <div data-theme="light" style={{ background: '#f5f5f5', padding: '24px', display: 'flex', gap: '12px', alignItems: 'center' }}>
      <ThemeToggle />
      <span style={{ color: '#555', fontSize: '13px' }}>Light mode active</span>
    </div>
  );
}
