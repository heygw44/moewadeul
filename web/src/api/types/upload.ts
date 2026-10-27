export interface ImageUploadRequest {
  contentType: string;
  sizeBytes: number;
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
  thumbnailUpload?: ImageUploadTarget;
}

export type CommunityImageUploadRequest = ImageUploadRequest;
export type CommunityImageUpload = ImageUpload;
