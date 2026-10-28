export type ReportKind = 'MEMBER' | 'REVIEW';

export type ReportType =
  | 'NO_SHOW'
  | 'MONEY_REQUEST'
  | 'HARASSMENT_OR_THREAT'
  | 'OFFENSIVE_BEHAVIOR'
  | 'FAKE_PROFILE'
  | 'ILLEGAL_CAMPING_INDUCEMENT'
  | 'INAPPROPRIATE_REVIEW';

export type ReportStatus = 'RECEIVED' | 'IN_REVIEW' | 'ACTIONED' | 'DISMISSED';

export type SanctionType = 'WARNING' | 'SUSPEND_7D' | 'SUSPEND_30D' | 'PERMANENT' | 'TEMPORARY_72H';

export type SanctionStatus = 'ACTIVE' | 'LIFTED' | 'EXPIRED';

export interface MemberReportCreateRequest {
  targetMemberId: number;
  basecampId: number;
  kind: ReportKind;
  companionReviewId?: number;
  type: ReportType;
  content: string;
}

export interface MemberReportCreateResponse {
  reportId: number;
  status: ReportStatus;
}
