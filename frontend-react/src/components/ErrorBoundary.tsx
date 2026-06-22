"use client";

import { Component, type ReactNode } from "react";

interface Props {
  children: ReactNode;
  /** Label shown in the error UI so the user knows which section broke. */
  section?: string;
  /** Called when the user clicks "Reload" — defaults to window.location.reload. */
  onRetry?: () => void;
}

interface State {
  error: Error | null;
}

/**
 * Catches render errors in its subtree and shows a recovery UI instead of a
 * white screen. Wrap each major section (lobby, game, admin) in its own
 * boundary so one broken section doesn't take down the whole app.
 */
export default class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error(
      `ErrorBoundary${this.props.section ? ` [${this.props.section}]` : ""}:`,
      error,
      info.componentStack,
    );
  }

  render() {
    if (this.state.error) {
      return (
        <div className="min-h-[50vh] flex items-center justify-center bg-bg px-6">
          <div className="text-center max-w-md">
            <div className="text-danger text-lg font-semibold mb-2">
              Something went wrong
            </div>
            {this.props.section && (
              <p className="text-muted text-sm mb-4">
                The {this.props.section} section encountered an error. Try again in a moment.
              </p>
            )}
            <button
              onClick={() => {
                this.setState({ error: null });
                (this.props.onRetry ?? (() => window.location.reload()))();
              }}
              className="btn-ghost px-6 py-2"
            >
              Reload this section
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
