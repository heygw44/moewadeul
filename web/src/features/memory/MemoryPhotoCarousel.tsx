import { useRef, useState } from 'react';
import type { MemoryImage } from '../../api/types';
import { Icon } from '../../components/icons';

const ARROW_BUTTON =
  'absolute top-1/2 inline-flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-sheet border border-line bg-surface text-ink hover:bg-fill';

// 사진을 가로로 한 장씩 넘겨 보는 정사각 칸이다. 스크롤 위치(snap)로 넘기므로 손가락으로 밀어도 되고,
// 좌우 버튼과 키보드(칸에 포커스를 두고 좌우 화살표)로도 넘긴다. 사진이 한 장이면 버튼과 쪽수를 두지 않는다.
export function MemoryPhotoCarousel({
  images,
  owner,
  onImageError,
}: {
  images: MemoryImage[];
  owner: string;
  onImageError?: () => void;
}) {
  const scrollerRef = useRef<HTMLUListElement>(null);
  const [index, setIndex] = useState(0);
  const total = images.length;
  if (total === 0) return null;

  function handleScroll() {
    const scroller = scrollerRef.current;
    if (!scroller || scroller.clientWidth === 0) return;
    setIndex(Math.min(total - 1, Math.max(0, Math.round(scroller.scrollLeft / scroller.clientWidth))));
  }

  function goTo(target: number) {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    scroller.scrollTo({ left: target * scroller.clientWidth, behavior: reduceMotion ? 'auto' : 'smooth' });
  }

  return (
    <div className="relative">
      <ul
        ref={scrollerRef}
        onScroll={handleScroll}
        tabIndex={total > 1 ? 0 : undefined}
        aria-label={`${owner}님의 기록 사진 ${total}장`}
        className="flex snap-x snap-mandatory overflow-x-auto rounded-control border border-line bg-fill [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        onKeyDown={(event) => {
          if (total < 2) return;
          if (event.key === 'ArrowRight' && index < total - 1) {
            event.preventDefault();
            goTo(index + 1);
          } else if (event.key === 'ArrowLeft' && index > 0) {
            event.preventDefault();
            goTo(index - 1);
          }
        }}
      >
        {images.map((image, position) => (
          <li key={image.imageId} className="aspect-square w-full shrink-0 snap-center">
            <img
              src={image.url}
              alt={`${owner}님의 기록 사진 ${position + 1} / ${total}`}
              loading={position === 0 ? 'eager' : 'lazy'}
              onError={onImageError}
              className="h-full w-full object-cover"
            />
          </li>
        ))}
      </ul>
      {total > 1 && (
        <>
          {index > 0 && (
            <button type="button" aria-label="이전 사진" onClick={() => goTo(index - 1)} className={`${ARROW_BUTTON} left-2`}>
              <Icon name="chevronLeft" size={20} />
            </button>
          )}
          {index < total - 1 && (
            <button type="button" aria-label="다음 사진" onClick={() => goTo(index + 1)} className={`${ARROW_BUTTON} right-2`}>
              <Icon name="chevronRight" size={20} />
            </button>
          )}
          <p
            aria-live="polite"
            className="absolute bottom-2 right-2 rounded-sheet border border-line bg-surface px-2 py-0.5 font-mono text-xs tabular-nums text-ink"
          >
            <span className="sr-only">사진 </span>
            {index + 1}/{total}
          </p>
        </>
      )}
    </div>
  );
}
