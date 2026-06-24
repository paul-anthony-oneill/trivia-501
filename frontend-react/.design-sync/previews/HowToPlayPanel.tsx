import { HowToPlayPanel } from 'frontend-react';
import { useRef, useEffect } from 'react';

const Wrap = ({ children }: { children: React.ReactNode }) => (
  <div data-theme="dark" style={{ background: 'var(--bg)', padding: '16px', maxWidth: '480px' }}>
    {children}
  </div>
);

// Auto-click the toggle button so the panel is open in the screenshot
function OpenedPanel() {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const btn = ref.current?.querySelector('button');
    if (btn) btn.click();
  }, []);
  return <div ref={ref}><HowToPlayPanel /></div>;
}

export function Expanded() {
  return <Wrap><OpenedPanel /></Wrap>;
}

export function Collapsed() {
  return <Wrap><HowToPlayPanel /></Wrap>;
}
