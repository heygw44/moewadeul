import type { SpotType } from './spot';
import type { BakjiReportReason } from './bakji';
import type { BasecampStatus } from './basecamp';
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
  // 단계가 없는 제재(임시 정지 등)는 null이다.
  level: number | null;
  status: SanctionStatus;
  reason: string;
  startsAt: string;
  endsAt: string | null;
  liftedAt: string | null;
}

// 후기 신고일 때만 companionReview가 있다. 처리 전에는 resultNote, handledBy, handledAt이 null이다.
export interface AdminReportDetail extends AdminReportSummary {
  content: string;
  resultNote: string | null;
  handledBy: number | null;
  handledAt: string | null;
  basecamp: AdminReportBasecamp | null;
  companionReview: AdminReportReview | null;
  sanctionHistory: AdminSanctionHistoryItem[];
}

// 관리자가 새로 내릴 수 있는 제재 종류다. 임시 정지는 서버가 신고 접수 때 만든다.
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
  // 공공데이터 장소는 null이다.
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
