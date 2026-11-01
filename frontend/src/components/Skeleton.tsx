type SkeletonProps = {
  className?: string;
};

export function Skeleton({ className }: SkeletonProps) {
  return (
    <div
      aria-hidden="true"
      className={['animate-pulse rounded-row bg-fill-strong motion-reduce:animate-none', className ?? '']
        .filter(Boolean)
        .join(' ')}
    />
  );
}
