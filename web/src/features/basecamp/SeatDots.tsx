// 정원만큼 점을 찍고, 찬 자리는 먹색으로 칠한다. 숫자를 바로 옆에 적으므로 점은 스크린 리더에 숨긴다.
export function SeatDots({ headcount, capacity, size = 'sm' }: { headcount: number; capacity: number; size?: 'sm' | 'md' }) {
  const dot = size === 'md' ? 'size-[22px]' : 'size-2.5';
  return (
    <span aria-hidden="true" className="flex gap-1">
      {Array.from({ length: capacity }, (_, index) => (
        <i key={index} className={`${dot} rounded-full ${index < headcount ? 'bg-ink' : 'bg-fill-strong'}`} />
      ))}
    </span>
  );
}
