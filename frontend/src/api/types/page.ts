export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  hasNext: boolean;
}

export interface CursorPage<T> {
  content: T[];
  size: number;
  hasNext: boolean;
  nextCursor: number | null;
}

export interface PageRequest {
  page: number;
  cursor: number | null;
}
