import { DebugPanel } from 'frontend-react';
import { useRef, useEffect } from 'react';

// Shows the closed toggle button (bottom-right corner)
export function Closed() {
  return (
    <div data-theme="dark" style={{ background: '#0f0f0f', width: '400px', height: '200px', position: 'relative' }}>
      <DebugPanel gameId="preview-game-id" gameType="freeplay" />
    </div>
  );
}

// Auto-opens the debug panel; API call will fail → shows "Error:" state
function OpenedDebugPanel() {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const btn = ref.current?.querySelector('button');
    if (btn) btn.click();
  }, []);
  return <div ref={ref}><DebugPanel gameId="preview-game-id" gameType="freeplay" /></div>;
}

export function Open() {
  return (
    <div data-theme="dark" style={{ background: '#0f0f0f', width: '420px', height: '380px', position: 'relative' }}>
      <OpenedDebugPanel />
    </div>
  );
}
