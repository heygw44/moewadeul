import type { ReactNode } from 'react';

type EmptyStateProps = {
  title: string;
  description?: string;
  action?: ReactNode;
  // 지도가 없는 화면의 본문 열에 둘 때 켠다. 흰 면에 담아서 목록 패널·머리 띠와 같은 가장자리에 맞춘다.
  framed?: boolean;
};

// 결과가 없을 때 다음에 할 일을 함께 안내한다. 머리 띠의 제목과 같은 쪽에 맞추려고 왼쪽 정렬한다.
export function EmptyState({ title, description, action, framed = false }: EmptyStateProps) {
  const body = (
    <div className="flex flex-col items-start gap-2 px-4 py-8">
      <p className="text-lg font-semibold text-ink">{title}</p>
      {description && <p className="text-sm text-ink-muted">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
  if (!framed) return body;
  return <div className="rounded-control border border-contour bg-card">{body}</div>;
}
