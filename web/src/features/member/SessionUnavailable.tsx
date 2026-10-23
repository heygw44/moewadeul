import { BrandMark } from '../../components/BrandMark';
import { Button } from '../../components/Button';

// 로그인 상태를 확인하지 못했을 때 앱 전체 대신 보여 준다. 로그인 화면으로 보내지 않고, 사용자가 다시 시도할 수 있게 한다.
export function SessionUnavailable({ retrying, onRetry }: { retrying: boolean; onRetry(): void }) {
  return (
    <div className="flex min-h-dvh flex-col bg-canvas">
      <header className="px-4 py-4">
        <BrandMark className="text-xl text-ink" />
      </header>
      <main className="flex flex-1 items-center justify-center bg-canvas px-4 py-10">
        <section role="alert" className="w-full max-w-sm rounded-sheet bg-fill p-6">
          <h1 className="text-xl font-bold tracking-tight">로그인 상태를 확인하지 못했어요</h1>
          <p className="mt-2 text-ink-muted">
            인터넷 연결이 불안정하거나 서버가 잠시 응답하지 않아요. 연결을 확인한 뒤 다시 시도해 주세요.
          </p>
          <div className="mt-6">
            <Button loading={retrying} onClick={onRetry}>
              다시 시도
            </Button>
          </div>
        </section>
      </main>
    </div>
  );
}
