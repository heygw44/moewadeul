type UnderlineTabsProps<T extends string> = {
  label: string;
  options: ReadonlyArray<{ value: T; label: string }>;
  value: T;
  onChange: (value: T) => void;
};

// 제목 아래에 놓는 분류 탭이다. 세이지 알약 트랙 안에서 고른 탭만 먹색 알약으로 뒤집고, aria-pressed로 알린다.
// 좁은 화면에서는 트랙 안에서만 가로로 밀린다. 이름은 예전 밑줄 모양에서 왔고, 부르는 곳이 많아 그대로 둔다.
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
