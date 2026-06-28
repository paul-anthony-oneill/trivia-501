import { AnimatedScorePopup } from 'frontend-react';

// onComplete is a no-op so the popup stays visible for screenshotting
const noop = () => {};

const Wrap = ({ children }: { children: React.ReactNode }) => (
  <div data-theme="dark" style={{ position: 'relative', width: '400px', height: '300px', background: '#0f0f0f', overflow: 'hidden' }}>
    {children}
  </div>
);

export function ValidScore() {
  return (
    <Wrap>
      <AnimatedScorePopup scoreValue={47} result="VALID" onComplete={noop} />
    </Wrap>
  );
}

export function BustResult() {
  return (
    <Wrap>
      <AnimatedScorePopup scoreValue={163} result="BUST" reason="163 is not a valid darts finish" onComplete={noop} />
    </Wrap>
  );
}

export function InvalidAnswer() {
  return (
    <Wrap>
      <AnimatedScorePopup scoreValue={0} result="INVALID" reason="No player found matching that name" onComplete={noop} />
    </Wrap>
  );
}
