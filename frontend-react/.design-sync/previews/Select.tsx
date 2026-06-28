import { Select } from 'frontend-react';

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

const leagues = [
  { value: 'premier-league', label: 'Premier League' },
  { value: 'la-liga', label: 'La Liga' },
  { value: 'bundesliga', label: 'Bundesliga' },
  { value: 'serie-a', label: 'Serie A' },
];

export function Default() {
  return (
    <Wrap>
      <Select label="League" value="premier-league" onChange={() => {}} options={leagues} />
    </Wrap>
  );
}

export function Required() {
  return (
    <Wrap>
      <Select label="Difficulty" value="" onChange={() => {}} required options={[
        { value: 'easy', label: 'Easy' },
        { value: 'medium', label: 'Medium' },
        { value: 'hard', label: 'Hard' },
      ]} />
    </Wrap>
  );
}

export function WithError() {
  return (
    <Wrap>
      <Select label="Season" value="" onChange={() => {}} options={[{ value: '', label: 'Pick a season' }, { value: '2023', label: '2023/24' }]} error="Season is required" />
    </Wrap>
  );
}

export function Disabled() {
  return (
    <Wrap>
      <Select label="Category" value="football" onChange={() => {}} disabled options={[{ value: 'football', label: 'Football' }]} />
    </Wrap>
  );
}
