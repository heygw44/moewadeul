type Segment<T extends string> = { value: T; label: string };

type AdminSegmentsProps<T extends string> = {
  label: string;
  segments: ReadonlyArray<Segment<T>>;
  value: T;
  onChange: (value: T) => void;
};

// 관리자 목록 위에서 보기 하나를 고르는 칸이다. 지도의 유형 칸처럼 고른 칸만 먹색으로 뒤집는다.
// 칸이 많아도 좁은 화면에서 가로 스크롤이 생기지 않게 줄을 바꿔 놓는다.
export function AdminSegments<T extends string>({ label, segments, value, onChange }: AdminSegmentsProps<T>) {
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-1.5">
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
            className={`inline-flex min-h-11 items-center justify-center rounded-control px-3 text-sm whitespace-nowrap sm:px-4 ${
              pressed
                ? 'border border-ink bg-ink font-bold text-white'
                : 'border border-line bg-surface text-ink hover:bg-fill'
            }`}
          >
            {segment.label}
          </button>
        );
      })}
    </div>
  );
}
