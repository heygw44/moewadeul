package com.moewadeul.community.application.post;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.web.PatchField;
import com.moewadeul.community.domain.post.CommunityPost;
import com.moewadeul.community.domain.post.CommunityPostRepository;
import com.moewadeul.spot.application.ActiveSpotChecker;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityPostWriter {

    private final ActiveSpotChecker activeSpotChecker;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostItemReader communityPostItemReader;
    private final CommunityImageAttachments communityImageAttachments;
    private final Clock clock;

    @Transactional
    public long write(long memberId, CommunityPostWriteCommand command) {
        requireValidTitle(command.title());
        requireValidContent(command.content());
        if (command.spotId() != null) {
            requireLinkable(command.spotId());
        }
        CommunityPost post =
                CommunityPost.write(memberId, command.title(), command.content(), command.spotId(), clock.instant());
        long postId = communityPostRepository.saveAndFlush(post).getId();
        if (!command.imageIds().isEmpty()) {
            communityImageAttachments.replace(memberId, postId, command.imageIds());
        }
        return postId;
    }

    @Transactional
    public CommunityPostItem revise(long memberId, long postId, CommunityPostReviseCommand command) {
        CommunityPost post = lockOwnActivePost(memberId, postId);
        Instant now = clock.instant();
        applyChanges(post, command, now);
        if (command.imageIds().present()
                && communityImageAttachments.replace(
                        memberId, postId, command.imageIds().value())) {
            post.markEdited(now);
        }
        // 고친 내용을 MyBatis로 읽기 전에 DB에 반영한다.
        communityPostRepository.flush();
        return communityPostItemReader
                .findDetail(postId, memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    @Transactional
    public void delete(long memberId, long postId) {
        lockOwnActivePost(memberId, postId).delete(clock.instant());
    }

    @Transactional(readOnly = true)
    public void requireOwnActivePost(long memberId, long postId) {
        loadOwnActivePost(memberId, postId);
    }

    private CommunityPost lockOwnActivePost(long memberId, long postId) {
        return requireOwnActive(communityPostRepository.findByIdForUpdate(postId), memberId);
    }

    private CommunityPost loadOwnActivePost(long memberId, long postId) {
        return requireOwnActive(communityPostRepository.findById(postId), memberId);
    }

    private static CommunityPost requireOwnActive(Optional<CommunityPost> found, long memberId) {
        CommunityPost post = found.filter(CommunityPost::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        post.requireWrittenBy(memberId);
        return post;
    }

    private void applyChanges(CommunityPost post, CommunityPostReviseCommand command, Instant now) {
        requireNotCleared(command.title(), "제목");
        requireNotCleared(command.content(), "본문");
        if (command.title().present()) {
            requireValidTitle(command.title().value());
        }
        if (command.content().present()) {
            requireValidContent(command.content().value());
        }
        Long newSpotId = command.spotId().value();
        if (command.spotId().present() && newSpotId != null) {
            requireLinkable(newSpotId);
        }
        post.revise(command.title().value(), command.content().value(), now);
        if (command.spotId().present()) {
            post.changeSpot(newSpotId, now);
        }
    }

    private static void requireNotCleared(PatchField<?> field, String label) {
        if (field.present() && field.value() == null) {
            throw invalidInput(label + "은(는) 비울 수 없어요.");
        }
    }

    private void requireLinkable(long spotId) {
        if (!activeSpotChecker.isActive(spotId)) {
            throw invalidInput("연결할 수 없는 장소예요.");
        }
    }

    private static void requireValidTitle(String title) {
        if (title == null || title.isBlank()) {
            throw invalidInput("제목을 입력해 주세요.");
        }
        if (title.length() > CommunityPost.TITLE_MAX_LENGTH) {
            throw invalidInput("제목은 " + CommunityPost.TITLE_MAX_LENGTH + "자 이하여야 해요.");
        }
    }

    private static void requireValidContent(String content) {
        if (content == null || content.isBlank()) {
            throw invalidInput("본문을 입력해 주세요.");
        }
        if (content.length() > CommunityPost.CONTENT_MAX_LENGTH) {
            throw invalidInput("본문은 " + CommunityPost.CONTENT_MAX_LENGTH + "자 이하여야 해요.");
        }
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(CommonErrorCode.INVALID_INPUT, message);
    }
}
