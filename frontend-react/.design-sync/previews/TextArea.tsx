import { TextArea } from 'frontend-react';

const Wrap = ({ children }: { children: React.ReactNode }) => (
  <div
    data-theme="dark"
    style={{
      background: '#0f0f0f',
      padding: '24px',
      maxWidth: '360px',
      '--color-surface': '#1a1a1a',
      '--color-on-surface': '#f0f0f0',
      '--color-on-surface-variant': '#a0a0a0',
      '--color-primary': '#4ade80',
      '--color-outline': '#333333',
      '--color-error': '#ef4444',
      '--radius-sm': '8px',
    } as React.CSSProperties}
  >
    {children}
  </div>
);

export function Default() {
  return <Wrap><TextArea label="Description" value="" onChange={() => {}} placeholder="Enter description…" /></Wrap>;
}

export function WithValue() {
  return (
    <Wrap>
      <TextArea label="Notes" value={"Great question for mid-season.\nPlenty of valid answers in the 40–80 range."} onChange={() => {}} rows={4} />
    </Wrap>
  );
}

export function WithError() {
  return <Wrap><TextArea label="Reason" value="too short" onChange={() => {}} error="Must be at least 20 characters" /></Wrap>;
}

export function Disabled() {
  return <Wrap><TextArea label="Read-only notes" value="Admin-locked content" onChange={() => {}} disabled /></Wrap>;
}
