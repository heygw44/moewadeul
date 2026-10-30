package com.moewadeul.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moewadeul.admin.application.audit.AuditLogQueryService;
import com.moewadeul.admin.infra.audit.AdminAuditLogMapper;
import com.moewadeul.common.audit.AdminAuditRecorder;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.common.patch.PatchField;
import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.community.application.moderation.AdminCommunityQueryService;
import com.moewadeul.community.domain.post.CommunityPostStatus;
import com.moewadeul.community.infra.moderation.AdminCommunityMapper;
import com.moewadeul.member.application.profile.MyInfoService;
import com.moewadeul.member.application.profile.MyInfoUpdateCommand;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.MemberRole;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.memory.application.MemoryImageReader;
import com.moewadeul.memory.application.moderation.AdminMemoryQueryService;
import com.moewadeul.memory.domain.MemoryStatus;
import com.moewadeul.memory.infra.moderation.AdminMemoryMapper;
import com.moewadeul.program.application.catalog.ProgramAdminService;
import com.moewadeul.program.application.catalog.ProgramApplicantQuery;
import com.moewadeul.program.application.catalog.ProgramQueryService;
import com.moewadeul.program.domain.PaymentRepository;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramRepository;
import com.moewadeul.program.infra.catalog.ProgramQueryMapper;
import com.moewadeul.spot.application.ActiveSpotChecker;
import com.moewadeul.spot.application.bakji.BakjiFeedbackService;
import com.moewadeul.spot.application.bakji.BakjiProblemReportCommand;
import com.moewadeul.spot.application.moderation.AdminSpotQueryService;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.SpotRepository;
import com.moewadeul.spot.domain.SpotStatus;
import com.moewadeul.spot.domain.bakji.BakjiConfirmationRepository;
import com.moewadeul.spot.domain.bakji.BakjiReportRepository;
import com.moewadeul.spot.infra.moderation.AdminSpotMapper;
import com.moewadeul.trust.application.report.AdminMemberReportQueryService;
import com.moewadeul.trust.infra.report.AdminMemberReportMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class EnumInputServicesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
    private static final String ADMIN_STATUS_MESSAGE = "status는 PENDING_REVIEW 또는 HIDDEN이어야 해요.";
    private static final String BAKJI_REASON_MESSAGE = "신고 사유는 ILLEGAL_AREA, CLOSED, FALSE_INFO 중 하나여야 해요.";

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "DELETED", "hidden", ""})
    @DisplayName("[F-21] 관리자 장소 목록은 PENDING_REVIEW와 HIDDEN 밖의 status를 INVALID_INPUT으로 거절한다")
    void adminSpotListRejectsOtherStatus(String status) {
        AdminSpotQueryService service = new AdminSpotQueryService(mock(AdminSpotMapper.class));

        assertThatThrownBy(() -> service.list(status, 0, 20))
                .satisfies(e -> assertInvalidInput(e, ADMIN_STATUS_MESSAGE));
    }

    @Test
    @DisplayName("[F-21] 관리자 장소 목록은 status를 생략하면 PENDING_REVIEW로, HIDDEN이면 HIDDEN으로 조회한다")
    void adminSpotListDefaultsToPendingReview() {
        AdminSpotMapper mapper = mock(AdminSpotMapper.class);
        AdminSpotQueryService service = new AdminSpotQueryService(mapper);

        service.list(null, 0, 20);
        service.list("HIDDEN", 0, 20);

        verify(mapper).selectPage(SpotStatus.PENDING_REVIEW, 0L, 21);
        verify(mapper).selectPage(SpotStatus.HIDDEN, 0L, 21);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "DELETED", "hidden", ""})
    @DisplayName("[F-33] 관리자 기록 목록은 PENDING_REVIEW와 HIDDEN 밖의 status를 INVALID_INPUT으로 거절한다")
    void adminMemoryListRejectsOtherStatus(String status) {
        AdminMemoryQueryService service =
                new AdminMemoryQueryService(mock(AdminMemoryMapper.class), mock(MemoryImageReader.class));

        assertThatThrownBy(() -> service.list(status, 0, 20))
                .satisfies(e -> assertInvalidInput(e, ADMIN_STATUS_MESSAGE));
    }

    @Test
    @DisplayName("[F-33] 관리자 기록 목록은 status를 생략하면 PENDING_REVIEW로 조회한다")
    void adminMemoryListDefaultsToPendingReview() {
        AdminMemoryMapper mapper = mock(AdminMemoryMapper.class);
        AdminMemoryQueryService service = new AdminMemoryQueryService(mapper, mock(MemoryImageReader.class));

        service.list(null, 0, 20);

        verify(mapper).selectMemories(MemoryStatus.PENDING_REVIEW, 0L, 21);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "sanction", ""})
    @DisplayName("[F-21] 감사 로그 조회는 모르는 targetType을 빈 페이지가 아니라 INVALID_INPUT으로 거절한다")
    void auditLogsRejectUnknownTargetType(String targetType) {
        AdminAuditLogMapper mapper = mock(AdminAuditLogMapper.class);
        AuditLogQueryService service =
                new AuditLogQueryService(mapper, JsonMapper.builder().build());

        assertThatThrownBy(() -> service.findLogs(null, targetType, null, null, 0, 20))
                .satisfies(e -> assertInvalidInput(
                        e, "대상 종류는 " + EnumParser.names(AdminAuditTargetType.class) + " 중 하나여야 해요."));
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("[F-21] 감사 로그 조회는 targetType 이름을 enum으로 바꿔 매퍼에 넘긴다")
    void auditLogsPassTargetTypeToMapper() {
        AdminAuditLogMapper mapper = mock(AdminAuditLogMapper.class);
        AuditLogQueryService service =
                new AuditLogQueryService(mapper, JsonMapper.builder().build());

        service.findLogs(null, "COMMUNITY_POST", null, null, 0, 20);

        verify(mapper).selectLogs(null, AdminAuditTargetType.COMMUNITY_POST, null, null, 0L, 21);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "DELETED", "hidden", ""})
    @DisplayName("관리자 커뮤니티 글과 댓글 목록은 PENDING_REVIEW와 HIDDEN 밖의 status를 INVALID_INPUT으로 거절한다")
    void adminCommunityListsRejectOtherStatus(String status) {
        AdminCommunityQueryService service = new AdminCommunityQueryService(mock(AdminCommunityMapper.class));

        assertThatThrownBy(() -> service.listPosts(status, 0, 20))
                .satisfies(e -> assertInvalidInput(e, ADMIN_STATUS_MESSAGE));
        assertThatThrownBy(() -> service.listComments(status, 0, 20))
                .satisfies(e -> assertInvalidInput(e, ADMIN_STATUS_MESSAGE));
    }

    @Test
    @DisplayName("관리자 커뮤니티 글 목록은 status를 생략하면 PENDING_REVIEW로 조회한다")
    void adminCommunityPostListDefaultsToPendingReview() {
        AdminCommunityMapper mapper = mock(AdminCommunityMapper.class);

        new AdminCommunityQueryService(mapper).listPosts(null, 0, 20);

        verify(mapper).selectPosts(CommunityPostStatus.PENDING_REVIEW, 0L, 21);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "received", ""})
    @DisplayName("관리자 회원 신고 목록은 알 수 없는 status를 INVALID_INPUT으로 거절한다")
    void adminMemberReportListRejectsUnknownStatus(String status) {
        AdminMemberReportQueryService service = new AdminMemberReportQueryService(mock(AdminMemberReportMapper.class));

        assertThatThrownBy(() -> service.list(status, null, 0, 20))
                .satisfies(e -> assertInvalidInput(e, "알 수 없는 신고 상태예요."));
    }

    @Test
    @DisplayName("관리자 회원 신고 목록은 status를 생략하면 상태 조건 없이 조회한다")
    void adminMemberReportListWithoutStatusHasNoFilter() {
        AdminMemberReportMapper mapper = mock(AdminMemberReportMapper.class);

        new AdminMemberReportQueryService(mapper).list(null, null, 0, 20);

        verify(mapper).selectList(null, null, 0L, 21);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "confirmed", ""})
    @DisplayName("행사 신청자 목록은 알 수 없는 신청 상태를 INVALID_INPUT으로 거절한다")
    void programApplicantsRejectsUnknownStatus(String status) {
        ProgramAdminService service = programAdminService(mock(ProgramQueryMapper.class));

        assertThatThrownBy(() -> service.applications(1L, new ProgramApplicantQuery(status, 0, 20)))
                .satisfies(e -> assertInvalidInput(e, "status: 신청 상태가 맞지 않아요."));
    }

    @Test
    @DisplayName("행사 신청자 목록은 status를 생략하면 상태 조건 없이, 허용 값이면 그 상태로 조회한다")
    void programApplicantsMapsStatus() {
        ProgramQueryMapper mapper = mock(ProgramQueryMapper.class);
        ProgramAdminService service = programAdminService(mapper);

        service.applications(1L, new ProgramApplicantQuery(null, 0, 20));
        service.applications(1L, new ProgramApplicantQuery("CONFIRMED", 0, 20));

        verify(mapper).selectApplicants(1L, null, 0L, 21);
        verify(mapper).selectApplicants(1L, ProgramApplicationStatus.CONFIRMED, 0L, 21);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "spam", "UNKNOWN"})
    @DisplayName("박지 문제 신고는 허용하지 않는 사유와 null을 허용 목록을 담은 INVALID_INPUT으로 거절한다")
    void bakjiReportRejectsUnknownReason(String reason) {
        SpotRepository spotRepository = mock(SpotRepository.class);
        Spot spot = mock(Spot.class);
        when(spot.isActiveBakji()).thenReturn(true);
        when(spotRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(spot));
        BakjiFeedbackService service = new BakjiFeedbackService(
                spotRepository, mock(BakjiConfirmationRepository.class), mock(BakjiReportRepository.class), CLOCK);

        assertThatThrownBy(() -> service.reportProblem(1L, 5L, new BakjiProblemReportCommand(reason, null)))
                .satisfies(e -> assertInvalidInput(e, BAKJI_REASON_MESSAGE));
        assertThatThrownBy(() -> service.reportProblem(1L, 5L, new BakjiProblemReportCommand(null, null)))
                .satisfies(e -> assertInvalidInput(e, BAKJI_REASON_MESSAGE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "twenties", "TEENS"})
    @DisplayName("내 정보 수정은 허용하지 않는 연령대를 INVALID_INPUT으로 거절한다")
    void myInfoRejectsUnknownAgeGroup(String value) {
        MyInfoService service = myInfoService(mock(Member.class));
        MyInfoUpdateCommand command =
                new MyInfoUpdateCommand(PatchField.absent(), PatchField.of(value), PatchField.absent());

        assertThatThrownBy(() -> service.update(1L, command))
                .satisfies(e ->
                        assertInvalidInput(e, "연령대는 TWENTIES, THIRTIES, FORTIES, FIFTIES, SIXTIES_PLUS 중 하나여야 해요."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "female", "OTHER"})
    @DisplayName("내 정보 수정은 허용하지 않는 성별을 INVALID_INPUT으로 거절한다")
    void myInfoRejectsUnknownGender(String value) {
        MyInfoService service = myInfoService(mock(Member.class));
        MyInfoUpdateCommand command =
                new MyInfoUpdateCommand(PatchField.absent(), PatchField.absent(), PatchField.of(value));

        assertThatThrownBy(() -> service.update(1L, command))
                .satisfies(e -> assertInvalidInput(e, "성별은 FEMALE, MALE 중 하나여야 해요."));
    }

    @Test
    @DisplayName("내 정보 수정은 허용 목록의 연령대와 성별 이름을 그대로 반영한다")
    void myInfoAppliesAllowedNames() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getRole()).thenReturn(MemberRole.USER);
        MyInfoUpdateCommand command =
                new MyInfoUpdateCommand(PatchField.absent(), PatchField.of("SIXTIES_PLUS"), PatchField.of("FEMALE"));

        myInfoService(member).update(1L, command);

        verify(member).changeSelfAgeGroup(AgeGroup.SIXTIES_PLUS, CLOCK.instant());
        verify(member).changeSelfGender(Gender.FEMALE, CLOCK.instant());
    }

    @Test
    @DisplayName("내 정보 수정은 연령대와 성별에 null을 주면 값을 지운다")
    void myInfoClearsOnNull() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getRole()).thenReturn(MemberRole.USER);
        MyInfoService service = myInfoService(member);
        MyInfoUpdateCommand command =
                new MyInfoUpdateCommand(PatchField.absent(), PatchField.of(null), PatchField.of(null));

        assertThatCode(() -> service.update(1L, command)).doesNotThrowAnyException();

        verify(member).changeSelfAgeGroup(null, CLOCK.instant());
        verify(member).changeSelfGender(null, CLOCK.instant());
    }

    private static ProgramAdminService programAdminService(ProgramQueryMapper mapper) {
        ProgramRepository programRepository = mock(ProgramRepository.class);
        when(programRepository.findById(1L)).thenReturn(Optional.of(mock(Program.class)));
        return new ProgramAdminService(
                programRepository,
                mock(ProgramApplicationRepository.class),
                mock(PaymentRepository.class),
                mapper,
                mock(ProgramQueryService.class),
                mock(ActiveSpotChecker.class),
                mock(AdminAuditRecorder.class),
                mock(OutboxEventRecorder.class),
                CLOCK);
    }

    private static MyInfoService myInfoService(Member member) {
        MemberRepository memberRepository = mock(MemberRepository.class);
        when(memberRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(member));
        return new MyInfoService(memberRepository, CLOCK);
    }

    private static void assertInvalidInput(Throwable thrown, String message) {
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
            assertThat(e.getMessage()).isEqualTo(message);
        });
    }
}
