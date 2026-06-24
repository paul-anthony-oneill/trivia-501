import { FormModal, TextField } from 'frontend-react';

const noop = () => {};

export function WithForm() {
  return (
    <div data-theme="dark" style={{ background: '#0f0f0f', width: '100vw', height: '500px' }}>
      <FormModal open={true} title="Edit Question" onSave={noop} onCancel={noop}>
        <div style={{
          '--color-surface': '#1a1a1a',
          '--color-on-surface': '#f0f0f0',
          '--color-on-surface-variant': '#a0a0a0',
          '--color-primary': '#4ade80',
          '--color-outline': '#333333',
          '--color-error': '#ef4444',
          '--radius-sm': '8px',
        } as React.CSSProperties}>
          <TextField label="Question Title" value="Goals for Arsenal in Premier League" onChange={noop} />
          <TextField label="Season" value="2022/23" onChange={noop} />
        </div>
      </FormModal>
    </div>
  );
}

export function Loading() {
  return (
    <div data-theme="dark" style={{ background: '#0f0f0f', width: '100vw', height: '400px' }}>
      <FormModal open={true} title="Saving…" onSave={noop} onCancel={noop} loading={true}>
        <p style={{ color: '#a0a0a0', fontSize: '14px' }}>Processing your changes…</p>
      </FormModal>
    </div>
  );
}
