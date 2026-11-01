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
  | 'PROGRAM_APPLICATION_EXPIRED'
  | 'PROGRAM_SEAT_RELEASED';

export interface NotificationItem {
  notificationId: number;
  type: NotificationType;
  title: string;
  body: string;
  link: string | null;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationSetting {
  type: NotificationType;
  emailEnabled: boolean;
}
