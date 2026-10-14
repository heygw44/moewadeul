// 커뮤니티 글과 장소 후기가 같은 모양으로 이미지 업로드 URL을 받는다.
export interface ImageUploadRequest {
  contentType: string;
  sizeBytes: number;
}

export interface ImageUpload {
  imageId: number;
  uploadUrl: string;
  method: 'PUT';
  headers: Record<string, string>;
  expiresAt: string;
}

export type CommunityImageUploadRequest = ImageUploadRequest;
export type CommunityImageUpload = ImageUpload;
