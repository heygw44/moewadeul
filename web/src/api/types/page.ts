export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  hasNext: boolean;
}

export interface NumberedPage<T> extends Page<T> {
  totalElements: number;
  totalPages: number;
}
