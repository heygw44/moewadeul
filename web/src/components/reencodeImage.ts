import { canvasToBlob, drawOriented, fillWhiteBehind } from './imageCanvas';

const JPEG = 'image/jpeg';
const JPEG_QUALITY = 0.92;
// iOS Safari가 만들 수 있는 캔버스 넓이의 상한이다. 이보다 큰 캔버스는 저장에 실패한다.
const MAX_CANVAS_PIXELS = 16_777_216;

// 올리기 전에 사진을 캔버스로 다시 만들어, 원본에 있던 메타데이터(촬영 위치 포함)를 지운다.
// 크기는 원본과 같고, 촬영 방향은 픽셀에 반영한다. 다만 넓이가 캔버스 상한을 넘는 아주 큰 사진은 상한 안에 들어갈 만큼만 줄인다.
// 형식은 원본과 같게 만든다. 하지만 브라우저가 그 형식으로 저장하지 못하거나(Safari의 WebP 등) 결과가 maxBytes를 넘으면 JPEG로 다시 만든다.
// 그래도 만들지 못하거나 maxBytes를 넘으면 null을 돌려준다. 이때 호출하는 쪽은 원본을 올리지 않는다.
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
