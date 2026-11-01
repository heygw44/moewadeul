type Segment<T extends string> = { value: T; label: string };

type AdminSegmentsProps<T extends string> = {
  label: string;
  segments: ReadonlyArray<Segment<T>>;
  value: T;
  onChange: (value: T) => void;
};

export function AdminSegments<T extends string>({ label, segments, value, onChange }: AdminSegmentsProps<T>) {
  return (
    <div role="group" aria-label={label} className="flex w-fit max-w-full flex-wrap gap-1 rounded-sheet bg-fill p-1">
      {segments.map((segment) => {
        const pressed = segment.value === value;
        return (
          <button
            key={segment.value}
            type="button"
            aria-pressed={pressed}
            onClick={() => {
              if (!pressed) onChange(segment.value);
            }}
            className={`inline-flex min-h-10 items-center justify-center rounded-control px-3 text-sm font-semibold whitespace-nowrap sm:px-4 ${
              pressed ? 'bg-ink text-white' : 'text-ink-muted hover:bg-fill-strong hover:text-ink'
            }`}
          >
            {segment.label}
          </button>
        );
      })}
    </div>
  );
}
