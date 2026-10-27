import { useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { toUserMessage } from '../api/errors';
import type { ImageUpload, ImageUploadRequest } from '../api/types';
import { putImage } from '../api/uploads';
import { createThumbnail } from './createThumbnail';
import { reencodeImage } from './reencodeImage';

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const IMAGE_TYPES: readonly string[] = ['image/jpeg', 'image/png', 'image/webp'];

const MAX_CONCURRENT_UPLOADS = 3;

const REENCODE_FAILED = '이 사진은 올릴 수 없어요. 빼고 다른 사진을 골라 주세요.';

export type UploadImageItem = {
  key: string;
  imageId?: number;
  url?: string;
  file?: File;
  status: 'uploading' | 'done' | 'failed';
  error?: string;
};

export function restorableImages(images: UploadImageItem[]): UploadImageItem[] {
  return images.filter((image) => image.status === 'done' || (image.status === 'failed' && image.file !== undefined));
}

let nextKey = 0;

type UseImageUploadsOptions = {
  max: number;
  initial?: { imageId: number; url: string }[];
  restored?: UploadImageItem[];
  requestUpload: (req: ImageUploadRequest) => Promise<ImageUpload>;
  thumbnail?: boolean;
};

export type ImageUploads = {
  images: UploadImageItem[];
  messages: string[];
  uploading: boolean;
  failed: boolean;
  slotsLeft: number;
  max: number;
  imageIds: number[];
  handleFiles: (event: ChangeEvent<HTMLInputElement>) => void;
  retry: (image: UploadImageItem) => void;
  moveForward: (index: number) => void;
  remove: (key: string) => void;
};

export function useImageUploads({
  max,
  initial = [],
  restored,
  requestUpload,
  thumbnail = false,
}: UseImageUploadsOptions): ImageUploads {
  const [images, setImages] = useState<UploadImageItem[]>(
    () =>
      restored ??
      initial.map((image) => ({
        key: `saved-${image.imageId}`,
        imageId: image.imageId,
        url: image.url,
        status: 'done' as const,
      })),
  );
  const [messages, setMessages] = useState<string[]>([]);

  const slotsLeft = max - images.length;

  function patchImage(key: string, patch: Partial<UploadImageItem>) {
    setImages((previous) => previous.map((image) => (image.key === key ? { ...image, ...patch } : image)));
  }

  const waiting = useRef<{ key: string; file: File }[]>([]);
  const running = useRef(0);

  function drain() {
    while (running.current < MAX_CONCURRENT_UPLOADS) {
      const next = waiting.current.shift();
      if (!next) return;
      running.current += 1;
      void upload(next.key, next.file).finally(() => {
        running.current -= 1;
        drain();
      });
    }
  }

  function enqueue(key: string, file: File) {
    patchImage(key, { status: 'uploading', error: undefined });
    waiting.current.push({ key, file });
    drain();
  }

  async function upload(key: string, file: File) {
    try {
      const image = await reencodeImage(file, MAX_IMAGE_BYTES);
      if (!image) {
        patchImage(key, { status: 'failed', error: REENCODE_FAILED });
        return;
      }
      const thumbnailBlob = thumbnail ? await createThumbnail(file) : null;
      const target = await requestUpload({
        contentType: image.type,
        sizeBytes: image.size,
        ...(thumbnailBlob ? { thumbnailSizeBytes: thumbnailBlob.size } : {}),
      });
      if (thumbnailBlob && target.thumbnailUpload) await putImage(target.thumbnailUpload, thumbnailBlob);
      await putImage(target, image);
      patchImage(key, { status: 'done', imageId: target.imageId });
    } catch (error) {
      patchImage(key, { status: 'failed', error: toUserMessage(error) });
    }
  }

  function handleFiles(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []);
    event.target.value = '';
    const rejected: string[] = [];
    const accepted: UploadImageItem[] = [];
    let room = slotsLeft;
    for (const file of files) {
      if (!IMAGE_TYPES.includes(file.type)) {
        rejected.push(`${file.name}: JPEG, PNG, WebP 파일만 올릴 수 있어요. 다른 파일을 골라 주세요.`);
      } else if (file.size > MAX_IMAGE_BYTES) {
        rejected.push(`${file.name}: 5MB 이하 파일만 올릴 수 있어요. 더 작은 파일을 골라 주세요.`);
      } else if (file.size === 0) {
        rejected.push(`${file.name}: 비어 있는 파일이에요. 다른 파일을 골라 주세요.`);
      } else if (room <= 0) {
        rejected.push(`${file.name}: 사진은 ${max}장까지 올릴 수 있어요. 다른 사진을 빼고 올려 주세요.`);
      } else {
        room -= 1;
        accepted.push({ key: `new-${nextKey++}`, file, status: 'uploading' });
      }
    }
    setMessages(rejected);
    if (accepted.length === 0) return;
    setImages((previous) => [...previous, ...accepted]);
    for (const item of accepted) {
      if (item.file) enqueue(item.key, item.file);
    }
  }

  function moveForward(index: number) {
    setImages((previous) => {
      if (index <= 0 || index >= previous.length) return previous;
      const next = [...previous];
      const moved = next[index];
      const before = next[index - 1];
      if (!moved || !before) return previous;
      next[index - 1] = moved;
      next[index] = before;
      return next;
    });
  }

  function remove(key: string) {
    waiting.current = waiting.current.filter((item) => item.key !== key);
    setImages((previous) => previous.filter((image) => image.key !== key));
    setMessages([]);
  }

  return {
    images,
    messages,
    uploading: images.some((image) => image.status === 'uploading'),
    failed: images.some((image) => image.status === 'failed'),
    slotsLeft,
    max,
    imageIds: images.flatMap((image) => (image.imageId === undefined ? [] : [image.imageId])),
    handleFiles,
    retry: (image) => {
      if (image.file) enqueue(image.key, image.file);
    },
    moveForward,
    remove,
  };
}
