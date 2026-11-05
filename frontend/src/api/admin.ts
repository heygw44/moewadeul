import { apiFetch } from './client';
import type {
  AdminAuditLog,
  AdminAuditTargetType,
  AdminCommunityComment,
  AdminCommunityPost,
  AdminCommunityStatus,
  AdminMemoryStatus,
  AdminMemorySummary,
  AdminModerationResultStatus,
  AdminProgramApplicant,
  AdminProgramCancelResponse,
  AdminProgramCreateRequest,
  AdminProgramDetail,
  AdminProgramReviseRequest,
  AdminReportActionRequest,
  AdminReportActionResponse,
  AdminReportDetail,
  AdminReportSummary,
  AdminSpotStatus,
  AdminSpotSummary,
  Page,
  ProgramApplicationStatus,
  ReportStatus,
  SanctionLiftReason,
  SanctionStatus,
  SyncJobRun,
  SyncJobType,
} from './types';

const ADMIN_PAGE_SIZE = 20;

export function fetchAdminMemberReports(
  params: { status?: ReportStatus; urgent?: boolean; page: number },
  signal?: AbortSignal,
): Promise<Page<AdminReportSummary>> {
  return apiFetch<Page<AdminReportSummary>>('/api/admin/member-reports', {
    query: { status: params.status, urgent: params.urgent, page: params.page, size: ADMIN_PAGE_SIZE },
    signal,
  });
}

export function fetchAdminMemberReport(reportId: number, signal?: AbortSignal): Promise<AdminReportDetail> {
  return apiFetch<AdminReportDetail>(`/api/admin/member-reports/${reportId}`, { signal });
}

export function startReportReview(reportId: number): Promise<{ reportId: number; status: ReportStatus }> {
  return apiFetch(`/api/admin/member-reports/${reportId}/start-review`, { method: 'POST' });
}

export function actionReport(reportId: number, request: AdminReportActionRequest): Promise<AdminReportActionResponse> {
  return apiFetch<AdminReportActionResponse>(`/api/admin/member-reports/${reportId}/action`, {
    method: 'POST',
    body: request,
  });
}

export function dismissReport(
  reportId: number,
  request: { note?: string },
): Promise<{ reportId: number; status: ReportStatus }> {
  return apiFetch(`/api/admin/member-reports/${reportId}/dismiss`, { method: 'POST', body: request });
}

export function liftSanction(
  sanctionId: number,
  reason: SanctionLiftReason,
): Promise<{ sanctionId: number; status: SanctionStatus }> {
  return apiFetch(`/api/admin/sanctions/${sanctionId}/lift`, { method: 'POST', body: { reason } });
}

export function fetchAdminSpots(
  params: { status: AdminSpotStatus; page: number },
  signal?: AbortSignal,
): Promise<Page<AdminSpotSummary>> {
  return apiFetch<Page<AdminSpotSummary>>('/api/admin/spots', {
    query: { status: params.status, page: params.page, size: ADMIN_PAGE_SIZE },
    signal,
  });
}

export function hideSpot(spotId: number): Promise<{ spotId: number; status: string }> {
  return apiFetch(`/api/admin/spots/${spotId}/hide`, { method: 'POST' });
}

export function restoreSpot(spotId: number): Promise<{ spotId: number; status: string }> {
  return apiFetch(`/api/admin/spots/${spotId}/restore`, { method: 'POST' });
}

export function fetchAdminMemories(
  params: { status: AdminMemoryStatus; page: number },
  signal?: AbortSignal,
): Promise<Page<AdminMemorySummary>> {
  return apiFetch<Page<AdminMemorySummary>>('/api/admin/memories', {
    query: { status: params.status, page: params.page, size: ADMIN_PAGE_SIZE },
    signal,
  });
}

export function hideMemory(memoryId: number): Promise<{ memoryId: number; status: string }> {
  return apiFetch(`/api/admin/memories/${memoryId}/hide`, { method: 'POST' });
}

export function restoreMemory(memoryId: number): Promise<{ memoryId: number; status: string }> {
  return apiFetch(`/api/admin/memories/${memoryId}/restore`, { method: 'POST' });
}

export function fetchSyncJobRuns(
  params: { jobType?: SyncJobType; page: number; size?: number },
  signal?: AbortSignal,
): Promise<Page<SyncJobRun>> {
  return apiFetch<Page<SyncJobRun>>('/api/admin/sync-jobs', {
    query: { jobType: params.jobType, page: params.page, size: params.size ?? ADMIN_PAGE_SIZE },
    signal,
  });
}

export function startSyncJob(jobType: SyncJobType): Promise<{ jobRunId: number }> {
  return apiFetch('/api/admin/sync-jobs', { method: 'POST', body: { jobType } });
}

export function fetchAdminCommunityPosts(
  params: { status: AdminCommunityStatus; page: number },
  signal?: AbortSignal,
): Promise<Page<AdminCommunityPost>> {
  return apiFetch<Page<AdminCommunityPost>>('/api/admin/community/posts', {
    query: { status: params.status, page: params.page, size: ADMIN_PAGE_SIZE },
    signal,
  });
}

export function hideCommunityPost(postId: number): Promise<{ postId: number; status: AdminModerationResultStatus }> {
  return apiFetch(`/api/admin/community/posts/${postId}/hide`, { method: 'POST' });
}

export function restoreCommunityPost(postId: number): Promise<{ postId: number; status: AdminModerationResultStatus }> {
  return apiFetch(`/api/admin/community/posts/${postId}/restore`, { method: 'POST' });
}

export function fetchAdminCommunityComments(
  params: { status: AdminCommunityStatus; page: number },
  signal?: AbortSignal,
): Promise<Page<AdminCommunityComment>> {
  return apiFetch<Page<AdminCommunityComment>>('/api/admin/community/comments', {
    query: { status: params.status, page: params.page, size: ADMIN_PAGE_SIZE },
    signal,
  });
}

export function hideCommunityComment(
  commentId: number,
): Promise<{ commentId: number; status: AdminModerationResultStatus }> {
  return apiFetch(`/api/admin/community/comments/${commentId}/hide`, { method: 'POST' });
}

export function restoreCommunityComment(
  commentId: number,
): Promise<{ commentId: number; status: AdminModerationResultStatus }> {
  return apiFetch(`/api/admin/community/comments/${commentId}/restore`, { method: 'POST' });
}

export function createProgram(
  request: AdminProgramCreateRequest,
  idempotencyKey: string,
): Promise<{ programId: number }> {
  return apiFetch('/api/admin/programs', { method: 'POST', body: request, idempotencyKey });
}

export function reviseProgram(programId: number, request: AdminProgramReviseRequest): Promise<AdminProgramDetail> {
  return apiFetch<AdminProgramDetail>(`/api/admin/programs/${programId}`, { method: 'PATCH', body: request });
}

export function cancelProgram(programId: number): Promise<AdminProgramCancelResponse> {
  return apiFetch<AdminProgramCancelResponse>(`/api/admin/programs/${programId}/cancel`, { method: 'POST' });
}

export function fetchProgramApplicants(
  programId: number,
  params: { status?: ProgramApplicationStatus; page: number },
  signal?: AbortSignal,
): Promise<Page<AdminProgramApplicant>> {
  return apiFetch<Page<AdminProgramApplicant>>(`/api/admin/programs/${programId}/applications`, {
    query: { status: params.status, page: params.page, size: ADMIN_PAGE_SIZE },
    signal,
  });
}

export function fetchAdminAuditLogs(
  params: { adminId?: number; targetType?: AdminAuditTargetType; from?: string; to?: string; page: number },
  signal?: AbortSignal,
): Promise<Page<AdminAuditLog>> {
  return apiFetch<Page<AdminAuditLog>>('/api/admin/audit-logs', {
    query: {
      adminId: params.adminId,
      targetType: params.targetType,
      from: params.from,
      to: params.to,
      page: params.page,
      size: ADMIN_PAGE_SIZE,
    },
    signal,
  });
}
