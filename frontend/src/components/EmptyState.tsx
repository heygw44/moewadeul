import type { ReactNode } from 'react';

type EmptyStateProps = {
  title: string;
  description?: string;
  action?: ReactNode;
  framed?: boolean;
};

export function EmptyState({ title, description, action, framed = false }: EmptyStateProps) {
  const body = (
    <div className="flex flex-col items-start gap-2 px-6 py-10">
      <p className="text-lg font-semibold text-ink">{title}</p>
      {description && <p className="text-sm text-ink-muted">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
  if (!framed) return body;
  return <div className="rounded-sheet bg-fill">{body}</div>;
}
