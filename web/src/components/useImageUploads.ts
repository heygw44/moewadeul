import { useState } from 'react';
import type { ChangeEvent } from 'react';
import { toUserMessage } from '../api/errors';
import type { ImageUpload, ImageUploadRequest } from '../api/types';
import { putImage } from '../api/uploads';

// 서버 규칙과 같은 값이다. 커뮤니티 글과 장소 후기가 같은 형식·크기 제한을 쓰고, 장수만 다르다.
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const IMAGE_TYPES: readonly string[] = ['image/jpeg', 'image/png', 'image/webp'];

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
export function useImageUploads({ max, initial = [], restored, requestUpload }: UseImageUploadsOptions): ImageUploads {
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

  // 올리는 도중에 이미지를 뺐다면 patchImage가 아무것도 바꾸지 않는다.
  async function upload(key: string, file: File) {
    patchImage(key, { status: 'uploading', error: undefined });
    try {
      const target = await requestUpload({ contentType: file.type, sizeBytes: file.size });
      await putImage(target, file);
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
      if (item.file) void upload(item.key, item.file);
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
      if (image.file) void upload(image.key, image.file);
    },
    moveForward,
    remove,
  };
}
