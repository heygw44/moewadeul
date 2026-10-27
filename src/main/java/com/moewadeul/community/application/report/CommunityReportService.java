package com.moewadeul.community.application.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.community.domain.CommunityErrorCode;
import com.moewadeul.community.domain.comment.CommunityComment;
import com.moewadeul.community.domain.comment.CommunityCommentRepository;
import com.moewadeul.community.domain.post.CommunityPost;
import com.moewadeul.community.domain.post.CommunityPostRepository;
import com.moewadeul.community.domain.report.CommunityReport;
import com.moewadeul.community.domain.report.CommunityReportReason;
import com.moewadeul.community.domain.report.CommunityReportRepository;
import com.moewadeul.community.domain.report.CommunityReportTargetType;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * InnoDB의 기본 격리 수준(REPEATABLE READ)에서는 트랜잭션이 첫 일반 SELECT를 실행한 시점의 스냅샷을 읽는다. 그래서 4번째와 5번째 신고가 동시에
 * 들어오면 두 트랜잭션이 서로의 미커밋 행을 보지 못해 둘 다 4건으로 세고, 검토 대기로 바꾸는 일을 놓친다. 이를 막으려고 신고 트랜잭션은 첫
 * 쿼리로 대상 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 같은 대상의 신고가 줄을 서고, 뒤 트랜잭션은 앞 트랜잭션이 커밋한 뒤에
 * 스냅샷을 만들어 커밋된 신고를 모두 센다. 그래서 이 잠금 쿼리를 어떤 조회보다 앞에 둬야 한다.
 */
@Service
@RequiredArgsConstructor
public class CommunityReportService {

    private static final int PENDING_REVIEW_REPORT_THRESHOLD = 5;

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityReportRepository communityReportRepository;
    private final Clock clock;

    @Transactional
    public void reportPost(long memberId, long postId, CommunityReportCommand command) {
        // 잠금 쿼리가 이 트랜잭션의 첫 쿼리여야 한다. 클래스 설명을 본다.
        CommunityPost post = communityPostRepository
                .findByIdForUpdate(postId)
                .filter(CommunityPost::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        boolean reachedThreshold = saveReport(CommunityReportTargetType.POST, post.getId(), memberId, command);
        if (reachedThreshold) {
            post.markPendingReview(clock.instant());
        }
    }

    @Transactional
    public void reportComment(long memberId, long commentId, CommunityReportCommand command) {
        // 잠금 쿼리가 이 트랜잭션의 첫 쿼리여야 한다. 클래스 설명을 본다.
        CommunityComment comment = communityCommentRepository
                .findByIdForUpdate(commentId)
                .filter(CommunityComment::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        communityPostRepository
                .findById(comment.getPostId())
                .filter(CommunityPost::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        boolean reachedThreshold = saveReport(CommunityReportTargetType.COMMENT, comment.getId(), memberId, command);
        if (reachedThreshold) {
            comment.markPendingReview(clock.instant());
        }
    }

    private boolean saveReport(
            CommunityReportTargetType targetType, long targetId, long memberId, CommunityReportCommand command) {
        CommunityReportReason reason = command.reason();
        String content = command.content();
        if (content != null && content.length() > CommunityReport.CONTENT_MAX_LENGTH) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT, "신고 내용은 " + CommunityReport.CONTENT_MAX_LENGTH + "자 이하여야 해요.");
        }
        if (communityReportRepository.existsByTargetTypeAndTargetIdAndReporterId(targetType, targetId, memberId)) {
            throw alreadyReported();
        }
        Instant now = clock.instant();
        try {
            communityReportRepository.saveAndFlush(
                    CommunityReport.of(targetType, targetId, memberId, reason, content, now));
        } catch (DataIntegrityViolationException e) {
            throw alreadyReported();
        }
        return communityReportRepository.countByTargetTypeAndTargetIdAndReviewedAtIsNull(targetType, targetId)
                >= PENDING_REVIEW_REPORT_THRESHOLD;
    }

    private static BusinessException alreadyReported() {
        return new BusinessException(CommunityErrorCode.COMMUNITY_ALREADY_REPORTED);
    }
}
