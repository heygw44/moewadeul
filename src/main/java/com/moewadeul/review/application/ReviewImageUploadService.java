package com.moewadeul.review.application;

import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import com.moewadeul.review.domain.ReviewImage;
import com.moewadeul.review.domain.ReviewImageRepository;
import com.moewadeul.review.infra.ReviewImageMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewImageUploadService {

    private final ReviewImageRepository reviewImageRepository;
    private final ReviewImageMapper reviewImageMapper;
    private final ImageStorage imageStorage;
    private final Clock clock;

    @Transactional
    public ReviewImageUpload issue(long memberId, String contentType, long sizeBytes) {
        String extension = ReviewImage.extensionOf(contentType)
                .orElseThrow(() -> new InvalidFieldException("contentType", "허용하지 않는 이미지 형식이에요."));
        if (!ReviewImage.isAllowedSize(sizeBytes)) {
            throw new InvalidFieldException("sizeBytes", "이미지 크기는 1바이트 이상 5MB 이하여야 해요.");
        }
        Instant now = clock.instant();
        requireUploadRoom(memberId, now);
        String key = "review/" + memberId + "/" + UUID.randomUUID() + "." + extension;
        ReviewImage image =
                reviewImageRepository.saveAndFlush(ReviewImage.issue(memberId, key, contentType, sizeBytes, now));
        PresignedUpload upload = imageStorage.presignUpload(key, contentType, sizeBytes);
        return new ReviewImageUpload(image.getId(), upload.url(), upload.headers(), upload.expiresAt());
    }

    private void requireUploadRoom(long memberId, Instant now) {
        // 같은 회원의 발급을 한 줄로 세운다. 그래야 상한에 한 장 모자랄 때 동시에 온 요청이 같은 수를 읽고 함께 통과하지 못한다.
        reviewImageMapper.lockMember(memberId);
        List<Instant> createdAts = reviewImageMapper.selectUnattachedCreatedAtsAfter(
                memberId, now.minus(ReviewImageCleanupService.RETENTION), ReviewImage.MAX_UNATTACHED_PER_MEMBER);
        if (createdAts.size() >= ReviewImage.MAX_UNATTACHED_PER_MEMBER) {
            Instant oldestExpiresAt = createdAts.get(0).plus(ReviewImageCleanupService.RETENTION);
            throw new RateLimitedException(
                    CommonErrorCode.IMAGE_UPLOAD_LIMITED, Duration.between(now, oldestExpiresAt));
        }
    }
}
