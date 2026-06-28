import { ConfirmDialog } from 'frontend-react';

// ConfirmDialog uses native <dialog>.showModal() — rendered in top-layer
const noop = () => {};

export function DangerConfirm() {
  return (
    <div data-theme="dark" style={{ background: '#0f0f0f', width: '100vw', height: '400px', position: 'relative' }}>
      <ConfirmDialog
        open={true}
        title="Forfeit game?"
        message="Your current score and progress will be lost. This can't be undone."
        confirmText="Yes, forfeit"
        cancelText="Keep playing"
        type="danger"
        onConfirm={noop}
        onCancel={noop}
      />
    </div>
  );
}

export function InfoConfirm() {
  return (
    <div data-theme="dark" style={{ background: '#0f0f0f', width: '100vw', height: '400px', position: 'relative' }}>
      <ConfirmDialog
        open={true}
        title="Save question?"
        message="This will publish the question to the daily challenge pool."
        confirmText="Save"
        cancelText="Cancel"
        type="info"
        onConfirm={noop}
        onCancel={noop}
      />
    </div>
  );
}
