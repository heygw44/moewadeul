import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';

export function adminErrorMessage(error: unknown): string {
  if (
    error instanceof ApiError &&
    error.code === 'INVALID_INPUT' &&
    error.fieldErrors.length === 0 &&
    error.serverMessage
  ) {
    return error.serverMessage;
  }
  return toUserMessage(error);
}
