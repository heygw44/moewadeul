import { useEffect, useState } from 'react';
import { serverNow } from '../../api/client';

export function useNow(active: boolean): number {
  const [now, setNow] = useState(() => serverNow());

  useEffect(() => {
    if (!active) return;
    const timer = window.setInterval(() => setNow(serverNow()), 1000);
    return () => window.clearInterval(timer);
  }, [active]);

  return now;
}
