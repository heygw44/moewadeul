package com.moewadeul.notification.application.event;

import java.time.Instant;
import java.util.List;

final class NotificationPayloads {

    private NotificationPayloads() {}

    record Applied(long applicationId, long applicantId, long leaderId) {}

    record Decision(long applicationId, long applicantId, long basecampId) {}

    record Kicked(long basecampId, long memberId, String reason) {}

    record BasecampMembers(long basecampId, List<Long> memberIds) {}

    record ReportResolved(long reportId, long reporterId, String result) {}

    record SanctionConfirmed(long memberId, long sanctionId, String sanctionType, Instant endsAt) {}

    record ProgramApplicationConfirmed(long applicationId, long memberId, long programId) {}

    record ProgramApplicationCanceled(
            long applicationId, long memberId, long programId, String reason, boolean refunded) {}

    record ProgramApplicationExpired(long applicationId, long memberId, long programId) {}

    record ProgramSeatReleased(long programId, List<Long> memberIds) {}
}
