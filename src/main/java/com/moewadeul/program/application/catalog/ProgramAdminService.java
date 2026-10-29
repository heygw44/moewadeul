package com.moewadeul.program.application.catalog;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditRecorder;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.program.application.ProgramApplicationEvents;
import com.moewadeul.program.domain.PaymentRepository;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramApplication;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramCancelReason;
import com.moewadeul.program.domain.ProgramChange;
import com.moewadeul.program.domain.ProgramDetails;
import com.moewadeul.program.domain.ProgramRepository;
import com.moewadeul.program.domain.ProgramRevision;
import com.moewadeul.program.infra.catalog.ProgramApplicantRow;
import com.moewadeul.program.infra.catalog.ProgramQueryMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramAdminService {

    private final ProgramRepository programRepository;
    private final ProgramApplicationRepository programApplicationRepository;
    private final PaymentRepository paymentRepository;
    private final ProgramQueryMapper programQueryMapper;
    private final ProgramQueryService programQueryService;
    private final AdminAuditRecorder adminAuditRecorder;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    @Transactional
    public long create(long adminId, ProgramCreateCommand command) {
        Program program = newProgram(adminId, command, clock.instant());
        requireVisibleSpot(command.spotId());
        Program saved = programRepository.saveAndFlush(program);
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.PROGRAM_CREATE,
                AdminAuditTargetType.PROGRAM,
                saved.getId(),
                new ProgramAuditDetails.Create(saved.getCapacity(), saved.isOvernight()));
        log.info("program created programId={} adminId={}", saved.getId(), adminId);
        return saved.getId();
    }

    @Transactional
    public ProgramDetail revise(long adminId, long programId, ProgramReviseCommand command) {
        Program program = lockProgram(programId);
        if (command.spotId().present()) {
            requireVisibleSpot(command.spotId().value());
        }
        boolean applicationsExist = programApplicationRepository.existsByProgramId(programId);
        ProgramChange change = revise(program, command, clock.instant(), applicationsExist);
        programRepository.saveAndFlush(program);
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.PROGRAM_UPDATE,
                AdminAuditTargetType.PROGRAM,
                programId,
                updateDetail(change));
        log.info("program revised programId={} adminId={}", programId, adminId);
        return programQueryService.detail(programId, null);
    }

    @Transactional
    public ProgramCancelResult cancel(long adminId, long programId) {
        Program program = lockProgram(programId);
        Instant now = clock.instant();
        String fromStatus = program.getStatus().name();
        program.cancel(now);
        List<ProgramApplication> activeApplications =
                programApplicationRepository.findActiveByProgramForUpdate(programId);
        Set<Long> paidApplicationIds = new HashSet<>(paymentRepository.findPaidApplicationIdsByProgram(programId));
        int canceledApplicationCount = programApplicationRepository.cancelActiveByProgram(
                programId, ProgramCancelReason.PROGRAM_CANCELED, now);
        int refundedPaymentCount = paymentRepository.refundPaidByProgram(programId, now);
        recordCanceledEvents(programId, activeApplications, paidApplicationIds);
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.PROGRAM_CANCEL,
                AdminAuditTargetType.PROGRAM,
                programId,
                new ProgramAuditDetails.Cancel(
                        fromStatus, program.getStatus().name(), canceledApplicationCount, refundedPaymentCount));
        log.info(
                "program canceled programId={} adminId={} canceledApplicationCount={} refundedPaymentCount={}",
                programId,
                adminId,
                canceledApplicationCount,
                refundedPaymentCount);
        return new ProgramCancelResult(programId, program.getStatus().name(), canceledApplicationCount);
    }

    private void recordCanceledEvents(
            long programId, List<ProgramApplication> applications, Set<Long> paidApplicationIds) {
        for (ProgramApplication application : applications) {
            long applicationId = application.getId();
            outboxEventRecorder.record(
                    ProgramApplicationEvents.CANCELED_EVENT_TYPE,
                    ProgramApplicationEvents.AGGREGATE_TYPE,
                    applicationId,
                    new ProgramApplicationEvents.CanceledPayload(
                            applicationId,
                            application.getMemberId(),
                            programId,
                            ProgramCancelReason.PROGRAM_CANCELED.name(),
                            paidApplicationIds.contains(applicationId)));
        }
    }

    @Transactional(readOnly = true)
    public ProgramApplicantPage applications(long programId, ProgramApplicantQuery query) {
        programRepository.findById(programId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        ProgramApplicationStatus status =
                EnumParser.parseNullable(ProgramApplicationStatus.class, query.status(), "status: 신청 상태가 맞지 않아요.");
        long offset = (long) query.page() * query.size();
        List<ProgramApplicantRow> rows =
                programQueryMapper.selectApplicants(programId, status, offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<ProgramApplicantItem> content = rows.stream()
                .limit(query.size())
                .map(ProgramAdminService::toItem)
                .toList();
        return new ProgramApplicantPage(content, query.page(), query.size(), hasNext);
    }

    private Program lockProgram(long programId) {
        return programRepository
                .findByIdForUpdate(programId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private void requireVisibleSpot(Long spotId) {
        if (spotId != null && !programQueryMapper.existsActiveSpot(spotId)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "spotId: 지도에 보이는 장소만 연결할 수 있어요.");
        }
    }

    private static Program newProgram(long adminId, ProgramCreateCommand command, Instant now) {
        int paymentDeadlineMinutes = command.paymentDeadlineMinutes() == null
                ? Program.DEFAULT_PAYMENT_DEADLINE_MINUTES
                : command.paymentDeadlineMinutes();
        ProgramDetails details = new ProgramDetails(
                command.title(),
                command.description(),
                command.spotId(),
                command.locationText(),
                command.startAt(),
                command.endAt(),
                command.capacity(),
                command.fee(),
                command.applyOpenAt(),
                command.applyCloseAt(),
                paymentDeadlineMinutes,
                command.overnight());
        try {
            return Program.create(adminId, details, now);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    private static ProgramChange revise(
            Program program, ProgramReviseCommand command, Instant now, boolean applicationsExist) {
        ProgramRevision revision = new ProgramRevision(
                command.title(),
                command.description(),
                command.spotId(),
                command.locationText(),
                command.startAt(),
                command.endAt(),
                command.capacity(),
                command.fee(),
                command.applyOpenAt(),
                command.applyCloseAt(),
                command.paymentDeadlineMinutes(),
                command.overnight());
        try {
            return program.revise(revision, now, applicationsExist);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    private static ProgramAuditDetails.Update updateDetail(ProgramChange change) {
        if (!change.isCapacityChanged()) {
            return new ProgramAuditDetails.Update(change.changedFields(), null, null);
        }
        return new ProgramAuditDetails.Update(change.changedFields(), change.capacityBefore(), change.capacityAfter());
    }

    private static ProgramApplicantItem toItem(ProgramApplicantRow row) {
        return new ProgramApplicantItem(
                row.applicationId(),
                new ProgramApplicantItem.Applicant(row.memberId(), row.nickname()),
                row.status().name(),
                row.paymentDueAt(),
                row.confirmedAt(),
                row.canceledAt(),
                row.cancelReason() == null ? null : row.cancelReason().name(),
                row.reviewRequestedAt(),
                row.createdAt());
    }
}
