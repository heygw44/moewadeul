import { useId, useState } from 'react';
import { Icon } from '../../components/icons';
import { readStored, writeStored } from '../../lib/storage';
import { DESKTOP_QUERY } from './useIsDesktop';

const OPEN_KEY = 'moewadeul.mapLegendOpen';
const LEGEND_ITEMS: ReadonlyArray<{ key: string; label: string; swatch: string }> = [
  { key: 'public', label: '야영장·휴양림', swatch: 'bg-brand' },
  { key: 'bakji', label: '박지', swatch: 'bg-bakji' },
  { key: 'warning', label: '공원 경계 경고', swatch: 'bg-surface ring-[2.5px] ring-inset ring-warning-line' },
];

function initialOpen(): boolean {
  const stored = readStored(OPEN_KEY);
  if (stored !== null) return stored === '1';
  return window.matchMedia(DESKTOP_QUERY).matches;
}

export function MapLegend({ hiddenOnMobile = false }: { hiddenOnMobile?: boolean }) {
  const [open, setOpen] = useState(initialOpen);
  const listId = useId();

  function toggle() {
    const next = !open;
    writeStored(OPEN_KEY, next ? '1' : '0');
    setOpen(next);
  }

  return (
    <div
      className={`pointer-events-auto absolute bottom-[calc(var(--sheet-top)+4.75rem)] left-4 z-10 flex-col overflow-hidden rounded-[1.125rem] bg-surface text-ink shadow-raise lg:bottom-[3.75rem] lg:flex lg:flex-row lg:items-center lg:rounded-control ${hiddenOnMobile ? 'hidden' : 'flex'}`}
    >
      <button
        type="button"
        onClick={toggle}
        aria-expanded={open}
        aria-controls={listId}
        className="inline-flex min-h-11 items-center gap-1.5 px-4 text-sm font-semibold hover:bg-fill focus-visible:-outline-offset-2"
      >
        범례
        <Icon name={open ? 'chevronLeft' : 'chevronRight'} size={16} className="shrink-0 text-ink-muted" />
      </button>
      {open && (
        <ul id={listId} className="flex flex-col gap-1.5 px-4 pb-3 lg:flex-row lg:gap-4 lg:py-0 lg:pr-5 lg:pl-1">
          {LEGEND_ITEMS.map((item) => (
            <li key={item.key} className="flex items-center gap-2 text-sm">
              <span aria-hidden="true" className={`block size-3 shrink-0 rounded-full ${item.swatch}`} />
              {item.label}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
