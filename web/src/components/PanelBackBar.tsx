import { Icon } from './icons';

type PanelBackBarProps = { onBack: () => void } & (
  { label: string; ariaLabel?: never } | { ariaLabel: string; label?: never }
);

export function PanelBackBar({ onBack, label, ariaLabel }: PanelBackBarProps) {
  return (
    <div className="sticky top-0 z-10 flex items-center bg-surface px-2 lg:px-4">
      {label === undefined ? (
        <button
          type="button"
          aria-label={ariaLabel}
          onClick={onBack}
          className="flex size-11 items-center justify-center rounded-control text-ink hover:bg-fill"
        >
          <Icon name="chevronLeft" size={24} />
        </button>
      ) : (
        <button
          type="button"
          onClick={onBack}
          className="inline-flex min-h-11 items-center gap-1 rounded-control pr-3 pl-1 text-sm font-semibold text-ink hover:bg-fill"
        >
          <Icon name="chevronLeft" size={22} />
          {label}
        </button>
      )}
    </div>
  );
}
