import { useEffect, useRef, useState } from 'react';

type FormDraft = { path: string; memberId: number; values: unknown };

let kept: FormDraft | null = null;
let capture: (() => FormDraft) | null = null;

export function keepFormDraft(): void {
  if (capture) kept = capture();
}

export function forgetFormDraft(): void {
  kept = null;
}

export function useRestoredDraft<T>(path: string, memberId: number | undefined): T | null {
  const [draft] = useState<T | null>(() =>
    kept !== null && memberId !== undefined && kept.path === path && kept.memberId === memberId
      ? (kept.values as T)
      : null,
  );
  useEffect(() => {
    forgetFormDraft();
  }, []);
  return draft;
}

export function useDraftCapture<T>(path: string, memberId: number | undefined, values: T): void {
  const latest = useRef(values);
  useEffect(() => {
    latest.current = values;
  });

  useEffect(() => {
    if (memberId === undefined) return;
    const current = () => ({ path, memberId, values: latest.current });
    capture = current;
    return () => {
      if (capture === current) capture = null;
    };
  }, [path, memberId]);
}
