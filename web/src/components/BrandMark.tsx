type BrandMarkProps = {
  className?: string;
};

export function BrandMark({ className = '' }: BrandMarkProps) {
  return (
    <span
      className={[
        'inline-flex items-center gap-[0.35em] font-extrabold whitespace-nowrap tracking-[-0.03em]',
        className,
      ].join(' ')}
    >
      <svg aria-hidden="true" focusable="false" viewBox="0 0 26 26" className="size-[1.15em] shrink-0">
        <path d="M2 21 9.5 8l4.2 7.2L16.5 11 24 21Z" className="fill-brand" />
        <path d="M2 23h22" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" />
      </svg>
      뫼와들
    </span>
  );
}
