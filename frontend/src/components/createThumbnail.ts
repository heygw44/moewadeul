import { canvasToBlob, drawOriented, fillWhiteBehind } from './imageCanvas';

const MAX_THUMBNAIL_BYTES = 256 * 1024;
const MAX_EDGE = 480;
const JPEG_QUALITY = 0.8;

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
