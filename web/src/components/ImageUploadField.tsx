import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { Button } from './Button';
import { Icon } from './icons';
import { Notice } from './Notice';
import type { ImageUploads } from './useImageUploads';

type ImageUploadFieldProps = {
  uploads: ImageUploads;
  // 한 화면에 업로드 칸이 둘이어도 라벨이 섞이지 않게 입력 id를 부르는 쪽이 정한다.
  inputId: string;
  error?: string;
  disabled: boolean;
  className?: string;
};

// 사진을 고르고, 올리는 상태를 보여 주고, 순서를 바꾸거나 빼는 칸이다. 상태는 useImageUploads가 가진다.
export function ImageUploadField({ uploads, inputId, error, disabled, className }: ImageUploadFieldProps) {
  const { images, messages, slotsLeft, max } = uploads;
  const full = slotsLeft <= 0;
  return (
    <fieldset className={['flex min-w-0 flex-col gap-3', className ?? ''].join(' ')}>
      <legend className="float-left mb-3 w-full text-sm font-medium text-ink">사진 (최대 {max}장)</legend>
      <p className="text-sm text-ink-muted">JPEG·PNG·WebP, 장당 5MB까지예요.</p>

      {messages.length > 0 && (
        <div role="alert">
          <Notice tone="warning" title="올리지 못한 파일이 있어요">
            <ul className="list-disc pl-5">
              {messages.map((message) => (
                <li key={message} className="break-words">
                  {message}
                </li>
              ))}
            </ul>
          </Notice>
        </div>
      )}

      <ul className={`clear-both grid grid-cols-3 gap-2 ${max > 3 ? 'sm:grid-cols-5' : ''}`}>
        {images.map((image, index) => (
          <li key={image.key} className="flex min-w-0 flex-col gap-1">
            <ImagePreview file={image.file} url={image.url} alt={`올린 이미지 ${index + 1}`} />
            <p className="text-xs text-ink-muted">
              {image.status === 'uploading' && '올리는 중이에요'}
              {image.status === 'done' && <span className="text-forest-deep">올렸어요</span>}
              {image.status === 'failed' && <span className="text-danger">올리지 못했어요</span>}
            </p>
            {image.status === 'failed' && image.error && (
              <p role="alert" className="break-words text-xs text-danger">
                {image.error}
              </p>
            )}
            {/* 사진 칸이 좁아서 앞으로 옮기기는 화살표만 두고, 이름은 aria-label로 준다. 짝이 없는 버튼은 한 줄을 다 쓴다. */}
            <div className="mt-1 grid grid-cols-[2.75rem_minmax(0,1fr)] gap-1">
              {image.status === 'failed' && (
                <Button
                  variant="secondary"
                  onClick={() => uploads.retry(image)}
                  disabled={disabled}
                  className="col-span-2 px-1 text-sm"
                >
                  다시 시도
                </Button>
              )}
              {index > 0 && (
                <Button
                  variant="secondary"
                  aria-label={`이미지 ${index + 1}번 앞으로 옮기기`}
                  onClick={() => uploads.moveForward(index)}
                  disabled={disabled}
                  title="앞으로"
                  className="px-1 text-sm"
                >
                  <Icon name="arrowUp" size={16} />
                </Button>
              )}
              <Button
                variant="danger-outline"
                aria-label={`이미지 ${index + 1}번 빼기`}
                className={['whitespace-nowrap px-1 text-sm', index > 0 ? '' : 'col-span-2'].join(' ')}
                onClick={() => uploads.remove(image.key)}
                disabled={disabled}
              >
                빼기
              </Button>
            </div>
          </li>
        ))}
        {!full && (
          <li className="relative flex min-w-0">
            <input
              id={inputId}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              disabled={disabled}
              onChange={uploads.handleFiles}
              className="peer sr-only"
            />
            <label
              htmlFor={inputId}
              className={[
                'flex aspect-square w-full flex-col items-center justify-center gap-1 rounded-control border border-dashed border-ink-subtle p-1 text-center text-sm peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-sea',
                disabled ? 'cursor-not-allowed bg-paper-deep text-ink-muted' : 'cursor-pointer bg-card text-ink hover:bg-paper-deep',
              ].join(' ')}
            >
              <Icon name="image" size={24} />
              <span>사진 추가</span>
              <span className="font-mono text-xs tabular-nums text-ink-muted">
                {images.length}/{max}
              </span>
            </label>
          </li>
        )}
      </ul>

      {error && (
        <p role="alert" className="flex items-start gap-1 text-sm text-danger">
          <Icon name="alert" size={16} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </p>
      )}
    </fieldset>
  );
}

// 올리는 파일은 브라우저 안에서만 쓰는 임시 주소로 미리 보여 준다. 임시 주소를 만들고 지우는 일을 같은 effect에 둬야
// 개발 모드에서 effect가 두 번 돌아도 주소가 새지 않는다. 주소는 상태를 거치지 않고 img 요소에 바로 넣는다.
function ImagePreview({ file, url, alt }: { file?: File; url?: string; alt: string }): ReactNode {
  const ref = useRef<HTMLImageElement>(null);

  useEffect(() => {
    const image = ref.current;
    if (!file || !image) return;
    const created = URL.createObjectURL(file);
    image.src = created;
    return () => URL.revokeObjectURL(created);
  }, [file]);

  const box = 'aspect-square w-full rounded-control border border-contour bg-paper-deep object-cover';
  return <img ref={ref} src={file ? undefined : url} alt={alt} className={box} />;
}
