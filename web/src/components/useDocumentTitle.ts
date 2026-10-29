import { useEffect } from 'react';

const SITE_TITLE = '뫼와들';

type Options = { resetOnLeave?: boolean };

// title이 null이면 건드리지 않는다. 불러오는 중에는 앞 화면의 제목을 그대로 두려는 화면이 쓴다.
export function useDocumentTitle(title: string | null, { resetOnLeave = false }: Options = {}) {
  useEffect(() => {
    if (title === null) return;
    document.title = title;
    if (!resetOnLeave) return;
    return () => {
      document.title = SITE_TITLE;
    };
  }, [title, resetOnLeave]);
}
