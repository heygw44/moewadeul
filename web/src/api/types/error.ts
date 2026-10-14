export interface FieldErrorItem {
  field: string;
  reason: string;
}

export interface ErrorBody {
  code: string;
  message: string;
  // 서버는 추적 ID를 늘 넣지만, 값을 못 구하면 null이 올 수 있어서 이쪽에서는 비워 둔다.
  traceId?: string;
  // 서버는 입력 검증 오류일 때만 이 필드를 넣는다.
  fieldErrors?: FieldErrorItem[];
  // 이용 정지 중 로그인할 때만 온다. 해제 시각이 없는 정지면 서버가 이 필드를 빼고 준다.
  suspendedUntil?: string;
}
