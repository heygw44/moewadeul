import { apiFetch } from './client';
import type { Me, MeUpdateRequest, MemberProfile, PasswordChangeRequest } from './types';

export function updateMe(req: MeUpdateRequest): Promise<Me> {
  return apiFetch<Me>('/api/me', { method: 'PATCH', body: req });
}

export function changePassword(req: PasswordChangeRequest): Promise<void> {
  return apiFetch<void>('/api/me/password', { method: 'PUT', body: req });
}

export function withdraw(password: string): Promise<void> {
  return apiFetch<void>('/api/me', { method: 'DELETE', body: { password }, skipUnauthorizedHandler: true });
}

export function fetchMemberProfile(memberId: number, signal?: AbortSignal): Promise<MemberProfile> {
  return apiFetch<MemberProfile>(`/api/members/${memberId}/profile`, { signal });
}
