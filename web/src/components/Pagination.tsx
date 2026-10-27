import { Icon } from './icons';

type PaginationProps = {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  label?: string;
};

const WINDOW = 5;

function pageWindow(page: number, totalPages: number): number[] {
  const count = Math.min(WINDOW, totalPages);
  const start = Math.min(Math.max(1, page - Math.floor(WINDOW / 2)), totalPages - count + 1);
  return Array.from({ length: count }, (_, index) => start + index);
}

const BUTTON_CLASS =
  'inline-flex min-h-11 min-w-11 items-center justify-center rounded-control px-2 text-base tabular-nums disabled:cursor-not-allowed disabled:text-ink-subtle';

export function Pagination({ page, totalPages, onChange, label = '페이지' }: PaginationProps) {
  if (totalPages <= 1) return null;
  const current = Math.min(page, totalPages);
  return (
    <nav aria-label={label} className="flex flex-wrap items-center justify-center gap-0.5">
      <button
        type="button"
        aria-label="이전 페이지"
        disabled={current <= 1}
        onClick={() => onChange(current - 1)}
        className={`${BUTTON_CLASS} text-ink hover:bg-fill`}
      >
        <Icon name="chevronLeft" size={18} />
      </button>
      {pageWindow(current, totalPages).map((number) => (
        <button
          key={number}
          type="button"
          aria-current={number === page ? 'page' : undefined}
          aria-label={`${number}페이지`}
          onClick={() => onChange(number)}
          className={[
            BUTTON_CLASS,
            number === page ? 'bg-ink font-semibold text-white' : 'text-ink-muted hover:bg-fill hover:text-ink',
          ].join(' ')}
        >
          {number}
        </button>
      ))}
      <button
        type="button"
        aria-label="다음 페이지"
        disabled={current >= totalPages}
        onClick={() => onChange(current + 1)}
        className={`${BUTTON_CLASS} text-ink hover:bg-fill`}
      >
        <Icon name="chevronRight" size={18} />
      </button>
    </nav>
  );
}
