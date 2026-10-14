// 목록 공통 형식. 서버는 전체 개수를 주지 않고 다음 페이지가 있는지만 알려 준다.
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  hasNext: boolean;
}

// 번호형 페이지 목록(커뮤니티 글)은 조건에 맞는 전체 개수와 페이지 수를 더 준다.
export interface NumberedPage<T> extends Page<T> {
  totalElements: number;
  totalPages: number;
}
