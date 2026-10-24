import { ApiError } from './client';
import type { ImageUploadTarget } from './types';

// 사전 서명 URL로 직접 PUT한다. 우리 서버가 아니라서 apiFetch(JSON 본문, CSRF 헤더, 세션 쿠키)를 쓰지 않는다.
// 서명에 형식이 들어 있으므로 Content-Type은 본문의 형식과 같아야 하고, Content-Length는 브라우저가 붙인다.
// 화면이 다시 만든 사진과 썸네일 Blob을 같은 방식으로 올린다.
export async function putImage(upload: ImageUploadTarget, image: Blob, signal?: AbortSignal): Promise<void> {
  let response: Response;
  try {
    response = await fetch(upload.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': image.type },
      body: image,
      credentials: 'omit',
      signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError(0, 'IMAGE_UPLOAD_FAILED', null);
  }
  if (!response.ok) {
    throw new ApiError(response.status, 'IMAGE_UPLOAD_FAILED', null);
  }
}
