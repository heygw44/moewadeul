package com.moewadeul.program.application.catalog;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.paging.PageSlice;
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
        List<ProgramListRow> rows = programQueryMapper.selectList(
                query.phase(), now, PageSlice.offsetOf(query.page(), query.size()), PageSlice.fetchSize(query.size()));
        PageSlice<ProgramSummary> slice =
                PageSlice.of(rows, query.page(), query.size()).map(row -> toSummary(row, now));
        return new ProgramPage(slice, programQueryMapper.selectNextApplyOpenAt(now));
    }

    @Transactional(readOnly = true)
    public ProgramDetail detail(long programId, Long viewerId) {
        Instant now = clock.instant();
        ProgramDetailRow row = programQueryMapper.selectDetail(programId, now);
        if (row == null) {
            throw BusinessException.notFound();
        }
        return toDetail(row, now, findMyApplication(programId, viewerId));
    }

    @Transactional(readOnly = true)
    public PageSlice<MyProgramApplicationItem> listMine(long memberId, MyProgramApplicationQuery query) {
        Instant now = clock.instant();
        List<MyProgramApplicationRow> rows = programQueryMapper.selectMyApplications(
                memberId,
                query.status(),
                PageSlice.offsetOf(query.page(), query.size()),
                PageSlice.fetchSize(query.size()));
        return PageSlice.of(rows, query.page(), query.size()).map(row -> toMyItem(row, now));
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
                phase);
    }

    private static MyProgramApplicationItem toMyItem(MyProgramApplicationRow row, Instant now) {
        ProgramPhase phase = ProgramPhase.of(row.programStatus(), row.applyOpenAt(), row.applyCloseAt(), now);
        return new MyProgramApplicationItem(
                row.applicationId(),
                row.status(),
                row.paymentDueAt(),
                row.confirmedAt(),
                row.canceledAt(),
                row.cancelReason(),
                row.createdAt(),
                new MyProgramApplicationItem.Program(
                        row.programId(),
                        row.title(),
                        row.locationText(),
                        row.startAt(),
                        row.endAt(),
                        row.fee(),
                        phase));
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
