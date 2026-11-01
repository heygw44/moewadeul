type UnderlineTabsProps<T extends string> = {
  label: string;
  options: ReadonlyArray<{ value: T; label: string }>;
  value: T;
  onChange: (value: T) => void;
};

export function UnderlineTabs<T extends string>({ label, options, value, onChange }: UnderlineTabsProps<T>) {
  return (
    <div
      role="group"
      aria-label={label}
      className="flex w-fit max-w-full gap-1 overflow-x-auto rounded-control bg-fill p-1"
    >
      {options.map((item) => (
        <button
          key={item.value}
          type="button"
          aria-pressed={value === item.value}
          onClick={() => onChange(item.value)}
          className={[
            'min-h-10 whitespace-nowrap rounded-control px-4 text-base font-semibold transition-colors',
            value === item.value ? 'bg-ink text-white' : 'text-ink-muted hover:text-ink',
          ].join(' ')}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}
