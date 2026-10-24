import { useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { toUserMessage } from '../api/errors';
import type { ImageUpload, ImageUploadRequest } from '../api/types';
import { putImage } from '../api/uploads';
import { createThumbnail } from './createThumbnail';
import { reencodeImage } from './reencodeImage';

// 서버 규칙과 같은 값이다. 커뮤니티 글과 장소 후기가 같은 형식·크기 제한을 쓰고, 장수만 다르다.
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const IMAGE_TYPES: readonly string[] = ['image/jpeg', 'image/png', 'image/webp'];

// 한꺼번에 올리는 사진 수의 상한이다. 업로드 URL은 올리기 직전에 받고, 저장소는 업로드가 끝난 시각을 URL 만료 시각과 비교한다.
// 그래서 한 장이 걸리는 시간은 같이 올리는 장수에 비례하고, 장수가 많아도 뒤에 기다린 사진의 URL은 늦게 받아 만료되지 않는다.
const MAX_CONCURRENT_UPLOADS = 3;

const REENCODE_FAILED = '이 사진은 올릴 수 없어요. 빼고 다른 사진을 골라 주세요.';

export type UploadImageItem = {
  key: string;
  // 서버에 올린 뒤에 받는다. 기존 이미지는 처음부터 있다.
  imageId?: number;
  // 기존 이미지의 조회 URL
  url?: string;
  file?: File;
  status: 'uploading' | 'done' | 'failed';
  error?: string;
};

// 세션이 끊겨 폼을 잠시 떠날 때 보관할 사진이다. 올리기를 마친 사진과 올리지 못해 다시 올릴 수 있는 사진만 남기고, 올리는 중인 사진은 뺀다.
export function restorableImages(images: UploadImageItem[]): UploadImageItem[] {
  return images.filter((image) => image.status === 'done' || (image.status === 'failed' && image.file !== undefined));
}

// 같은 화면에서 보관했다가 되살린 사진의 key와 새로 고른 사진의 key가 겹치지 않도록, 훅 인스턴스와 무관하게 센다.
let nextKey = 0;

type UseImageUploadsOptions = {
  max: number;
  initial?: { imageId: number; url: string }[];
  // 보관해 둔 초안에서 되살린 사진이다. 있으면 initial 대신 이 목록으로 시작한다.
  restored?: UploadImageItem[];
  // 업로드 URL을 받는 API다. 커뮤니티와 후기가 엔드포인트만 다르다.
  requestUpload: (req: ImageUploadRequest) => Promise<ImageUpload>;
  // 켜면 목록 화면에 쓸 작은 썸네일을 만들어 사진과 함께 올린다. 썸네일을 못 만들면 사진만 올린다.
  thumbnail?: boolean;
};

export type ImageUploads = {
  images: UploadImageItem[];
  messages: string[];
  uploading: boolean;
  failed: boolean;
  slotsLeft: number;
  max: number;
  // 올리기를 마친 이미지 ID를 화면 순서대로 돌려준다.
  imageIds: number[];
  handleFiles: (event: ChangeEvent<HTMLInputElement>) => void;
  retry: (image: UploadImageItem) => void;
  moveForward: (index: number) => void;
  remove: (key: string) => void;
};

// 파일을 고르면 형식·크기·장수를 먼저 거르고, 통과한 파일마다 업로드 URL을 받아 저장소에 바로 올린다.
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

  // 대기열에서 상한까지만 꺼내 올린다. 한 장이 끝나면 다음 장을 꺼낸다.
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

  // 올리는 도중에 이미지를 뺐다면 patchImage가 아무것도 바꾸지 않는다. upload는 예외를 던지지 않는다.
  // 저장소에는 브라우저에서 다시 만든 사진만 올린다. 그래서 원본의 촬영 위치 같은 메타데이터가 남지 않는다.
  // 다시 만들지 못하면 원본을 대신 올리지 않고 실패로 둔다. 미리 보기는 원본 파일을 그대로 쓴다.
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
    // 같은 파일을 다시 고를 수 있게 입력을 비운다.
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
