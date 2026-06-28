import { EntitySearch } from 'frontend-react';

const noop = () => {};

const inputStyle: React.CSSProperties = {
  background: '#1a1a1a',
  border: '1px solid #333',
  borderRadius: '8px',
  color: '#f0f0f0',
  padding: '12px 16px',
  fontSize: '16px',
  width: '100%',
  boxSizing: 'border-box',
};

export function Empty() {
  return (
    <div data-theme="dark" style={{ background: '#0f0f0f', padding: '24px', maxWidth: '360px' }}>
      <p style={{ color: '#a0a0a0', fontSize: '11px', fontFamily: 'monospace', marginBottom: '8px', textTransform: 'uppercase', letterSpacing: '1px' }}>Footballer search</p>
      <EntitySearch entityType="footballer" onSelect={noop} placeholder="Type a player name…" className={`w-full`} />
    </div>
  );
}

export function Disabled() {
  return (
    <div data-theme="dark" style={{ background: '#0f0f0f', padding: '24px', maxWidth: '360px' }}>
      <p style={{ color: '#a0a0a0', fontSize: '11px', fontFamily: 'monospace', marginBottom: '8px', textTransform: 'uppercase', letterSpacing: '1px' }}>Disabled (game over)</p>
      <EntitySearch entityType="footballer" onSelect={noop} placeholder="Game over" disabled className={`w-full`} />
    </div>
  );
}
