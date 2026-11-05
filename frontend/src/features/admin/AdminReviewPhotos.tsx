import { useState } from 'react';
import type { AdminCommunityImage } from '../../api/types';
import { Button } from '../../components/Button';

interface AdminReviewPhotosProps {
  images: ReadonlyArray<AdminCommunityImage>;
  onReload: () => void;
}

export function AdminReviewPhotos({ images, onReload }: AdminReviewPhotosProps) {
  const [broken, setBroken] = useState<ReadonlySet<number>>(new Set());
  const [attempt, setAttempt] = useState(0);
  if (images.length === 0) return null;

  function mark(imageId: number, isBroken: boolean) {
    setBroken((previous) => {
      if (previous.has(imageId) === isBroken) return previous;
      const next = new Set(previous);
      if (isBroken) next.add(imageId);
      else next.delete(imageId);
      return next;
    });
  }

  function reload() {
    setBroken(new Set());
    setAttempt((value) => value + 1);
    onReload();
  }

  return (
    <div className="flex flex-col gap-2">
      <ul className="grid grid-cols-3 gap-1 sm:grid-cols-5" aria-label={`사진 ${images.length}장`}>
        {images.map((image, index) => (
          <li key={image.imageId} className="aspect-square overflow-hidden rounded-row bg-fill-strong">
            <a
              href={image.url}
              target="_blank"
              rel="noreferrer"
              aria-label={`사진 ${index + 1} 크게 보기`}
              className="block h-full w-full"
            >
              <img
                key={attempt}
                src={image.url}
                alt={`사진 ${index + 1}`}
                loading="lazy"
                onLoad={() => mark(image.imageId, false)}
                onError={() => mark(image.imageId, true)}
                className="h-full w-full object-cover"
              />
            </a>
          </li>
        ))}
      </ul>
      {broken.size > 0 && (
        <div role="alert" className="flex flex-wrap items-center gap-3 text-sm text-ink">
          <p className="min-w-0 flex-1">
            사진 {broken.size}장을 불러오지 못했어요. 화면을 오래 열어 두면 사진 주소가 만료돼요.
          </p>
          <Button variant="secondary" onClick={reload}>
            다시 불러오기
          </Button>
        </div>
      )}
    </div>
  );
}
