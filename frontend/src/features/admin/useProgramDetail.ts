import { useCallback, useEffect, useState } from 'react';
import { getProgram } from '../../api/programs';
import type { ProgramDetail } from '../../api/types';

export type ProgramLoad =
  { status: 'loading' } | { status: 'error'; error: unknown } | { status: 'ready'; program: ProgramDetail };

export function useProgramDetail(programId: number): { load: ProgramLoad; reload: () => void } {
  const [load, setLoad] = useState<ProgramLoad>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getProgram(programId, controller.signal).then(
      (program) => {
        if (!controller.signal.aborted) setLoad({ status: 'ready', program });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setLoad({ status: 'error', error });
      },
    );
    return () => controller.abort();
  }, [programId, attempt]);

  const reload = useCallback(() => {
    setLoad({ status: 'loading' });
    setAttempt((value) => value + 1);
  }, []);

  return { load, reload };
}
