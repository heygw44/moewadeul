import type { NotificationType } from '../../api/types';

export const NOTIFICATION_LABELS: Record<NotificationType, string> = {
  BASECAMP_APPLIED: '내 베이스캠프에 합류 신청이 왔을 때',
  BASECAMP_APPROVED: '합류 신청이 승인됐을 때',
  BASECAMP_REJECTED: '합류 신청이 거절됐을 때',
  BASECAMP_KICKED: '캠프 리더가 나를 베이스캠프에서 내보냈을 때',
  BASECAMP_CONFIRMED: '베이스캠프가 확정됐을 때',
  BASECAMP_CANCELED: '베이스캠프가 취소됐을 때',
  BASECAMP_COMPLETED: '베이스캠프가 끝나 동행 후기를 쓸 수 있게 됐을 때',
  BASECAMP_MEMBER_CHANGED: '베이스캠프 멤버가 바뀌었을 때',
  MEMBER_REPORT_RESOLVED: '내 신고가 처리됐을 때',
  SANCTION_CONFIRMED: '나에게 제재가 확정됐을 때',
  PROGRAM_APPLICATION_CONFIRMED: '결제를 마쳐 행사 신청이 확정됐을 때',
  PROGRAM_APPLICATION_CANCELED: '행사 신청이 취소됐을 때',
  PROGRAM_APPLICATION_EXPIRED: '결제 기한이 지나 행사 신청이 만료됐을 때',
  PROGRAM_SEAT_RELEASED: '빈자리 알림을 신청한 행사에 자리가 났을 때',
};

export const NOTIFICATION_TYPES = Object.keys(NOTIFICATION_LABELS) as NotificationType[];
