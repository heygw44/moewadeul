export interface FieldErrorItem {
  field: string;
  reason: string;
}

export interface ErrorBody {
  code: string;
  message: string;
  traceId?: string;
  fieldErrors?: FieldErrorItem[];
  suspendedUntil?: string;
}
