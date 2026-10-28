import { ApiError, apiFetch } from './client';
import type {
  LoginRequest,
  LoginResponse,
  Me,
  MemberStatus,
  PasswordResetConfirmRequest,
  SignupRequest,
  SignupResponse,
} from './types';

export function signUp(req: SignupRequest): Promise<SignupResponse> {
  return apiFetch<SignupResponse>('/api/members', { method: 'POST', body: req });
}

export function login(req: LoginRequest): Promise<LoginResponse> {
  return apiFetch<LoginResponse>('/api/auth/login', { method: 'POST', body: req, skipUnauthorizedHandler: true });
}

export function logout(): Promise<void> {
  return apiFetch<void>('/api/auth/logout', { method: 'POST', skipUnauthorizedHandler: true });
}

export function requestPasswordReset(email: string): Promise<void> {
  return apiFetch<void>('/api/auth/password-reset/request', {
    method: 'POST',
    body: { email },
    skipUnauthorizedHandler: true,
  });
}

export function confirmPasswordReset(req: PasswordResetConfirmRequest): Promise<void> {
  return apiFetch<void>('/api/auth/password-reset/confirm', {
    method: 'POST',
    body: req,
    skipUnauthorizedHandler: true,
  });
}

export function verifyEmail(code: string): Promise<{ status: MemberStatus }> {
  return apiFetch<{ status: MemberStatus }>('/api/me/email-verification', { method: 'POST', body: { code } });
}

export function resendVerification(): Promise<void> {
  return apiFetch<void>('/api/me/email-verification/resend', { method: 'POST' });
}

export async function fetchMe(signal?: AbortSignal): Promise<Me | null> {
  try {
    return await apiFetch<Me>('/api/me', { signal, skipUnauthorizedHandler: true });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}
