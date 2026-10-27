import { apiFetch } from './client';
import type {
  MyProgramApplicationItem,
  Page,
  ProgramApplicationStatus,
  ProgramApplyResponse,
  ProgramCancelResponse,
  ProgramDetail,
  ProgramPage,
  ProgramPayResponse,
  ProgramStatus,
} from './types';

export type ProgramListStatus = Exclude<ProgramStatus, 'CANCELED'>;

export function listPrograms(
  status: ProgramListStatus | undefined,
  page: number,
  signal?: AbortSignal,
): Promise<ProgramPage> {
  return apiFetch<ProgramPage>('/api/programs', { query: { status, page }, signal });
}

export function getProgram(programId: number, signal?: AbortSignal): Promise<ProgramDetail> {
  return apiFetch<ProgramDetail>(`/api/programs/${programId}`, { signal });
}

export function applyProgram(
  programId: number,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<ProgramApplyResponse> {
  return apiFetch<ProgramApplyResponse>(`/api/programs/${programId}/applications`, {
    method: 'POST',
    idempotencyKey,
    signal,
  });
}

export function payApplication(
  applicationId: number,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<ProgramPayResponse> {
  return apiFetch<ProgramPayResponse>(`/api/program-applications/${applicationId}/pay`, {
    method: 'POST',
    body: { method: 'FAKE_CARD' },
    idempotencyKey,
    signal,
  });
}

export function cancelApplication(applicationId: number, signal?: AbortSignal): Promise<ProgramCancelResponse> {
  return apiFetch<ProgramCancelResponse>(`/api/program-applications/${applicationId}/cancel`, {
    method: 'POST',
    signal,
  });
}

export function subscribeVacancyAlert(programId: number, signal?: AbortSignal): Promise<void> {
  return apiFetch<void>(`/api/programs/${programId}/vacancy-alerts`, { method: 'POST', signal });
}

export function unsubscribeVacancyAlert(programId: number, signal?: AbortSignal): Promise<void> {
  return apiFetch<void>(`/api/programs/${programId}/vacancy-alerts`, { method: 'DELETE', signal });
}

export function listMyProgramApplications(
  status: ProgramApplicationStatus | undefined,
  page: number,
  signal?: AbortSignal,
): Promise<Page<MyProgramApplicationItem>> {
  return apiFetch<Page<MyProgramApplicationItem>>('/api/me/program-applications', {
    query: { status, page },
    signal,
  });
}
