import type { ReactNode } from 'react';

type EvidenceItem = {
  label: string;
  value: ReactNode;
};

type EvidenceProps = {
  items: EvidenceItem[];
};

export function Evidence({ items }: EvidenceProps) {
  if (items.length === 0) return null;

  return (
    <dl>
      {items.map((item, index) => (
        <div key={`${index}-${item.label}`} className="flex justify-between gap-4 py-1.5">
          <dt className="shrink-0 text-sm text-ink-muted">{item.label}</dt>
          <dd className="min-w-0 text-right font-mono text-sm tabular-nums text-ink break-words">{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}
