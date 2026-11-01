export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  hasNext: boolean;
}
