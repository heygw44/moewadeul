import { useDelayedFlag } from '../components/useDelayedFlag';

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
