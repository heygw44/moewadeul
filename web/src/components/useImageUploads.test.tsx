import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import type { ChangeEvent } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ImageUpload } from '../api/types';
import { putImage } from '../api/uploads';
import { useImageUploads } from './useImageUploads';
import type { ImageUploads } from './useImageUploads';

vi.mock('../api/uploads', () => ({ putImage: vi.fn() }));

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
    vi.mocked(putImage).mockImplementation(async () => {
      putCalls += 1;
    });
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
});
