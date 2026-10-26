import { Component } from 'react';
import type { ReactNode } from 'react';
import { buttonClassName } from '../components/buttonClass';

type RouteBoundaryProps = {
  // 주소가 바뀌면 오류를 지우고 새 화면을 다시 그린다.
  resetKey: string;
  children: ReactNode;
};

type RouteBoundaryState = {
  failed: boolean;
  resetKey: string;
};

// 화면 코드를 받지 못했을 때(배포로 파일이 바뀐 뒤 옛 탭이 새 화면으로 옮겨 가는 경우 등) 빈 화면 대신 다시 불러오라고 안내한다.
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
