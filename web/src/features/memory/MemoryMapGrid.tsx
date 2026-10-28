import { useMemo, useState } from 'react';
import type { MemoryPinData } from '../../map/types';
import { memoryCellId, memoryCellLabel } from './memoryItems';
import type { MemoryCellItem } from './memoryItems';
import { MemoryGrid } from './MemoryGrid';
import { MemoryMap } from './MemoryMap';

export function MemoryMapGrid({ items }: { items: MemoryCellItem[] }) {
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const pins = useMemo<MemoryPinData[]>(
    () =>
      items.map((item) => ({
        id: item.id,
        position: item.position,
        thumbnailUrl: item.thumbnailUrl,
        label: memoryCellLabel(item),
      })),
    [items],
  );

  function handleSelect(id: number) {
    setSelectedId(id);
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    document
      .getElementById(memoryCellId(id))
      ?.scrollIntoView({ block: 'nearest', behavior: reduceMotion ? 'auto' : 'smooth' });
  }

  return (
    <div className="flex flex-col gap-4">
      <MemoryMap pins={pins} selectedId={selectedId} onSelect={handleSelect} />
      <MemoryGrid items={items} selectedId={selectedId} />
    </div>
  );
}
