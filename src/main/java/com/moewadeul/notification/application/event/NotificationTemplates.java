package com.moewadeul.notification.application.event;

import com.moewadeul.common.event.BasecampEvents;
import com.moewadeul.common.event.ProgramEvents;
import com.moewadeul.common.event.TrustEvents;
import com.moewadeul.common.time.KoreaTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class NotificationTemplates {

    private static final DateTimeFormatter SUSPENSION_END_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(KoreaTime.ZONE);

    private NotificationTemplates() {}

    static List<NotificationDraft> applied(long basecampId, BasecampEvents.AppliedPayload payload) {
        return List.of(new NotificationDraft(
                payload.leaderId(),
                BasecampEvents.APPLIED,
                "새 합류 신청",
                "베이스캠프에 합류 신청이 들어왔어요. 신청자를 보고 승인하거나 거절해 주세요.",
                "/basecamps/" + basecampId + "/manage"));
    }

    static List<NotificationDraft> approved(BasecampEvents.DecisionPayload payload) {
        return List.of(new NotificationDraft(
                payload.applicantId(),
                BasecampEvents.APPROVED,
                "합류 승인",
                "합류 신청이 승인돼 베이스캠프 멤버가 됐어요.",
                basecampLink(payload.basecampId())));
    }

    static List<NotificationDraft> rejected(BasecampEvents.DecisionPayload payload) {
        return List.of(new NotificationDraft(
                payload.applicantId(),
                BasecampEvents.REJECTED,
                "합류 거절",
                "합류 신청이 거절됐어요.",
                basecampLink(payload.basecampId())));
    }

    static List<NotificationDraft> kicked(BasecampEvents.KickedPayload payload) {
        String body = "캠프 리더가 " + kickReasonClause(payload.reason()) + " 베이스캠프에서 내보냈어요. 같은 베이스캠프에는 다시 신청할 수 없어요.";
        return List.of(new NotificationDraft(
                payload.memberId(), BasecampEvents.KICKED, "베이스캠프 내보내기", body, basecampLink(payload.basecampId())));
    }

    static List<NotificationDraft> confirmed(BasecampEvents.MembersPayload payload) {
        return toMembers(
                payload,
                BasecampEvents.CONFIRMED,
                "베이스캠프 확정",
                "베이스캠프가 확정됐어요. 안전 수칙: 일정을 가족이나 지인과 공유해 주세요. 되도록 일행과 떨어지지 말고 흔적을 남기지 마세요.",
                basecampLink(payload.basecampId()));
    }

    static List<NotificationDraft> canceled(BasecampEvents.MembersPayload payload) {
        return toMembers(
                payload, BasecampEvents.CANCELED, "베이스캠프 취소", "합류한 베이스캠프가 취소됐어요.", basecampLink(payload.basecampId()));
    }

    static List<NotificationDraft> completed(BasecampEvents.MembersPayload payload) {
        return toMembers(
                payload,
                BasecampEvents.COMPLETED,
                "베이스캠프 완료",
                "베이스캠프가 끝났어요. 14일 안에 함께한 멤버에게 동행 후기를 남겨 주세요.",
                "/me/companion-reviews");
    }

    static List<NotificationDraft> memberChanged(BasecampEvents.MembersPayload payload) {
        return toMembers(
                payload,
                BasecampEvents.MEMBER_CHANGED,
                "멤버 변경",
                "멤버가 바뀌어 베이스캠프 구성이 달라졌어요.",
                basecampLink(payload.basecampId()));
    }

    static List<NotificationDraft> reportResolved(TrustEvents.ReportResolvedPayload payload) {
        String body = switch (payload.result()) {
            case "ACTIONED" -> "신고한 내용을 검토해 조치했어요.";
            case "DISMISSED" -> "신고한 내용을 검토했지만 조치할 일은 아니라고 판단했어요.";
            default -> throw new IllegalArgumentException("알 수 없는 신고 처리 결과입니다: " + payload.result());
        };
        return List.of(
                new NotificationDraft(payload.reporterId(), TrustEvents.REPORT_RESOLVED, "신고 처리 완료", body, null));
    }

    static List<NotificationDraft> sanctionConfirmed(TrustEvents.SanctionConfirmedPayload payload) {
        return List.of(new NotificationDraft(
                payload.memberId(), TrustEvents.SANCTION_CONFIRMED, "이용 제재 안내", sanctionBody(payload), null));
    }

    static List<NotificationDraft> programApplicationConfirmed(ProgramEvents.ApplicationPayload payload) {
        return List.of(new NotificationDraft(
                payload.memberId(),
                ProgramEvents.APPLICATION_CONFIRMED,
                "행사 신청 확정",
                "결제를 마쳐 행사 신청이 확정됐어요.",
                programLink(payload.programId())));
    }

    static List<NotificationDraft> programApplicationCanceled(ProgramEvents.CanceledPayload payload) {
        String body = switch (payload.reason()) {
            case "USER" -> "행사 신청을 취소했어요.";
            case "PROGRAM_CANCELED" -> "행사가 취소돼 신청도 취소됐어요.";
            case "SANCTIONED" -> "이용 제재로 행사 신청이 취소됐어요.";
            default -> throw new IllegalArgumentException("알 수 없는 행사 신청 취소 사유입니다: " + payload.reason());
        };
        if (payload.refunded()) {
            body += " 결제한 금액은 환불돼요.";
        }
        return List.of(new NotificationDraft(
                payload.memberId(),
                ProgramEvents.APPLICATION_CANCELED,
                "행사 신청 취소",
                body,
                programLink(payload.programId())));
    }

    static List<NotificationDraft> programApplicationExpired(ProgramEvents.ApplicationPayload payload) {
        return List.of(new NotificationDraft(
                payload.memberId(),
                ProgramEvents.APPLICATION_EXPIRED,
                "행사 신청 만료",
                "결제 기한이 지나 행사 신청이 취소됐어요.",
                programLink(payload.programId())));
    }

    static List<NotificationDraft> programSeatReleased(ProgramEvents.SeatReleasedPayload payload) {
        return payload.memberIds().stream()
                .map(memberId -> new NotificationDraft(
                        memberId,
                        ProgramEvents.SEAT_RELEASED,
                        "행사 빈자리 알림",
                        "빈자리 알림을 신청한 행사에 자리가 생겼어요. 먼저 신청한 회원이 자리를 가져가요.",
                        programLink(payload.programId())))
                .toList();
    }

    private static String sanctionBody(TrustEvents.SanctionConfirmedPayload payload) {
        return switch (payload.sanctionType()) {
            case "WARNING" -> "경고를 받았어요.";
            case "PERMANENT" -> "이용이 영구 정지됐어요.";
            case "SUSPEND_7D", "SUSPEND_30D" -> {
                if (payload.endsAt() == null) {
                    throw new IllegalArgumentException("정지 종료 시각이 없습니다. sanctionId=" + payload.sanctionId());
                }
                yield SUSPENSION_END_FORMAT.format(payload.endsAt()) + "까지 이용이 정지돼요.";
            }
            default -> throw new IllegalArgumentException("알 수 없는 제재 종류입니다: " + payload.sanctionType());
        };
    }

    private static String kickReasonClause(String reason) {
        return switch (reason) {
            case "NO_CONTACT" -> "연락이 닿지 않아";
            case "CONDITION_MISMATCH" -> "일정이나 조건이 맞지 않아";
            case "INAPPROPRIATE_BEHAVIOR" -> "부적절한 행동 때문에";
            case "OTHER" -> "기타 이유로";
            default -> throw new IllegalArgumentException("알 수 없는 강퇴 사유입니다: " + reason);
        };
    }

    private static List<NotificationDraft> toMembers(
            BasecampEvents.MembersPayload payload, String type, String title, String body, String link) {
        return payload.memberIds().stream()
                .map(memberId -> new NotificationDraft(memberId, type, title, body, link))
                .toList();
    }

    private static String programLink(long programId) {
        return "/programs/" + programId;
    }

    private static String basecampLink(long basecampId) {
        return "/basecamps/" + basecampId;
    }
}
