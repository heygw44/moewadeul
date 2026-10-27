import { useState } from 'react';
import { useSession } from './session';

export function useViewerEpoch(): number {
  const session = useSession();
  const viewer: number | 'anonymous' | null =
    session.status === 'loading' ? null : (session.me?.memberId ?? 'anonymous');
  const [state, setState] = useState<{ viewer: number | 'anonymous' | null; epoch: number }>({ viewer, epoch: 0 });

  if (viewer !== null && viewer !== state.viewer) {
    setState({ viewer, epoch: state.viewer === null ? state.epoch : state.epoch + 1 });
  }
  return state.epoch;
}
