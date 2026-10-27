package com.moewadeul.program.application.catalog;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.program.domain.ProgramPhase;
import com.moewadeul.program.infra.catalog.MyApplicationRow;
import com.moewadeul.program.infra.catalog.MyProgramApplicationRow;
import com.moewadeul.program.infra.catalog.ProgramDetailRow;
import com.moewadeul.program.infra.catalog.ProgramListRow;
import com.moewadeul.program.infra.catalog.ProgramQueryMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProgramQueryService {

    private final ProgramQueryMapper programQueryMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProgramPage list(ProgramListQuery query) {
        Instant now = clock.instant();
        String phase = query.phase() == null ? null : query.phase().name();
        long offset = (long) query.page() * query.size();
        List<ProgramListRow> rows = programQueryMapper.selectList(phase, now, offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<ProgramSummary> content = rows.stream()
                .limit(query.size())
                .map(row -> toSummary(row, now))
                .toList();
        Instant nextApplyOpenAt = programQueryMapper.selectNextApplyOpenAt(now);
        return new ProgramPage(content, query.page(), query.size(), hasNext, nextApplyOpenAt);
    }

    @Transactional(readOnly = true)
    public ProgramDetail detail(long programId, Long viewerId) {
        Instant now = clock.instant();
        ProgramDetailRow row = programQueryMapper.selectDetail(programId, now);
        if (row == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return toDetail(row, now, findMyApplication(programId, viewerId));
    }

    @Transactional(readOnly = true)
    public MyProgramApplicationPage listMine(long memberId, MyProgramApplicationQuery query) {
        Instant now = clock.instant();
        long offset = (long) query.page() * query.size();
        List<MyProgramApplicationRow> rows =
                programQueryMapper.selectMyApplications(memberId, query.status(), offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<MyProgramApplicationItem> content =
                rows.stream().limit(query.size()).map(row -> toMyItem(row, now)).toList();
        return new MyProgramApplicationPage(content, query.page(), query.size(), hasNext);
    }

    private ProgramDetail.MyApplication findMyApplication(long programId, Long viewerId) {
        if (viewerId == null) {
            return null;
        }
        MyApplicationRow row = programQueryMapper.selectLatestApplication(programId, viewerId);
        if (row == null) {
            return null;
        }
        return new ProgramDetail.MyApplication(row.applicationId(), row.status().name(), row.paymentDueAt());
    }

    private static ProgramSummary toSummary(ProgramListRow row, Instant now) {
        ProgramPhase phase = ProgramPhase.of(row.status(), row.applyOpenAt(), row.applyCloseAt(), now);
        return new ProgramSummary(
                row.programId(),
                row.title(),
                row.locationText(),
                row.spotId(),
                row.startAt(),
                row.endAt(),
                row.applyOpenAt(),
                row.applyCloseAt(),
                row.capacity(),
                row.remainingSeats(),
                row.fee(),
                row.overnight(),
                phase.name());
    }

    private static MyProgramApplicationItem toMyItem(MyProgramApplicationRow row, Instant now) {
        ProgramPhase phase = ProgramPhase.of(row.programStatus(), row.applyOpenAt(), row.applyCloseAt(), now);
        return new MyProgramApplicationItem(
                row.applicationId(),
                row.status().name(),
                row.paymentDueAt(),
                row.confirmedAt(),
                row.canceledAt(),
                row.cancelReason() == null ? null : row.cancelReason().name(),
                row.createdAt(),
                new MyProgramApplicationItem.Program(
                        row.programId(),
                        row.title(),
                        row.locationText(),
                        row.startAt(),
                        row.endAt(),
                        row.fee(),
                        phase.name()));
    }

    private static ProgramDetail toDetail(ProgramDetailRow row, Instant now, ProgramDetail.MyApplication mine) {
        ProgramPhase phase = ProgramPhase.of(row.status(), row.applyOpenAt(), row.applyCloseAt(), now);
        return new ProgramDetail(
                row.programId(),
                row.title(),
                row.description(),
                row.locationText(),
                row.spotId(),
                row.startAt(),
                row.endAt(),
                row.applyOpenAt(),
                row.applyCloseAt(),
                row.capacity(),
                row.remainingSeats(),
                row.fee(),
                row.paymentDeadlineMinutes(),
                row.overnight(),
                phase.name(),
                mine);
    }
}
