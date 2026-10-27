import { apiFetch } from './client';
import type { IdentityVerificationRequest, IdentityVerificationResponse, MyTrust } from './types';

export function verifyIdentity(req: IdentityVerificationRequest): Promise<IdentityVerificationResponse> {
  return apiFetch<IdentityVerificationResponse>('/api/me/identity-verification', { method: 'POST', body: req });
}

export function fetchMyTrust(signal?: AbortSignal): Promise<MyTrust> {
  return apiFetch<MyTrust>('/api/me/trust', { signal });
}
