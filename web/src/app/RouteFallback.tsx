import { useDelayedFlag } from '../components/useDelayedFlag';

// 화면 코드가 도착하기까지 보여 준다. 금방 도착하면 깜빡이지 않도록 300ms 뒤에야 안내 글을 낸다.
export function RouteFallback() {
  const slow = useDelayedFlag(true);
  return (
    <div className="min-h-dvh bg-canvas px-4 py-10">
      {slow ? (
        <p role="status" className="text-ink-muted">
          화면을 불러오고 있어요
        </p>
      ) : null}
    </div>
  );
}
