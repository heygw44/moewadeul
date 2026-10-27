import { canvasToBlob, drawOriented, fillWhiteBehind } from './imageCanvas';

const JPEG = 'image/jpeg';
const JPEG_QUALITY = 0.92;
// iOS Safari가 만들 수 있는 캔버스 넓이의 상한이다. 이보다 큰 캔버스는 저장에 실패한다.
const MAX_CANVAS_PIXELS = 16_777_216;

export async function reencodeImage(file: File, maxBytes: number): Promise<Blob | null> {
  try {
    const canvas = await drawOriented(file, Infinity, MAX_CANVAS_PIXELS);
    if (!canvas) return null;
    if (file.type !== JPEG) {
      const same = await canvasToBlob(canvas, file.type);
      if (fits(same, file.type, maxBytes)) return same;
    }
    fillWhiteBehind(canvas);
    const jpeg = await canvasToBlob(canvas, JPEG, JPEG_QUALITY);
    return fits(jpeg, JPEG, maxBytes) ? jpeg : null;
  } catch {
    return null;
  }
}

function fits(blob: Blob | null, type: string, maxBytes: number): blob is Blob {
  return blob !== null && blob.type === type && blob.size > 0 && blob.size <= maxBytes;
}
