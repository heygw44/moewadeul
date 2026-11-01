import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { reencodeImage } from './reencodeImage';

describe('reencodeImage', () => {
  const MAX_BYTES = 100;
  let encoded: Record<string, Blob | null>;
  let calls: { type: string | undefined; quality: number | undefined; width: number; height: number }[];
  let fills: string[];

  const blob = (size: number, type: string) => new Blob(['x'.repeat(size)], { type });
  const photo = (type: string) => new File(['original'], 'photo', { type });

  beforeEach(() => {
    encoded = {};
    calls = [];
    fills = [];
    vi.stubGlobal(
      'createImageBitmap',
      vi.fn(async () => ({ width: 4032, height: 3024, close: vi.fn() })),
    );
    const context = {
      globalCompositeOperation: 'source-over',
      fillStyle: '',
      drawImage: vi.fn(),
      fillRect: vi.fn(() => fills.push(context.globalCompositeOperation)),
    };
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(context as unknown as CanvasRenderingContext2D);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (
      this: HTMLCanvasElement,
      callback: BlobCallback,
      type?: string,
      quality?: number,
    ) {
      calls.push({ type, quality, width: this.width, height: this.height });
      callback(type === undefined ? null : (encoded[type] ?? null));
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('PNG는 같은 형식으로 원본 크기 그대로 다시 만들고, 투명한 부분을 칠하지 않는다', async () => {
    const png = blob(80, 'image/png');
    encoded['image/png'] = png;
    const file = photo('image/png');

    const result = await reencodeImage(file, MAX_BYTES);

    expect(result).toBe(png);
    expect(createImageBitmap).toHaveBeenCalledWith(file, { imageOrientation: 'from-image' });
    expect(calls).toEqual([{ type: 'image/png', quality: undefined, width: 4032, height: 3024 }]);
    expect(fills).toEqual([]);
  });

  it('JPEG는 품질 0.92의 JPEG로 한 번만 만든다', async () => {
    const jpeg = blob(90, 'image/jpeg');
    encoded['image/jpeg'] = jpeg;

    const result = await reencodeImage(photo('image/jpeg'), MAX_BYTES);

    expect(result).toBe(jpeg);
    expect(calls.map((call) => [call.type, call.quality])).toEqual([['image/jpeg', 0.92]]);
  });

  it('브라우저가 WebP 대신 다른 형식을 주면 흰 배경을 깐 JPEG로 다시 만든다', async () => {
    encoded['image/webp'] = blob(50, 'image/png');
    const jpeg = blob(60, 'image/jpeg');
    encoded['image/jpeg'] = jpeg;

    const result = await reencodeImage(photo('image/webp'), MAX_BYTES);

    expect(result).toBe(jpeg);
    expect(calls.map((call) => call.type)).toEqual(['image/webp', 'image/jpeg']);
    expect(fills).toEqual(['destination-over']);
  });

  it('같은 형식의 결과가 크기 제한을 1바이트라도 넘으면 JPEG로 다시 만든다', async () => {
    encoded['image/png'] = blob(MAX_BYTES + 1, 'image/png');
    const jpeg = blob(MAX_BYTES, 'image/jpeg');
    encoded['image/jpeg'] = jpeg;

    const result = await reencodeImage(photo('image/png'), MAX_BYTES);

    expect(result).toBe(jpeg);
  });

  it('JPEG로 만들어도 크기 제한을 넘으면 null을 돌려준다', async () => {
    encoded['image/png'] = blob(MAX_BYTES + 1, 'image/png');
    encoded['image/jpeg'] = blob(MAX_BYTES + 1, 'image/jpeg');

    expect(await reencodeImage(photo('image/png'), MAX_BYTES)).toBeNull();
  });

  it('브라우저가 JPEG를 만들지 못하면 null을 돌려준다', async () => {
    expect(await reencodeImage(photo('image/jpeg'), MAX_BYTES)).toBeNull();
  });

  it('브라우저가 사진을 읽지 못하면 null을 돌려준다', async () => {
    vi.stubGlobal(
      'createImageBitmap',
      vi.fn(async () => {
        throw new DOMException('decode failed', 'InvalidStateError');
      }),
    );

    expect(await reencodeImage(photo('image/jpeg'), MAX_BYTES)).toBeNull();
    expect(calls).toEqual([]);
  });

  describe('캔버스 넓이 상한(16,777,216픽셀)', () => {
    const CAP = 16_777_216;
    const bitmapOf = (width: number, height: number) =>
      vi.stubGlobal(
        'createImageBitmap',
        vi.fn(async () => ({ width, height, close: vi.fn() })),
      );

    beforeEach(() => {
      encoded['image/jpeg'] = blob(10, 'image/jpeg');
    });

    it('넓이가 상한과 같으면 크기를 그대로 둔다', async () => {
      bitmapOf(4096, 4096);

      await reencodeImage(photo('image/jpeg'), MAX_BYTES);

      expect(calls[0]).toMatchObject({ width: 4096, height: 4096 });
    });

    it('넓이가 상한을 1픽셀이라도 넘으면 비율을 지키며 상한 안으로 줄인다', async () => {
      bitmapOf(4097, 4096);

      await reencodeImage(photo('image/jpeg'), MAX_BYTES);

      const { width, height } = calls[0]!;
      expect(width * height).toBeLessThanOrEqual(CAP);
      expect(width).toBe(4096);
      expect(height).toBe(4095);
    });

    it('48MP 사진은 상한에 가깝게 줄이고 가로세로 비율을 지킨다', async () => {
      bitmapOf(8064, 6048);

      await reencodeImage(photo('image/jpeg'), MAX_BYTES);

      const { width, height } = calls[0]!;
      expect(width * height).toBeLessThanOrEqual(CAP);
      expect(width * height).toBeGreaterThan(CAP * 0.999);
      expect(width / height).toBeCloseTo(8064 / 6048, 3);
    });
  });
});
