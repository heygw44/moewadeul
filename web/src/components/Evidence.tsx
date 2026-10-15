import type { ReactNode } from 'react';

type EvidenceItem = {
  label: string;
  value: ReactNode;
};

type EvidenceProps = {
  items: EvidenceItem[];
};

// 판단 근거(출처, 기준일, 좌표 등)를 라벨·값 두 칸으로 적는다. 선 없이 간격으로 나누고, 값은 숫자 폭을 맞춰 오른쪽에 둔다.
export function Evidence({ items }: EvidenceProps) {
  if (items.length === 0) return null;

  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-5 gap-y-2.5 text-sm">
      {items.map((item, index) => (
        <div key={`${index}-${item.label}`} className="contents">
          <dt className="text-ink-muted">{item.label}</dt>
          <dd className="min-w-0 text-right tabular-nums text-ink break-words">{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}
