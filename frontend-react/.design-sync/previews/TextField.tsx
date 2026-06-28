import { TextField } from 'frontend-react';

// TextField uses legacy --color-* vars; define them so it renders visibly
const Wrap = ({ children }: { children: React.ReactNode }) => (
  <div
    data-theme="dark"
    style={{
      background: '#0f0f0f',
      padding: '24px',
      maxWidth: '360px',
      // map legacy vars to current theme tokens
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
  return (
    <Wrap>
      <TextField label="Season" value="" onChange={() => {}} placeholder="e.g. 2023/24" />
    </Wrap>
  );
}

export function WithValue() {
  return (
    <Wrap>
      <TextField label="Team" value="Arsenal" onChange={() => {}} />
    </Wrap>
  );
}

export function Required() {
  return (
    <Wrap>
      <TextField label="Player Name" value="" onChange={() => {}} required placeholder="Enter a name" />
    </Wrap>
  );
}

export function WithError() {
  return (
    <Wrap>
      <TextField label="Starting Score" value="163" onChange={() => {}} error="163 is an invalid darts score" />
    </Wrap>
  );
}

export function Disabled() {
  return (
    <Wrap>
      <TextField label="Category" value="Premier League" onChange={() => {}} disabled />
    </Wrap>
  );
}
