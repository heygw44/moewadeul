// 커뮤니티 글과 장소 후기가 같은 모양으로 이미지 업로드 URL을 받는다.
export interface ImageUploadRequest {
  contentType: string;
  sizeBytes: number;
  // 브라우저가 만든 JPEG 썸네일 크기다. 썸네일을 못 만들면 보내지 않는다.
  thumbnailSizeBytes?: number;
}

export interface ImageUploadTarget {
  uploadUrl: string;
  method: 'PUT';
  headers: Record<string, string>;
  expiresAt: string;
}

export interface ImageUpload {
  imageId: number;
  uploadUrl: string;
  method: 'PUT';
  headers: Record<string, string>;
  expiresAt: string;
  // 요청에 썸네일 크기를 보냈을 때만 온다.
  thumbnailUpload?: ImageUploadTarget;
}

export type CommunityImageUploadRequest = ImageUploadRequest;
export type CommunityImageUpload = ImageUpload;
