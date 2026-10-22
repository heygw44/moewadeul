// 서버가 받는 썸네일 최대 크기와 같은 값이다.
const MAX_THUMBNAIL_BYTES = 256 * 1024;
const MAX_EDGE = 480;
const JPEG_QUALITY = 0.8;

// 목록 화면에 쓸 작은 JPEG를 만든다. 긴 변을 480px 이하로 줄이고, 작은 사진은 키우지 않는다.
// 브라우저가 이미지를 읽지 못하거나 결과가 크기 제한을 넘으면 null을 돌려주고, 호출하는 쪽은 원본만 올린다.
export async function createThumbnail(file: File): Promise<Blob | null> {
  let bitmap: ImageBitmap | undefined;
  try {
    if (typeof createImageBitmap !== 'function') return null;
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) return null;
    // JPEG에는 투명도가 없어서, PNG의 투명한 부분이 검게 나오지 않도록 흰색을 먼저 칠한다.
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, width, height);
    context.drawImage(bitmap, 0, 0, width, height);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY));
    if (!blob || blob.size === 0 || blob.size > MAX_THUMBNAIL_BYTES) return null;
    return blob;
  } catch {
    return null;
  } finally {
    bitmap?.close();
  }
}
