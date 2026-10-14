// 서버가 알림 종류를 정한 순서다. 이메일 수신 설정 화면도 이 순서로 보여 준다.
export type NotificationType =
  | 'BASECAMP_APPLIED'
  | 'BASECAMP_APPROVED'
  | 'BASECAMP_REJECTED'
  | 'BASECAMP_KICKED'
  | 'BASECAMP_CONFIRMED'
  | 'BASECAMP_CANCELED'
  | 'BASECAMP_COMPLETED'
  | 'BASECAMP_MEMBER_CHANGED'
  | 'MEMBER_REPORT_RESOLVED'
  | 'SANCTION_CONFIRMED'
  | 'PROGRAM_APPLICATION_CONFIRMED'
  | 'PROGRAM_APPLICATION_CANCELED'
  | 'PROGRAM_APPLICATION_EXPIRED';

export interface NotificationItem {
  notificationId: number;
  type: NotificationType;
  title: string;
  body: string;
  // 연결할 화면이 없으면 null이다. 값이 있으면 앱 안의 경로다.
  link: string | null;
  // 아직 읽지 않았으면 null이다.
  readAt: string | null;
  createdAt: string;
}

export interface NotificationSetting {
  type: NotificationType;
  emailEnabled: boolean;
}
