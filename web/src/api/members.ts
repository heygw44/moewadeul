import { apiFetch } from './client';
import type { Me, MeUpdateRequest, MemberProfile, PasswordChangeRequest } from './types';

export function updateMe(req: MeUpdateRequest): Promise<Me> {
  return apiFetch<Me>('/api/me', { method: 'PATCH', body: req });
}

// 현재 비밀번호가 틀려도 400(MEMBER_CURRENT_PASSWORD_MISMATCH)이라서 로그아웃 처리와 겹치지 않는다.
// 성공하면 서버가 이 세션의 ID를 새로 발급해 쿠키로 내려주고, 다른 기기의 세션은 지운다.
export function changePassword(req: PasswordChangeRequest): Promise<void> {
  return apiFetch<void>('/api/me/password', { method: 'PUT', body: req });
}

// 비밀번호가 틀리면 401(LOGIN_FAILED)이라서 로그인 화면으로 보내는 처리를 건너뛴다. 세션이 끊긴 401은 화면이 직접 다룬다.
export function withdraw(password: string): Promise<void> {
  return apiFetch<void>('/api/me', { method: 'DELETE', body: { password }, skipUnauthorizedHandler: true });
}

// 비로그인으로 부르면 서버가 닉네임만 준다. 없는 회원과 탈퇴한 회원은 404다.
export function fetchMemberProfile(memberId: number, signal?: AbortSignal): Promise<MemberProfile> {
  return apiFetch<MemberProfile>(`/api/members/${memberId}/profile`, { signal });
}
