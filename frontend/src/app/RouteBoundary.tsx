import { Component } from 'react';
import type { ReactNode } from 'react';
import { buttonClassName } from '../components/buttonClass';

type RouteBoundaryProps = {
  resetKey: string;
  children: ReactNode;
};

type RouteBoundaryState = {
  failed: boolean;
  resetKey: string;
};

export class RouteBoundary extends Component<RouteBoundaryProps, RouteBoundaryState> {
  state: RouteBoundaryState = { failed: false, resetKey: this.props.resetKey };

  static getDerivedStateFromError(): Partial<RouteBoundaryState> {
    return { failed: true };
  }

  static getDerivedStateFromProps(props: RouteBoundaryProps, state: RouteBoundaryState): RouteBoundaryState | null {
    return props.resetKey === state.resetKey ? null : { failed: false, resetKey: props.resetKey };
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="flex min-h-dvh items-center justify-center bg-canvas px-4 py-10">
        <section className="w-full max-w-sm rounded-sheet bg-fill p-6">
          <h1 className="text-xl font-bold tracking-tight">화면을 불러오지 못했어요</h1>
          <p className="mt-2 text-ink-muted">새 버전이 나왔거나 연결이 끊겼을 수 있어요. 새로고침해 주세요.</p>
          <button
            type="button"
            className={`mt-6 ${buttonClassName('primary')}`}
            onClick={() => window.location.reload()}
          >
            새로고침
          </button>
        </section>
      </div>
    );
  }
}
