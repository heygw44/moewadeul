package com.moewadeul.review.application;

import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import com.moewadeul.review.domain.ReviewImage;
import com.moewadeul.review.domain.ReviewImageRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewImageUploadService {

    private final ReviewImageRepository reviewImageRepository;
    private final ImageStorage imageStorage;
    private final Clock clock;

    @Transactional
    public ReviewImageUpload issue(long memberId, String contentType, long sizeBytes) {
        String extension = ReviewImage.extensionOf(contentType)
                .orElseThrow(() -> new InvalidFieldException("contentType", "허용하지 않는 이미지 형식이에요."));
        if (!ReviewImage.isAllowedSize(sizeBytes)) {
            throw new InvalidFieldException("sizeBytes", "이미지 크기는 1바이트 이상 5MB 이하여야 해요.");
        }
        String key = "review/" + memberId + "/" + UUID.randomUUID() + "." + extension;
        ReviewImage image = reviewImageRepository.saveAndFlush(
                ReviewImage.issue(memberId, key, contentType, sizeBytes, clock.instant()));
        PresignedUpload upload = imageStorage.presignUpload(key, contentType, sizeBytes);
        return new ReviewImageUpload(image.getId(), upload.url(), upload.headers(), upload.expiresAt());
    }
}
