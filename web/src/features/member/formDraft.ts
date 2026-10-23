import { useEffect, useRef, useState } from 'react';

// 세션이 끊겨(401) 로그인 화면으로 넘어가면 쓰던 폼이 사라진다. 그래서 넘어가기 직전의 폼 값을 화면 밖 메모리에 두고,
// 로그인한 뒤 같은 경로로 돌아오면 폼이 이 값으로 다시 채운다.
// 브라우저 저장소에는 넣지 않는다. 글과 사진에 개인정보가 들어 있을 수 있어서, 새로 고치거나 탭을 닫으면 함께 사라져야 한다.
type FormDraft = { path: string; memberId: number; values: unknown };

let kept: FormDraft | null = null;
// 지금 그려진 폼이 최신 값을 꺼내는 함수다. 한 화면에는 폼이 하나만 있다.
let capture: (() => FormDraft) | null = null;

// 세션이 끊겨 로그인 화면으로 보내기 직전에 부른다. 폼이 없으면 아무것도 남기지 않는다.
export function keepFormDraft(): void {
  if (capture) kept = capture();
}

// 로그아웃하면 지운다. 같은 탭에서 다음에 로그인한 사람에게 앞사람의 글이 보이지 않게 하기 위해서다.
export function forgetFormDraft(): void {
  kept = null;
}

// 폼이 처음 그려질 때 같은 회원이 같은 경로에 남긴 초안을 돌려준다. 없으면 null이다.
// 한 번 읽으면 지운다. 초안을 채운 뒤 다시 세션이 끊기면 그때 새로 남기고, 다른 회원이 로그인했다면 앞사람의 글은 버린다.
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

// 폼이 그려져 있는 동안 지금 값을 보관 대상으로 등록한다. 값은 렌더마다 ref에 갱신해 두고, 세션이 끊길 때만 읽는다.
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
