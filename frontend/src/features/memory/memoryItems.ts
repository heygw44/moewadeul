import type { LatLng } from '../../map/types';
import { nightsLabel } from './memoryLabels';
import { formatLocalDate } from '../../lib/datetime';

export interface MemoryCellItem {
  id: number;
  position: LatLng;
  thumbnailUrl?: string;
  visitedDate: string;
  nights: number;
  spotName?: string;
  isPrivate?: boolean;
  hiddenLabel?: string;
  imageCount: number;
}

export function memoryCellLabel(item: MemoryCellItem): string {
  const parts = [item.spotName ?? '다녀온 곳', formatLocalDate(item.visitedDate), nightsLabel(item.nights)];
  if (item.imageCount > 0) parts.push(`사진 ${item.imageCount}장`);
  if (item.isPrivate) parts.push('나만 보기');
  if (item.hiddenLabel) parts.push(item.hiddenLabel);
  return parts.join(', ');
}

export function memoryCellId(id: number): string {
  return `memory-cell-${id}`;
}
