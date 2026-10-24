import { canvasToBlob, drawOriented, fillWhiteBehind } from './imageCanvas';

// 서버가 받는 썸네일 최대 크기와 같은 값이다.
const MAX_THUMBNAIL_BYTES = 256 * 1024;
const MAX_EDGE = 480;
const JPEG_QUALITY = 0.8;

// 목록 화면에 쓸 작은 JPEG를 만든다. 긴 변을 480px 이하로 줄이고, 작은 사진은 키우지 않는다.
// 브라우저가 이미지를 읽지 못하거나 결과가 크기 제한을 넘으면 null을 돌려주고, 호출하는 쪽은 원본만 올린다.
export async function createThumbnail(file: File): Promise<Blob | null> {
  try {
    const canvas = await drawOriented(file, MAX_EDGE);
    if (!canvas) return null;
    fillWhiteBehind(canvas);
    const blob = await canvasToBlob(canvas, 'image/jpeg', JPEG_QUALITY);
    if (!blob || blob.size === 0 || blob.size > MAX_THUMBNAIL_BYTES) return null;
    return blob;
  } catch {
    return null;
  }
}
