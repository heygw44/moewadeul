type UnderlineTabsProps<T extends string> = {
  label: string;
  options: ReadonlyArray<{ value: T; label: string }>;
  value: T;
  onChange: (value: T) => void;
};

// 머리글 띠 아래에 놓는 분류 탭이다. 고른 탭은 aria-pressed로 알리고, 좁은 화면에서는 띠 안에서만 가로로 밀린다.
// 탭 안쪽 여백(px-3)만큼 줄을 왼쪽으로 당겨서 첫 탭 글자가 제목과 같은 가장자리에서 시작하게 한다.
export function UnderlineTabs<T extends string>({ label, options, value, onChange }: UnderlineTabsProps<T>) {
  return (
    <div role="group" aria-label={label} className="-mx-3 flex gap-1 overflow-x-auto">
      {options.map((item) => (
        <button
          key={item.value}
          type="button"
          aria-pressed={value === item.value}
          onClick={() => onChange(item.value)}
          className={[
            'min-h-11 whitespace-nowrap border-b-2 px-3 text-base',
            value === item.value
              ? 'border-ink font-semibold text-ink'
              : 'border-transparent text-ink-muted hover:text-ink',
          ].join(' ')}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}
