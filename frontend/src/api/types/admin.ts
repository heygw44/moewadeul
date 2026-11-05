import type { SpotType } from './spot';
import type { BakjiReportReason } from './bakji';
import type { BasecampStatus } from './basecamp';
import type { CommunityReportReason } from './community';
import type { ProgramApplicationStatus, ProgramCancelReason, ProgramDetail } from './program';
import type { ReportKind, ReportStatus, ReportType, SanctionStatus, SanctionType } from './report';

export interface AdminMemberRef {
  memberId: number;
  nickname: string;
}

export interface AdminReportSummary {
  reportId: number;
  kind: ReportKind;
  type: ReportType;
  urgent: boolean;
  status: ReportStatus;
  reporter: AdminMemberRef;
  target: AdminMemberRef;
  basecampId: number;
  createdAt: string;
}

export interface AdminReportBasecamp {
  basecampId: number;
  title: string;
  status: BasecampStatus;
  startDate: string;
}

export interface AdminReportReview {
  comment: string;
  tags: string[];
  rejoinWanted: boolean;
  hidden: boolean;
}

export interface AdminSanctionHistoryItem {
  sanctionId: number;
  type: SanctionType;
  level: number | null;
  status: SanctionStatus;
  reason: string;
  startsAt: string;
  endsAt: string | null;
  liftedAt: string | null;
}

export interface AdminReportDetail extends AdminReportSummary {
  content: string;
  resultNote: string | null;
  handledBy: number | null;
  handledAt: string | null;
  basecamp: AdminReportBasecamp | null;
  companionReview: AdminReportReview | null;
  sanctionHistory: AdminSanctionHistoryItem[];
}

export type ConfirmableSanctionType = Exclude<SanctionType, 'TEMPORARY_72H'>;

export interface AdminReportActionRequest {
  sanction: { type: ConfirmableSanctionType; reason: string } | null;
  hideReview: boolean;
  note?: string;
}

export interface AdminReportActionResponse {
  reportId: number;
  status: ReportStatus;
  sanctionId: number | null;
}

export type AdminSpotStatus = 'PENDING_REVIEW' | 'HIDDEN';

export interface AdminSpotSummary {
  spotId: number;
  type: SpotType;
  name: string;
  status: string;
  lat: number;
  lng: number;
  parkWarning: boolean;
  reporter: { memberId: number; nickname: string } | null;
  reportCount: number;
  reasonCounts: Record<BakjiReportReason, number>;
  recentReports: { reason: BakjiReportReason; content: string | null; createdAt: string }[];
  statusChangedAt: string;
}

export type SyncJobType = 'GOCAMPING' | 'FOREST' | 'FOREST_STANDARD' | 'PARK_BOUNDARY' | 'BAKJI_REJUDGE';

export type SyncJobStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface SyncJobRun {
  jobRunId: number;
  jobType: SyncJobType;
  status: SyncJobStatus;
  processedCount: number;
  skippedCount: number;
  errorMessage: string | null;
  startedAt: string;
  finishedAt: string | null;
}

export type AdminCommunityStatus = 'PENDING_REVIEW' | 'HIDDEN';

export type AdminModerationResultStatus = 'ACTIVE' | 'HIDDEN';

export interface AdminCommunityRecentReport {
  reason: CommunityReportReason;
  content: string | null;
  createdAt: string;
}

interface AdminCommunityItemBase {
  author: AdminMemberRef;
  status: AdminCommunityStatus;
  reportCount: number;
  reasonCounts: Partial<Record<CommunityReportReason, number>>;
  recentReports: AdminCommunityRecentReport[];
  statusChangedAt: string;
}

export interface AdminCommunityPost extends AdminCommunityItemBase {
  postId: number;
  title: string;
  content: string;
}

export interface AdminCommunityComment extends AdminCommunityItemBase {
  commentId: number;
  postId: number;
  content: string;
}

export interface AdminProgramCreateRequest {
  title: string;
  description: string;
  spotId?: number | null;
  locationText: string;
  startAt: string;
  endAt: string;
  capacity: number;
  fee: number;
  applyOpenAt: string;
  applyCloseAt: string;
  paymentDeadlineMinutes?: number;
  overnight: boolean;
}

export type AdminProgramReviseRequest = Partial<AdminProgramCreateRequest>;

export type AdminProgramDetail = Omit<ProgramDetail, 'myApplication' | 'vacancyAlertSubscribed'>;

export interface AdminProgramCancelResponse {
  programId: number;
  status: 'CANCELED';
  canceledApplicationCount: number;
}

export interface AdminProgramApplicant {
  applicationId: number;
  member: AdminMemberRef;
  status: ProgramApplicationStatus;
  paymentDueAt: string | null;
  confirmedAt: string | null;
  canceledAt: string | null;
  cancelReason: ProgramCancelReason | null;
  reviewRequestedAt: string | null;
  createdAt: string;
}

export type AdminAuditAction =
  | 'REPORT_START_REVIEW'
  | 'REPORT_ACTION'
  | 'REPORT_DISMISS'
  | 'SANCTION_LIFT'
  | 'SPOT_HIDE'
  | 'SPOT_RESTORE'
  | 'COMMUNITY_HIDE'
  | 'COMMUNITY_RESTORE'
  | 'MEMORY_HIDE'
  | 'MEMORY_RESTORE'
  | 'SYNC_JOB_RUN'
  | 'PROGRAM_CREATE'
  | 'PROGRAM_UPDATE'
  | 'PROGRAM_CANCEL';

export type AdminAuditTargetType =
  | 'MEMBER_REPORT'
  | 'SANCTION'
  | 'SPOT'
  | 'COMMUNITY_POST'
  | 'COMMUNITY_COMMENT'
  | 'MEMORY'
  | 'PROGRAM'
  | 'SYNC_JOB_RUN';

export interface AdminAuditLog {
  auditLogId: number;
  adminId: number;
  action: AdminAuditAction;
  targetType: AdminAuditTargetType;
  targetId: number;
  detail: Record<string, unknown> | null;
  createdAt: string;
}
