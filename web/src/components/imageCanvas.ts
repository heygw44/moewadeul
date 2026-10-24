// 사진을 촬영 방향대로 돌려 캔버스에 그린다. 긴 변이 maxEdge를 넘으면 그 길이로 줄이고, 작은 사진은 키우지 않는다.
// 넓이가 maxPixels를 넘으면 가로세로 비율을 지키며 그 안에 들어갈 만큼만 줄인다.
// 캔버스에는 픽셀만 남아서, 여기서 다시 만든 파일에는 원본의 메타데이터(촬영 위치 포함)가 들어가지 않는다.
// 브라우저가 캔버스를 만들지 못하면 null을 돌려주고, 사진을 읽지 못하면 예외를 던진다.
export async function drawOriented(
  file: Blob,
  maxEdge = Infinity,
  maxPixels = Infinity,
): Promise<HTMLCanvasElement | null> {
  if (typeof createImageBitmap !== 'function') return null;
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  try {
    const scale = Math.min(1, maxEdge / Math.max(bitmap.width, bitmap.height));
    let width = Math.max(1, Math.round(bitmap.width * scale));
    let height = Math.max(1, Math.round(bitmap.height * scale));
    if (width * height > maxPixels) {
      // 내림해야 줄인 넓이가 상한을 넘지 않는다.
      const shrink = Math.sqrt(maxPixels / (width * height));
      width = Math.max(1, Math.floor(width * shrink));
      height = Math.max(1, Math.floor(height * shrink));
    }
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) return null;
    context.drawImage(bitmap, 0, 0, width, height);
    return canvas;
  } finally {
    bitmap.close();
  }
}

// JPEG에는 투명도가 없어서, PNG·WebP의 투명한 부분이 검게 나오지 않도록 그림 뒤에 흰색을 칠한다.
export function fillWhiteBehind(canvas: HTMLCanvasElement): void {
  const context = canvas.getContext('2d');
  if (!context) return;
  context.globalCompositeOperation = 'destination-over';
  context.fillStyle = '#ffffff';
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.globalCompositeOperation = 'source-over';
}

// 브라우저가 그 형식으로 저장하지 못하면 null을 주거나 PNG로 바꿔 준다. 그래서 받는 쪽은 결과의 형식을 다시 확인한다.
export function canvasToBlob(canvas: HTMLCanvasElement, type: string, quality?: number): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality));
}
