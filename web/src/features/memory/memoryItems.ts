import type { LatLng } from '../../map/types';
import { nightsLabel } from './memoryLabels';
import { formatLocalDate } from '../../lib/datetime';

// 지도 핀과 격자 칸이 함께 쓰는 기록 한 건이다. 화면마다 다른 응답(내 기록, 회원별 기록)을 이 모양으로 바꾼다.
export interface MemoryCellItem {
  id: number;
  position: LatLng;
  thumbnailUrl?: string;
  visitedDate: string;
  nights: number;
  spotName?: string;
  // 나만 보기로 남긴 기록이다. 내 발자국 지도에서만 true가 될 수 있다.
  isPrivate?: boolean;
  imageCount: number;
}

// 핀 이름과 격자 칸 이름으로 읽히는 글이다.
export function memoryCellLabel(item: MemoryCellItem): string {
  const parts = [item.spotName ?? '다녀온 곳', formatLocalDate(item.visitedDate), nightsLabel(item.nights)];
  if (item.imageCount > 0) parts.push(`사진 ${item.imageCount}장`);
  if (item.isPrivate) parts.push('나만 보기');
  return parts.join(', ');
}

export function memoryCellId(id: number): string {
  return `memory-cell-${id}`;
}
