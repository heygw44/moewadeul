import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import type { ChangeEvent } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ImageUpload } from '../api/types';
import { putImage } from '../api/uploads';
import { reencodeImage } from './reencodeImage';
import { useImageUploads } from './useImageUploads';
import type { ImageUploads } from './useImageUploads';

vi.mock('../api/uploads', () => ({ putImage: vi.fn() }));
// jsdom에는 실제 캔버스가 없어서, 다시 만든 사진을 돌려주는 함수를 바꿔 끼운다.
vi.mock('./reencodeImage', () => ({ reencodeImage: vi.fn() }));

// React가 act 밖의 상태 변경을 경고하지 않도록 테스트 환경임을 알린다.
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

type Pending = { resolve: (upload: ImageUpload) => void; reject: (error: Error) => void };

describe('useImageUploads', () => {
  let container: HTMLDivElement;
  let root: Root;
  let uploads: ImageUploads;
  let requests: Pending[];
  let putCalls: number;

  // 프로젝트의 TypeScript lib에 Promise.withResolvers가 없어서 실행기 형태로 만든다.
  const requestUpload = vi.fn(
    () =>
      new Promise<ImageUpload>((resolve, reject) => {
        requests.push({ resolve, reject });
      }),
  );

  function Probe() {
    uploads = useImageUploads({ max: 10, requestUpload });
    return null;
  }

  function photos(count: number) {
    return Array.from({ length: count }, (_, i) => new File(['x'], `p${i}.jpg`, { type: 'image/jpeg' }));
  }

  async function choose(files: File[]) {
    await act(async () => {
      uploads.handleFiles({ target: { files, value: '' } } as unknown as ChangeEvent<HTMLInputElement>);
    });
  }

  // requests[index]의 업로드 URL을 내주고, 그 사진이 올라가는 것까지 기다린다.
  async function finish(index: number) {
    await act(async () => {
      requests[index]!.resolve({ imageId: index + 1 } as ImageUpload);
    });
  }

  beforeEach(() => {
    requests = [];
    putCalls = 0;
    requestUpload.mockClear();
    vi.mocked(putImage).mockClear();
    vi.mocked(putImage).mockImplementation(async () => {
      putCalls += 1;
    });
    vi.mocked(reencodeImage).mockReset();
    vi.mocked(reencodeImage).mockImplementation(async (file) => new Blob(['reencoded'], { type: file.type }));
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    act(() => root.render(<Probe />));
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
  });

  it('사진을 여러 장 고르면 3장만 업로드 URL을 받고, 한 장이 끝나야 다음 장이 받는다', async () => {
    await choose(photos(5));
    expect(requestUpload).toHaveBeenCalledTimes(3);

    await finish(0);
    expect(requestUpload).toHaveBeenCalledTimes(4);
    expect(putCalls).toBe(1);

    await finish(1);
    expect(requestUpload).toHaveBeenCalledTimes(5);
  });

  it('올리기에 실패한 사진도 자리를 비워 다음 사진이 이어서 올라간다', async () => {
    await choose(photos(4));
    expect(requestUpload).toHaveBeenCalledTimes(3);

    await act(async () => {
      requests[0]!.reject(new Error('network'));
    });

    expect(requestUpload).toHaveBeenCalledTimes(4);
    expect(uploads.images.map((image) => image.status)).toEqual(['failed', 'uploading', 'uploading', 'uploading']);
  });

  it('기다리는 중에 뺀 사진은 업로드 URL을 받지 않는다', async () => {
    await choose(photos(4));
    const queued = uploads.images[3]!;

    await act(async () => {
      uploads.remove(queued.key);
    });
    await finish(0);

    expect(requestUpload).toHaveBeenCalledTimes(3);
  });

  it('저장소에는 원본 대신 다시 만든 사진을 올리고, 업로드 URL도 다시 만든 사진의 형식과 크기로 받는다', async () => {
    const original = new File(['original-with-exif'], 'p.webp', { type: 'image/webp' });
    const reencoded = new Blob(['jpeg-without-exif-longer'], { type: 'image/jpeg' });
    vi.mocked(reencodeImage).mockResolvedValue(reencoded);

    await choose([original]);
    await finish(0);

    expect(reencodeImage).toHaveBeenCalledWith(original, 5 * 1024 * 1024);
    expect(requestUpload).toHaveBeenCalledWith({ contentType: 'image/jpeg', sizeBytes: reencoded.size });
    expect(putImage).toHaveBeenCalledTimes(1);
    expect(vi.mocked(putImage).mock.calls[0]![1]).toBe(reencoded);
    expect(uploads.images[0]!.status).toBe('done');
  });

  it('사진을 다시 만들지 못하면 원본을 올리지 않고 실패로 둔다', async () => {
    vi.mocked(reencodeImage).mockResolvedValue(null);

    await choose(photos(1));

    expect(requestUpload).not.toHaveBeenCalled();
    expect(putImage).not.toHaveBeenCalled();
    expect(uploads.images[0]!.status).toBe('failed');
    expect(uploads.images[0]!.error).toBe('이 사진은 올릴 수 없어요. 빼고 다른 사진을 골라 주세요.');
    expect(uploads.failed).toBe(true);
  });
});
