import { errorMessage } from './errors';
import type { ErrorBody, FieldErrorItem } from './types';

const GENERIC_ERROR_MESSAGE = '예상하지 못한 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요.';
const CSRF_COOKIE = 'XSRF-TOKEN';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly serverMessage: string;
  readonly traceId?: string;
  readonly fieldErrors: FieldErrorItem[];
  readonly retryAfterSeconds?: number;
  readonly body: ErrorBody | null;

  constructor(status: number, code: string, body: ErrorBody | null, retryAfterSeconds?: number) {
    super(errorMessage(code) ?? body?.message ?? GENERIC_ERROR_MESSAGE);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.serverMessage = body?.message ?? '';
    this.traceId = body?.traceId;
    this.fieldErrors = body?.fieldErrors ?? [];
    this.retryAfterSeconds = retryAfterSeconds;
    this.body = body;
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  query?: Record<string, string | number | boolean | readonly string[] | null | undefined>;
  body?: unknown;
  idempotencyKey?: string;
  signal?: AbortSignal;
  skipUnauthorizedHandler?: boolean;
}

let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

function readCookie(name: string): string | undefined {
  for (const pair of document.cookie.split(';')) {
    const separator = pair.indexOf('=');
    if (separator < 0) {
      continue;
    }
    if (pair.slice(0, separator).trim() === name) {
      return decodeURIComponent(pair.slice(separator + 1).trim());
    }
  }
  return undefined;
}

let serverClockOffsetMs = 0;

export function serverNow(): number {
  return Date.now() + serverClockOffsetMs;
}

// Date 헤더는 초 단위라서 보정한 값이 서버보다 1초 안쪽으로 늦을 수 있다. 남은 시간을 세는 쪽이 이 오차를 감안한다.
function recordServerClock(response: Response): void {
  const serverDate = Date.parse(response.headers.get('Date') ?? '');
  if (!Number.isNaN(serverDate)) {
    serverClockOffsetMs = serverDate - Date.now();
  }
}

let csrfCookieRequest: Promise<void> | null = null;

async function ensureCsrfCookie(): Promise<void> {
  csrfCookieRequest ??= fetch('/api/me', {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
    .then(
      () => undefined,
      () => undefined,
    )
    .finally(() => {
      csrfCookieRequest = null;
    });
  await csrfCookieRequest;
}

function buildUrl(path: string, query: RequestOptions['query']): string {
  if (!query) {
    return path;
  }
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === null || value === undefined) {
      continue;
    }
    if (Array.isArray(value)) {
      if (value.length > 0) {
        params.set(key, value.join(','));
      }
      continue;
    }
    params.set(key, String(value));
  }
  const search = params.toString();
  return search ? `${path}?${search}` : path;
}

// Retry-After는 보통 초 단위 정수다. HTTP 날짜 형식이 오면 지금부터 남은 초로 바꾼다.
function parseRetryAfter(header: string | null): number | undefined {
  if (!header) {
    return undefined;
  }
  const seconds = Number(header);
  if (Number.isFinite(seconds)) {
    return Math.max(0, Math.ceil(seconds));
  }
  const date = Date.parse(header);
  if (Number.isNaN(date)) {
    return undefined;
  }
  return Math.max(0, Math.ceil((date - Date.now()) / 1000));
}

function toErrorBody(value: unknown): ErrorBody | null {
  if (typeof value !== 'object' || value === null || !('code' in value) || !('message' in value)) {
    return null;
  }
  if (typeof value.code !== 'string' || typeof value.message !== 'string') {
    return null;
  }
  const body: ErrorBody = { code: value.code, message: value.message };
  if ('traceId' in value && typeof value.traceId === 'string') {
    body.traceId = value.traceId;
  }
  if ('fieldErrors' in value && Array.isArray(value.fieldErrors)) {
    body.fieldErrors = value.fieldErrors.flatMap((item: unknown): FieldErrorItem[] =>
      typeof item === 'object' &&
      item !== null &&
      'field' in item &&
      'reason' in item &&
      typeof item.field === 'string' &&
      typeof item.reason === 'string'
        ? [{ field: item.field, reason: item.reason }]
        : [],
    );
  }
  if ('suspendedUntil' in value && typeof value.suspendedUntil === 'string') {
    body.suspendedUntil = value.suspendedUntil;
  }
  return body;
}

function codeFromStatus(status: number): string {
  switch (status) {
    case 400:
      return 'INVALID_INPUT';
    case 401:
      return 'AUTHENTICATION_REQUIRED';
    case 403:
      return 'ACCESS_DENIED';
    case 404:
      return 'NOT_FOUND';
    case 405:
      return 'METHOD_NOT_ALLOWED';
    case 415:
      return 'UNSUPPORTED_MEDIA_TYPE';
    case 429:
      return 'TOO_MANY_REQUESTS';
    default:
      return 'INTERNAL_ERROR';
  }
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

const GET_TIMEOUT_MS = 15_000;
// 변경 요청은 서버가 처리를 마쳤을 수 있어 더 기다린다. 끊긴 요청을 다시 보낼 때는 멱등 키를 그대로 쓴다(idempotency.ts).
const MUTATION_TIMEOUT_MS = 30_000;

function startDeadline(callerSignal: AbortSignal | undefined, timeoutMs: number) {
  const controller = new AbortController();
  let expired = false;
  const followCaller = () => controller.abort(callerSignal?.reason);
  if (callerSignal?.aborted) {
    followCaller();
  } else {
    callerSignal?.addEventListener('abort', followCaller, { once: true });
  }
  const timer = setTimeout(() => {
    expired = true;
    controller.abort();
  }, timeoutMs);
  return {
    signal: controller.signal,
    expired: () => expired,
    clear: () => {
      clearTimeout(timer);
      callerSignal?.removeEventListener('abort', followCaller);
    },
  };
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers = new Headers({ Accept: 'application/json' });

  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  if (options.idempotencyKey) {
    headers.set('Idempotency-Key', options.idempotencyKey);
  }
  if (method !== 'GET') {
    let csrfToken = readCookie(CSRF_COOKIE);
    if (csrfToken === undefined) {
      await ensureCsrfCookie();
      csrfToken = readCookie(CSRF_COOKIE);
    }
    if (csrfToken !== undefined) {
      headers.set('X-XSRF-TOKEN', csrfToken);
    }
  }

  const deadline = startDeadline(options.signal, method === 'GET' ? GET_TIMEOUT_MS : MUTATION_TIMEOUT_MS);
  let response: Response;
  let text: string;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method,
      headers,
      credentials: 'same-origin',
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: deadline.signal,
    });
    recordServerClock(response);
    text = response.status === 204 ? '' : await response.text();
  } catch (error) {
    if (deadline.expired()) {
      throw new ApiError(0, 'REQUEST_TIMEOUT', null);
    }
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }
    throw new ApiError(0, 'NETWORK_ERROR', null);
  } finally {
    deadline.clear();
  }

  if (response.ok) {
    if (text === '') {
      return undefined as T;
    }
    const data = parseJson(text);
    if (data === undefined) {
      throw new ApiError(response.status, 'INTERNAL_ERROR', null);
    }
    return data as T;
  }

  const body = text === '' ? null : toErrorBody(parseJson(text));
  const retryAfterSeconds = response.status === 429 ? parseRetryAfter(response.headers.get('Retry-After')) : undefined;
  const error = new ApiError(response.status, body?.code ?? codeFromStatus(response.status), body, retryAfterSeconds);

  if (response.status === 401 && !options.skipUnauthorizedHandler) {
    unauthorizedHandler?.();
  }
  throw error;
}
